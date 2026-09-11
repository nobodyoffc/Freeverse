package parser;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;

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
