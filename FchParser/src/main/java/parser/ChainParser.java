package parser;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;

import core.fch.BlockFileUtils;
import core.fch.OpReFileUtils;
import data.fchData.Block;
import data.fchData.BlockMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.EsUtils;
import utils.FchUtils;
import writeEs.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static constants.IndicesNames.BLOCK_MARK;

/**
 * Orchestrates blockchain parsing: reads blocks via BlockFileReader,
 * links them to the chain, handles forks/orphans, and triggers reorgs.
 * <p>
 * File I/O is delegated to BlockFileReader (P3-6.2 separation of concerns).
 */
public class ChainParser {

	public static final int WRONG = BlockFileReader.WRONG;

	private static final int MAX_ORPHAN_RECHECK_ROUNDS = 100;
	private static final Logger log = LoggerFactory.getLogger(ChainParser.class);
	private static Thread lastCdThread = null;

	private final OpReFileUtils opReFile = new OpReFileUtils();
	private final ChainState state;
	private final BlockFileReader fileReader;

	public ChainParser(ChainState state) {
		this.state = state;
		this.fileReader = new BlockFileReader(state);
	}

	// ─── Main parse loop ────────────────────────────────────────────────

	/** Max consecutive re-reads of the same incomplete (not-yet-flushed) block before giving up. */
	private static final int MAX_INCOMPLETE_BLOCK_RETRIES = 30;

	/** Max consecutive re-reads of an unusable magic+size header on the tip file before giving up. */
	private static final int MAX_INCOMPLETE_HEADER_RETRIES = 30;

	/**
	 * Settle time before re-reading a header the fullnode was mid-write of. The directory
	 * watcher fires on any change under blocks/ (rev*.dat included), so without this the
	 * retry budget could be spent on wake-ups that arrive before the write has landed.
	 */
	private static final long INCOMPLETE_HEADER_SETTLE_MILLIS = 200;

	public int startParse(ElasticsearchClient esClient) throws Exception {

		System.out.println("Started parsing file:  " + state.getCurrentFile() + " ...");
		log.info("Started parsing file: {} ...", state.getCurrentFile());

		File file = new File(state.getPath(), state.getCurrentFile());
		FileInputStream fis = new FileInputStream(file);
		try {
			BlockFileReader.seek(fis, state.getPointer());

			long blockLength;
			long cdMakeTime = System.currentTimeMillis();
			int incompleteRetries = 0;
			int incompleteHeaderRetries = 0;

			while (true) {

				BlockFileReader.CheckResult checkResult = fileReader.checkBlock(fis);
				BlockMask blockMask = checkResult.getBlockMark();
				byte[] blockBytes = checkResult.getBlockBytes();
				blockLength = checkResult.getBlockLength();

				if (blockLength == BlockFileReader.FILE_END) {
					String nextFile = BlockFileUtils.getNextFile(state.getCurrentFile());
					if (new File(state.getPath(), nextFile).exists()) {
						System.out.println("file " + state.getCurrentFile() + " finished.");
						log.info("Parsing file {} finished.", state.getCurrentFile());
						state.setCurrentFile(nextFile);
						state.setPointer(0);

						fis.close();
						file = new File(state.getPath(), state.getCurrentFile());
						fis = new FileInputStream(file);

						System.out.println("Started parsing file:  " + state.getCurrentFile() + " ...");
						log.info("Started parsing file: {} ...", state.getCurrentFile());
						continue;
					} else {
						System.out.print(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
						System.out.println(" Waiting 30 seconds for new block file ...");
						TimeUnit.SECONDS.sleep(30);
						fis.close();
						fis = new FileInputStream(file);
						BlockFileReader.seek(fis, state.getPointer());
					}
				} else if (blockLength == BlockFileReader.WRONG) {
					System.out.println("Read Magic wrong. pointer: " + state.getPointer());
					log.info("Read Magic wrong. pointer: {}", state.getPointer());
					return WRONG;

				} else if (blockLength == BlockFileReader.HEADER_INCOMPLETE) {
					// The header at the pointer is not usable yet, but we are on the tip file
					// where the fullnode may be part-way through writing it (a buffered write
					// can be observed half-copied into the page cache). Do NOT advance the
					// pointer: wait for the file to change and re-read the same header.
					// Unless the fullnode rewrote the file under us — then no amount of waiting helps.
					RewriteCheck rewrite = resyncIfFileRewritten(esClient);
					if (rewrite == RewriteCheck.UNRECOVERABLE) return WRONG;
					if (rewrite == RewriteCheck.RESYNCED) {
						incompleteHeaderRetries = 0;
						fis.close();
						fis = new FileInputStream(file);
						BlockFileReader.seek(fis, state.getPointer());
						continue;
					}
					if (++incompleteHeaderRetries > MAX_INCOMPLETE_HEADER_RETRIES) {
						System.out.println("Read Magic wrong. pointer: " + state.getPointer());
						log.error("Block header at pointer {} of file {} still unusable after {} retries. Stopping.",
								state.getPointer(), state.getCurrentFile(), MAX_INCOMPLETE_HEADER_RETRIES);
						return WRONG;
					}
					log.info("Waiting for the fullnode to finish the block header at pointer {} (retry {}/{}).",
							state.getPointer(), incompleteHeaderRetries, MAX_INCOMPLETE_HEADER_RETRIES);
					AtomicBoolean running = new AtomicBoolean(true);
					FchUtils.waitForChangeInDirectory(state.getPath(), running);
					TimeUnit.MILLISECONDS.sleep(INCOMPLETE_HEADER_SETTLE_MILLIS);
					fis.close();
					fis = new FileInputStream(file);
					BlockFileReader.seek(fis, state.getPointer());

				} else if (blockLength == BlockFileReader.HEADER_FORK) {
					state.setPointer(state.getPointer() + 88);
					fis.close();
					fis = new FileInputStream(file);
					BlockFileReader.seek(fis, state.getPointer());

				} else if (blockLength == BlockFileReader.WAIT_MORE) {
					// A rewrite that moved blocks to lower offsets can leave the pointer in the
					// zero-filled tail, where it would wait forever for a block written elsewhere.
					RewriteCheck rewrite = resyncIfFileRewritten(esClient);
					if (rewrite == RewriteCheck.UNRECOVERABLE) return WRONG;
					if (rewrite == RewriteCheck.RESYNCED) {
						fis.close();
						fis = new FileInputStream(file);
						BlockFileReader.seek(fis, state.getPointer());
						continue;
					}
					System.out.print(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
					System.out.println(" Waiting for new block...");
					AtomicBoolean running = new AtomicBoolean(true);
					FchUtils.waitForChangeInDirectory(state.getPath(), running);
					fis.close();
					fis = new FileInputStream(file);
					BlockFileReader.seek(fis, state.getPointer());

				} else if (blockLength == BlockFileReader.BLANK_8) {
					state.setPointer(state.getPointer() + blockLength);
				} else {
					try {
						linkToChain(esClient, blockMask, blockBytes);
					} catch (IncompleteBlockException e) {
						// The block passed magic/size/length checks but its body is not yet a
						// complete, valid block — typically the fullnode has not finished flushing
						// a freshly-appended tip block. Do NOT advance the pointer; wait for the
						// file to change and re-read the same block.
						if (++incompleteRetries > MAX_INCOMPLETE_BLOCK_RETRIES) {
							log.error("Block at pointer {} still incomplete after {} retries: {}",
									state.getPointer(), MAX_INCOMPLETE_BLOCK_RETRIES, e.getMessage());
							return WRONG;
						}
						log.warn("Incomplete block at pointer {} (retry {}/{}): {}. Waiting for fullnode to finish writing.",
								state.getPointer(), incompleteRetries, MAX_INCOMPLETE_BLOCK_RETRIES, e.getMessage());
						AtomicBoolean running = new AtomicBoolean(true);
						FchUtils.waitForChangeInDirectory(state.getPath(), running);
						fis.close();
						fis = new FileInputStream(file);
						BlockFileReader.seek(fis, state.getPointer());
						continue;
					}
					incompleteRetries = 0;
					incompleteHeaderRetries = 0;
					recheckOrphans(esClient);
					state.setPointer(state.getPointer() + blockLength);
				}

				cdMakeTime = makeCd(esClient, cdMakeTime);
			}
		} finally {
			fis.close();
		}
	}

	// ─── Fullnode rewrite recovery ───────────────────────────────────────

	enum RewriteCheck { NONE, RESYNCED, UNRECOVERABLE }

	/** Consecutive ES marks found at their recorded offset after which the rewritten region is taken to have ended. */
	private static final int REWRITE_MATCH_RUN = 200;

	/**
	 * Detects that the fullnode rewrote the current blk file under the parser and, if so,
	 * re-anchors the parse there.
	 * <p>
	 * After an unclean shutdown the fullnode rolls back to its last flushed position and writes
	 * the following blocks again — possibly at other offsets, minus blocks it dropped
	 * (2026-10-22: blk00014.dat lost a 223-byte block and everything after moved 231 bytes
	 * down; the parser's pointer then sat 231 bytes inside a block and it stopped on
	 * "Read Magic wrong"). The block data itself is unchanged, only where it sits, so:
	 * <ol>
	 *   <li>walk the file to learn where each block is now;</li>
	 *   <li>correct {@code _pointer} on every stale mark, in ES and in memory, and drop
	 *       fork/orphan marks whose block is no longer in the file;</li>
	 *   <li>link any block in the rewritten region the parser has never seen;</li>
	 *   <li>resume right after the best block's new position.</li>
	 * </ol>
	 */
	private RewriteCheck resyncIfFileRewritten(ElasticsearchClient esClient) throws Exception {
		BlockMask best = state.getMainById(state.getBestHash());
		if (best == null || best.get_pointer() == null || best.get_fileOrder() == null) return RewriteCheck.NONE;
		if (best.get_fileOrder() != fileReader.getFileOrder()) return RewriteCheck.NONE;
		if (fileReader.isBlockAtItsPointer(best)) return RewriteCheck.NONE;

		String fileName = state.getCurrentFile();
		log.warn("Best block {} (height {}) is no longer at pointer {} of {}. The fullnode rewrote the file; resyncing.",
				best.getId(), best.getHeight(), best.get_pointer(), fileName);
		System.out.println("The fullnode rewrote " + fileName + ". Resyncing block positions...");

		Map<String, BlockFileReader.BlockPosition> byId = new HashMap<>();
		List<BlockFileReader.BlockPosition> walk = fileReader.walkBlockFile(fileName);
		for (BlockFileReader.BlockPosition p : walk) byId.put(p.id(), p);

		BlockFileReader.BlockPosition bestNow = byId.get(best.getId());
		if (bestNow == null) {
			log.error("Best block {} (height {}) is not in {} any more. Use \"Manually start from a height\" "
					+ "with a height whose block is still in the file.", best.getId(), best.getHeight(), fileName);
			return RewriteCheck.UNRECOVERABLE;
		}

		long rewriteStart = relocateMarks(esClient, byId, bestNow.offset());
		long resumePointer = bestNow.offset() + bestNow.size() + 8;
		linkUnseenBlocks(esClient, walk, rewriteStart, resumePointer);

		state.setPointer(resumePointer);
		log.info("Resynced {}: rewritten region starts at {}; resuming after best block {} at pointer {}.",
				fileName, rewriteStart, best.getId(), resumePointer);
		return RewriteCheck.RESYNCED;
	}

	/**
	 * Compares the ES marks of the current file, highest offset first, with where the blocks
	 * are now, and fixes the stale ones. Stops after {@link #REWRITE_MATCH_RUN} marks in a row
	 * are where they were recorded: below that the fullnode did not rewrite anything.
	 *
	 * @return the lowest new offset of a moved block, i.e. where the rewritten region starts
	 */
	private long relocateMarks(ElasticsearchClient esClient, Map<String, BlockFileReader.BlockPosition> byId,
							   long bestOffset) throws Exception {
		int fileOrder = fileReader.getFileOrder();
		Map<String, Long> moved = new LinkedHashMap<>();
		List<BlockMask> gone = new ArrayList<>();
		int matchRun = 0;
		List<FieldValue> after = null;

		scan:
		while (true) {
			List<FieldValue> searchAfter = after;
			SearchResponse<BlockMask> response = esClient.search(s -> {
				s.index(BLOCK_MARK)
						.query(q -> q.term(t -> t.field("_fileOrder").value(fileOrder)))
						.sort(so -> so.field(f -> f.field("_pointer").order(SortOrder.Desc)))
						.size(1000);
				if (searchAfter != null) s.searchAfter(searchAfter);
				return s;
			}, BlockMask.class);
			List<Hit<BlockMask>> hits = response.hits().hits();
			if (hits.isEmpty()) break;
			for (Hit<BlockMask> hit : hits) {
				BlockMask mark = hit.source();
				if (mark == null) continue;
				BlockFileReader.BlockPosition now = byId.get(mark.getId());
				if (now != null && now.offset() == mark.get_pointer()) {
					if (++matchRun >= REWRITE_MATCH_RUN) break scan;
					continue;
				}
				matchRun = 0;
				if (now != null) moved.put(mark.getId(), now.offset());
				else gone.add(mark);
			}
			after = hits.get(hits.size() - 1).sort();
		}

		long rewriteStart = bestOffset;
		BulkRequest.Builder br = new BulkRequest.Builder();
		int ops = 0;
		for (Map.Entry<String, Long> e : moved.entrySet()) {
			long newPointer = e.getValue();
			rewriteStart = Math.min(rewriteStart, newPointer);
			BlockMask inMemory = state.findMark(e.getKey());
			if (inMemory != null) inMemory.set_pointer(newPointer);
			Map<String, Object> doc = Map.of("_pointer", newPointer);
			br.operations(op -> op.update(u -> u.index(BLOCK_MARK).id(e.getKey()).action(a -> a.doc(doc))));
			ops++;
		}
		for (BlockMask mark : gone) {
			if (Preparer.MAIN.equals(mark.getStatus())) {
				// The main chain still links through it (its successors name it as preBlockId),
				// so the fullnode has it somewhere; only its position is unknown.
				log.error("Main block {} (height {}) was at pointer {} of {} but is not in that file any more.",
						mark.getId(), mark.getHeight(), mark.get_pointer(), state.getCurrentFile());
				continue;
			}
			log.warn("{} block {} was at pointer {} but the fullnode dropped it. Removing its mark.",
					mark.getStatus(), mark.getId(), mark.get_pointer());
			state.dropNonMain(mark.getId());
			state.evictBlockBytes(mark.getId());
			br.operations(op -> op.delete(d -> d.index(BLOCK_MARK).id(mark.getId())));
			ops++;
		}
		if (ops > 0) {
			BulkResponse response = esClient.bulk(br.refresh(Refresh.True).build());
			if (response.errors())
				throw new IOException("Failed to correct block marks after the fullnode rewrote " + state.getCurrentFile());
		}
		log.info("Rewrite of {}: {} marks moved, {} gone.", state.getCurrentFile(), moved.size(), gone.size());
		return rewriteStart;
	}

	/**
	 * Links blocks in the rewritten region before the resume point that the parser has never
	 * seen. The fullnode may have received a block during the rewrite that is not part of the
	 * old layout; resuming after the best block would skip it.
	 */
	private void linkUnseenBlocks(ElasticsearchClient esClient, List<BlockFileReader.BlockPosition> walk,
								  long from, long to) throws Exception {
		File file = new File(state.getPath(), state.getCurrentFile());
		for (BlockFileReader.BlockPosition p : walk) {
			if (p.offset() < from || p.offset() >= to) continue;
			if (state.findMark(p.id()) != null) continue;
			if (esClient.exists(e -> e.index(BLOCK_MARK).id(p.id())).value()) continue;

			state.setPointer(p.offset());
			try (FileInputStream fis = new FileInputStream(file)) {
				BlockFileReader.seek(fis, p.offset());
				BlockFileReader.CheckResult result = fileReader.checkBlock(fis);
				if (result.getBlockMark() == null) {
					log.warn("Unseen block {} at pointer {} could not be read (status {}); skipping it.",
							p.id(), p.offset(), result.getBlockLength());
					continue;
				}
				log.info("Linking block {} at pointer {}, written during the rewrite and not seen before.", p.id(), p.offset());
				linkToChain(esClient, result.getBlockMark(), result.getBlockBytes());
				recheckOrphans(esClient);
			}
		}
	}

	// ─── Chain linking ──────────────────────────────────────────────────

	private void linkToChain(ElasticsearchClient esClient, BlockMask blockMask, byte[] blockBytes) throws Exception {

		if (isRepeatBlockIgnore(blockMask))
			return;
		if (isLinkToMainChainWriteItToEs(esClient, blockMask, blockBytes))
			return;
		if (isNewForkAddMarkToEs(esClient, blockMask, blockBytes))
			return;
		if (isLinkedToForkWriteMarkToEs(esClient, blockMask, blockBytes)) {
			if (isForkOverMain(blockMask)) {
				HashMap<String, ArrayList<BlockMask>> chainMap = findLoseChainAndWinChain(blockMask);
				if (chainMap == null) return;
				reorganize(esClient, chainMap);
			}
			return;
		}
		writeOrphanMark(esClient, blockMask, blockBytes);

		System.out.println("Orphan block"
				+ ". Orphan: " + state.orphanSize()
				+ ". Fork: " + state.forkSize()
				+ ". Height: " + state.getBestHeight());
		log.info("Orphan block {} (pre {}) at file {} pointer {}. Orphans: {} Forks: {} Height: {}",
				blockMask.getId(), blockMask.getPreBlockId(), state.getCurrentFile(), blockMask.get_pointer(),
				state.orphanSize(), state.forkSize(), state.getBestHeight());
	}

	/** O(1) repeat check via HashMap. */
	private boolean isRepeatBlockIgnore(BlockMask blockMask) {
		if (state.mainContainsId(blockMask.getId())) {
			System.out.println("Repeat block...");
			log.info("Repeat block...");
			return true;
		}
		return false;
	}

	private boolean isLinkToMainChainWriteItToEs(ElasticsearchClient esClient, BlockMask blockMask, byte[] blockBytes) throws Exception {
		if (blockMask.getPreBlockId().equals(state.getBestHash())) {
			blockMask.setStatus(Preparer.MAIN);
			long newHeight = state.getBestHeight() + 1;
			blockMask.setHeight(newHeight);
			ReadyBlock rawBlock = new BlockParser().parseBlock(blockBytes, blockMask);
			ReadyBlock readyBlock = new BlockMaker().makeReadyBlock(esClient, rawBlock);
			new BlockWriter().writeIntoEs(esClient, readyBlock, opReFile, state);
			state.dropOldForks(newHeight);
			return true;
		}
		return false;
	}

	/** O(1) fork detection via HashMap. */
	private boolean isNewForkAddMarkToEs(ElasticsearchClient esClient, BlockMask blockMask, byte[] blockBytes) throws ElasticsearchException, IOException {
		BlockMask mainParent = state.getMainById(blockMask.getPreBlockId());
		if (mainParent != null && !mainParent.getId().equals(state.getBestHash())) {
			blockMask.setHeight(mainParent.getHeight() + 1);
			blockMask.setStatus(Preparer.FORK);
			state.addToFork(blockMask);
			state.cacheBlockBytes(blockMask.getId(), blockBytes);
			writeBlockMark(esClient, blockMask);

			System.out.println("New fork block. Height: " + blockMask.getHeight() + " forkList size:" + state.forkSize());
			log.info("New fork block. Height: {}", blockMask.getHeight());
			return true;
		}
		return false;
	}

	/** O(1) fork linking via HashMap lookup. No ConcurrentModificationException risk. */
	private boolean isLinkedToForkWriteMarkToEs(ElasticsearchClient esClient, BlockMask blockMask, byte[] blockBytes) throws ElasticsearchException, IOException {
		BlockMask parentFork = state.getForkById(blockMask.getPreBlockId());
		if (parentFork != null) {
			blockMask.setHeight(parentFork.getHeight() + 1);
			blockMask.setStatus(Preparer.FORK);
			writeBlockMark(esClient, blockMask);
			state.addToFork(blockMask);
			state.cacheBlockBytes(blockMask.getId(), blockBytes);

			System.out.println("Linked to fork block. Height: " + blockMask.getHeight() + " fork size:" + state.forkSize());
			log.info("Linked to fork block. Height: {}", blockMask.getHeight());
			return true;
		}
		return false;
	}

	private boolean isForkOverMain(BlockMask blockMask) {
		return blockMask.getHeight() > state.getBestHeight();
	}

	/**
	 * Parses and verifies a buffered block. The bytes cache can hold a stale
	 * snapshot captured while the fullnode was still flushing the block (the
	 * cause of the 2026-07-05 chain loss), so on verification failure evict
	 * the cache and re-read from disk once before giving up.
	 */
	private ReadyBlock parseBlockRereadingOnFailure(BlockMask blockMask) throws Exception {
		byte[] blockBytes = fileReader.getBlockBytes(blockMask);
		try {
			return new BlockParser().parseBlock(blockBytes, blockMask);
		} catch (IncompleteBlockException e) {
			log.warn("Cached bytes of block {} failed verification: {}. Re-reading from disk.",
					blockMask.getId(), e.getMessage());
			state.evictBlockBytes(blockMask.getId());
			blockBytes = fileReader.getBlockBytes(blockMask);
			return new BlockParser().parseBlock(blockBytes, blockMask);
		}
	}

	private void writeBlockMark(ElasticsearchClient esClient, BlockMask blockMask) throws ElasticsearchException, IOException {
		esClient.index(i -> i.index(BLOCK_MARK).id(blockMask.getId()).document(blockMask));
	}

	private void writeOrphanMark(ElasticsearchClient esClient, BlockMask blockMask, byte[] blockBytes) throws ElasticsearchException, IOException {
		blockMask.setStatus(Preparer.ORPHAN);
		blockMask.setOrphanHeight(state.getBestHeight());
		writeBlockMark(esClient, blockMask);
		state.addToOrphan(blockMask);
		state.cacheBlockBytes(blockMask.getId(), blockBytes);
	}

	// ─── Reorganization ─────────────────────────────────────────────────

	/** Uses O(1) HashMap lookups to trace fork chain back to main. */
	private HashMap<String, ArrayList<BlockMask>> findLoseChainAndWinChain(BlockMask blockMask) {
		System.out.println("findLoseChainAndWinChain");

		ArrayList<BlockMask> winList = new ArrayList<>();
		HashMap<String, ArrayList<BlockMask>> findMap = new HashMap<>();

		winList.add(blockMask);
		String preId = blockMask.getPreBlockId();

		while (true) {
			BlockMask forkBlock = state.getForkById(preId);
			if (forkBlock == null) {
				log.warn("Fork chain broken: could not find fork block with id {}", preId);
				return null;
			}

			winList.add(forkBlock);

			BlockMask mainBlock = state.getMainById(forkBlock.getPreBlockId());
			if (mainBlock != null) {
				List<BlockMask> loseList = state.getMainTailUntil(
						mainBlock.getId(), state.getReorgProtect() + 1);
				if (loseList == null) {
					log.warn("Fork attachment point {} not found within reorg depth", mainBlock.getId());
					return null;
				}

				findMap.put("lose", new ArrayList<>(loseList));
				findMap.put("win", winList);

				if (!loseList.isEmpty()) {
					System.out.println("Got loseList size: " + loseList.size()
							+ " last id:" + loseList.get(0).getId()
							+ " winList size:" + winList.size()
							+ " last id:" + winList.get(0).getId());
				}
				return findMap;
			}

			preId = forkBlock.getPreBlockId();
		}
	}

	private void reorganize(ElasticsearchClient esClient, HashMap<String, ArrayList<BlockMask>> chainMap) throws Exception {

		ArrayList<BlockMask> loseList = chainMap.get("lose");
		ArrayList<BlockMask> winList = chainMap.get("win");

		if (loseList == null || loseList.isEmpty())
			throw new Exception("loseList is null when reorganizing.");

		long heightBeforeFork = winList.get(winList.size() - 1).getHeight() - 1;

		System.out.println("Reorganization happen after height: " + heightBeforeFork);
		log.info("Reorganization happen after height: {}", heightBeforeFork);

		// Rollback FIRST: its deleteBlockMarks removes every mark above
		// heightBeforeFork (by height or orphanHeight). Writing the lose-list
		// fork marks before it would delete them right away, leaving ES without
		// the fork marks a later restart needs to reload this branch.
		new RollBacker().rollback(esClient, heightBeforeFork);
		treatLoseList(esClient, loseList);
		treatWinList(esClient, winList);
		restoreOrphanMarks(esClient, heightBeforeFork);

		System.out.println("Reorganized. Fork: " + state.forkSize() + " Height: " + heightBeforeFork);
	}

	/**
	 * Re-persists in-memory orphan marks whose ES documents were deleted by the
	 * reorg rollback (orphanHeight &gt; heightBeforeFork). The orphan buffer
	 * survives a live reorg in memory, but without their marks in ES a later
	 * restart could neither reload these blocks nor rewind the parse pointer
	 * to re-read them.
	 */
	private void restoreOrphanMarks(ElasticsearchClient esClient, long heightBeforeFork) throws ElasticsearchException, IOException {
		BulkRequest.Builder br = new BulkRequest.Builder();
		int count = 0;
		for (BlockMask bm : state.getOrphanSnapshot()) {
			if (bm.getOrphanHeight() != null && bm.getOrphanHeight() > heightBeforeFork) {
				br.operations(op -> op.index(in -> in
						.index(BLOCK_MARK)
						.id(bm.getId())
						.document(bm)));
				count++;
			}
		}
		if (count > 0) {
			esClient.bulk(br.build());
			log.info("Restored {} orphan block marks deleted by the reorg rollback.", count);
		}
	}

	private void treatLoseList(ElasticsearchClient esClient, ArrayList<BlockMask> loseList) throws ElasticsearchException, IOException {
		BulkRequest.Builder br = new BulkRequest.Builder();
		state.removeFromMain(loseList);
		for (int i = loseList.size() - 1; i >= 0; i--) {
			BlockMask bm = loseList.get(i);
			bm.setStatus(Preparer.FORK);
			state.addToFork(bm);
			br.operations(op -> op.index(in -> in
					.index(BLOCK_MARK)
					.id(bm.getId())
					.document(bm)));
		}
		esClient.bulk(br.build());
	}

	private void treatWinList(ElasticsearchClient esClient, ArrayList<BlockMask> winList) throws Exception {
		state.removeFromFork(winList);
		for (int i = winList.size() - 1; i >= 0; i--) {
			BlockMask blockMask = winList.get(i);
			blockMask.setStatus(Preparer.MAIN);

			ReadyBlock rawBlock = parseBlockRereadingOnFailure(blockMask);
			ReadyBlock readyBlock = new BlockMaker().makeReadyBlock(esClient, rawBlock);
			new BlockWriter().writeIntoEs(esClient, readyBlock, opReFile, state);

			System.out.println("writeWinListToEs. i:" + i
					+ " blockId:" + blockMask.getId()
					+ " height:" + blockMask.getHeight()
					+ " blockSize:" + blockMask.getSize()
					+ " pointer:" + blockMask.get_pointer());
		}
		state.dropOldForks(winList.get(0).getHeight());
	}

	// ─── Orphan management ──────────────────────────────────────────────

	/**
	 * Re-checks orphan blocks using snapshot iteration + O(1) HashMap lookups.
	 * No ConcurrentModificationException risk.
	 */
	private void recheckOrphans(ElasticsearchClient esClient) throws Exception {

		boolean found;
		int rounds = 0;

		do {
			if (++rounds > MAX_ORPHAN_RECHECK_ROUNDS) {
				log.warn("Orphan recheck exceeded {} rounds with {} orphans remaining. Breaking.",
						MAX_ORPHAN_RECHECK_ROUNDS, state.orphanSize());
				break;
			}
			found = false;

			List<BlockMask> orphanSnapshot = state.getOrphanSnapshot();

			for (BlockMask blockMask : orphanSnapshot) {

				// Linked to main chain tip
				if (blockMask.getPreBlockId().equals(state.getBestHash())) {
					blockMask.setHeight(state.getBestHeight() + 1);
					blockMask.setStatus(Preparer.MAIN);

					ReadyBlock rawBlock;
					try {
						rawBlock = parseBlockRereadingOnFailure(blockMask);
					} catch (IncompleteBlockException e) {
						// Even the disk re-read failed verification — the fullnode has
						// not finished flushing this block. Keep it buffered as an
						// orphan; this method runs again after every parsed block, so
						// it will be retried once the bytes are complete on disk.
						blockMask.setStatus(Preparer.ORPHAN);
						blockMask.setHeight(0L);
						log.warn("Orphan {} links to the tip but fails verification even after a disk re-read: {}. Will retry later.",
								blockMask.getId(), e.getMessage());
						continue;
					}
					ReadyBlock readyBlock = new BlockMaker().makeReadyBlock(esClient, rawBlock);
					new BlockWriter().writeIntoEs(esClient, readyBlock, opReFile, state);

					BlockMask lastMain = state.getLastMain();
					if (lastMain != null && !lastMain.getId().equals(state.getBestHash())) {
						throw new Exception("BestHash " + state.getBestHash()
								+ " is not the same as mainList:" + lastMain.getId());
					}
					state.removeOrphan(blockMask.getId());
					state.evictBlockBytes(blockMask.getId());
					found = true;
					break; // restart with fresh snapshot
				}

				// Duplicate in main chain
				if (state.mainContainsId(blockMask.getId())) {
					state.removeOrphan(blockMask.getId());
					state.evictBlockBytes(blockMask.getId());
					found = true;
					continue;
				}

				// New fork from main
				BlockMask mainParent = state.getMainById(blockMask.getPreBlockId());
				if (mainParent != null && !mainParent.getId().equals(state.getBestHash())) {
					blockMask.setHeight(mainParent.getHeight() + 1);
					blockMask.setStatus(Preparer.FORK);
					state.addToFork(blockMask);
					state.removeOrphan(blockMask.getId());
					writeBlockMark(esClient, blockMask);

					System.out.println("New fork block. Height: " + blockMask.getHeight()
							+ ". ForkList size:" + state.forkSize());
					log.info("New fork block. Height: {}", blockMask.getHeight());
					found = true;
					continue;
				}

				// Duplicate in fork
				if (state.forkContainsId(blockMask.getId())) {
					state.removeOrphan(blockMask.getId());
					state.evictBlockBytes(blockMask.getId());
					found = true;
					continue;
				}

				// Linked to a fork
				BlockMask forkParent = state.getForkById(blockMask.getPreBlockId());
				if (forkParent != null) {
					blockMask.setHeight(forkParent.getHeight() + 1);
					blockMask.setStatus(Preparer.FORK);
					state.addToFork(blockMask);
					state.removeOrphan(blockMask.getId());
					if (isForkOverMain(blockMask)) {
						HashMap<String, ArrayList<BlockMask>> chainMap = findLoseChainAndWinChain(blockMask);
						if (chainMap != null) {
							reorganize(esClient, chainMap);
						} else {
							// Don't abandon the whole orphan set over one untraceable
							// fork — keep processing the remaining orphans.
							log.warn("Fork over main at {} but the fork chain could not be traced; skipping reorg for now.",
									blockMask.getId());
						}
					}
					found = true;
					break; // restart with fresh snapshot after potential reorg
				}
			}
		} while (found);
	}

	// ─── CD update (background thread) ──────────────────────────────────

	private static long makeCd(ElasticsearchClient esClient, long cdMakeTime) {
		long now = System.currentTimeMillis();
		if (now - cdMakeTime > (1000L * 60 * 60 * 12)) {
			if (lastCdThread != null && lastCdThread.isAlive()) {
				log.warn("Previous CD update thread is still running, skipping new update");
				return cdMakeTime;
			}

			Thread cdThread = new Thread(() -> {
				try {
					Block bestBlock = EsUtils.getBestBlock(esClient);
					if (bestBlock == null) {
						log.warn("No best block in ES; skipping the CD update.");
						return;
					}
					CdMaker cdMaker = new CdMaker();
					cdMaker.makeUtxoCd(esClient, bestBlock);
					log.info("All cd of UTXOs updated.");

					try { TimeUnit.SECONDS.sleep(30); }
					catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }

					cdMaker.makeAddrCd(esClient);
					log.info("All cd of addresses updated.");

					try { TimeUnit.SECONDS.sleep(15); }
					catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
				} catch (Exception e) {
					log.error("Exception in cd update thread", e);
				} finally {
					synchronized (ChainParser.class) {
						if (lastCdThread == Thread.currentThread()) {
							lastCdThread = null;
						}
					}
				}
			});

			cdThread.setDaemon(true);
			synchronized (ChainParser.class) { lastCdThread = cdThread; }
			cdThread.start();
			cdMakeTime = now;
		}
		return cdMakeTime;
	}
}
