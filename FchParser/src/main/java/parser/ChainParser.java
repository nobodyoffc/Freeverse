package parser;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import constants.Constants;

import core.crypto.Hash;
import core.fch.BlockFileUtils;
import core.fch.OpReFileUtils;
import data.fchData.Block;
import data.fchData.BlockMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.BytesUtils;
import utils.EsUtils;
import utils.FchUtils;
import writeEs.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static constants.IndicesNames.BLOCK_MARK;
import static parser.Preparer.*;

public class ChainParser {

	public static final int FILE_END = -1;
	public static final int WRONG = -2;
	public static final int HEADER_FORK = -3;
	public static final int BLANK_8 = 8;
	public static final int WAIT_MORE = 0;
	private static final Logger log = LoggerFactory.getLogger(ChainParser.class);
	private static Thread lastCdThread = null;  // Track the last CD update thread
	private final OpReFileUtils opReFile = new OpReFileUtils();

	public int startParse(ElasticsearchClient esClient) throws Exception {

		System.out.println("Started parsing file:  "+Preparer.CurrentFile+" ...");
		log.info("Started parsing file: {} ...",Preparer.CurrentFile);

		File file = new File(Preparer.Path,Preparer.CurrentFile);
		FileInputStream fis = new FileInputStream(file);
		fis.skip(Pointer);

		long blockLength;

		long cdMakeTime = System.currentTimeMillis();

		while(true) {

			CheckResult checkResult = checkBlock(fis);
			BlockMask blockMask = checkResult.getBlockMark();
			byte[] blockBytes = checkResult.getBlockBytes();

			blockLength = checkResult.getBlockLength();

			if(blockLength == FILE_END) {
				String nextFile = BlockFileUtils.getNextFile(Preparer.CurrentFile);
				if(new File(Preparer.Path, nextFile).exists()) {
					System.out.println("file "+Preparer.CurrentFile+" finished.");
					log.info("Parsing file {} finished.",Preparer.CurrentFile);
					Preparer.CurrentFile = nextFile;
					Preparer.Pointer = 0;

					fis.close();
					file = new File(Preparer.Path,Preparer.CurrentFile);
					fis = new FileInputStream(file);

					System.out.println("Started parsing file:  "+Preparer.CurrentFile+" ...");
					log.info("Started parsing file: {} ...",Preparer.CurrentFile);
					continue;
				}else {
					System.out.print(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis())));
					System.out.println(" Waiting 30 seconds for new block file ...");
					TimeUnit.SECONDS.sleep(30);
					fis.close();
					fis = new FileInputStream(file);
					fis.skip(Preparer.Pointer);
				}
			}else if(blockLength == WRONG ) {
				System.out.println("Read Magic wrong. pointer: "+Preparer.Pointer);
				log.info("Read Magic wrong. pointer: {}",Preparer.Pointer);
				return WRONG;

			}else if(blockLength == HEADER_FORK) {
				Preparer.Pointer = Preparer.Pointer + 88;
				fis.close();
				fis = new FileInputStream(file);
				fis.skip(Preparer.Pointer);

			}else if(blockLength == WAIT_MORE) {
				System.out.print(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis())));
				System.out.println(" Waiting for new block...");
				AtomicBoolean running = new AtomicBoolean();
				running.set(true);
				FchUtils.waitForChangeInDirectory(Preparer.Path, running);
				fis.close();
				fis = new FileInputStream(file);
				fis.skip(Preparer.Pointer);

			}else if(blockLength == BLANK_8){
				Preparer.Pointer += blockLength;
			} else {

				linkToChain(esClient, blockMask,blockBytes);

				recheckOrphans(esClient);

				Preparer.Pointer += blockLength;
			}

			cdMakeTime = makeCd(esClient, cdMakeTime);
		}
	}

	private static long makeCd(ElasticsearchClient esClient, long cdMakeTime) {
//		long now = System.currentTimeMillis();
//		if( now - cdMakeTime > (1000*60*60)) {
//			Thread cdThread = new Thread(() -> {
//				try {
//					Block bestBlock = EsUtils.getBestBlock(esClient);
//					CdMaker cdMaker = new CdMaker();
//					cdMaker.makeUtxoCd(esClient, bestBlock);
//					log.info("All cd of UTXOs updated.");
//
//					// Use a shorter sleep time and check for interruption
//					try {
//						TimeUnit.SECONDS.sleep(30);
//					} catch (InterruptedException e) {
//						Thread.currentThread().interrupt();
//						return;
//					}
//
//					cdMaker.makeAddrCd(esClient);
//					log.info("All cd of addresses updated.");
//
//					// Use a shorter sleep time and check for interruption
//					try {
//						TimeUnit.SECONDS.sleep(15);
//					} catch (InterruptedException e) {
//						Thread.currentThread().interrupt();
//						return;
//					}
//				} catch (Exception e) {
//					log.error("Exception in cd update thread", e);
//				}
//			});
//			cdThread.setDaemon(true); // Mark as daemon thread so it won't prevent JVM exit
//			cdThread.start();
//
//			// Wait for the thread to complete with a timeout
//			try {
//				cdThread.join(TimeUnit.MINUTES.toMillis(30)); // Wait up to 30 minutes
//				if (cdThread.isAlive()) {
//					log.warn("CD update thread did not complete within timeout period");
//					cdThread.interrupt(); // Interrupt the thread if it's still running
//				}
//			} catch (InterruptedException e) {
//				log.error("Interrupted while waiting for CD update thread", e);
//				Thread.currentThread().interrupt();
//			}
//
//			cdMakeTime = now;


//			Block bestBlock = EsUtils.getBestBlock(esClient);
//
//			CdMaker cdMaker = new CdMaker();
//
//			cdMaker.makeUtxoCd(esClient,bestBlock);
//			log.info("All cd of UTXOs updated.");
//			TimeUnit.MINUTES.sleep(2);
//
//			cdMaker.makeAddrCd(esClient);
//			log.info("All cd of addresses updated.");
//			TimeUnit.MINUTES.sleep(1);
//
//			cdMakeTime = now;
//		}
//		return cdMakeTime;
		long now = System.currentTimeMillis();
		if( now - cdMakeTime > (1000*60*60*12)) {
			// Check if previous thread is still running
			if (lastCdThread != null && lastCdThread.isAlive()) {
				log.warn("Previous CD update thread is still running, skipping new update");
				return cdMakeTime;
			}

			Thread cdThread = new Thread(() -> {
				try {
					Block bestBlock = EsUtils.getBestBlock(esClient);
					CdMaker cdMaker = new CdMaker();
					cdMaker.makeUtxoCd(esClient, bestBlock);
					log.info("All cd of UTXOs updated.");

					// Use a shorter sleep time and check for interruption
					try {
						TimeUnit.SECONDS.sleep(30);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}

					cdMaker.makeAddrCd(esClient);
					log.info("All cd of addresses updated.");

					// Use a shorter sleep time and check for interruption
					try {
						TimeUnit.SECONDS.sleep(15);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}
				} catch (Exception e) {
					log.error("Exception in cd update thread", e);
				} finally {
					// Clear the thread reference when done
					synchronized (ChainParser.class) {
						if (lastCdThread == Thread.currentThread()) {
							lastCdThread = null;
						}
					}
				}
			});

			cdThread.setDaemon(true); // Mark as daemon thread so it won't prevent JVM exit

			// Store reference to the new thread
			synchronized (ChainParser.class) {
				lastCdThread = cdThread;
			}

			cdThread.start();
			cdMakeTime = now;
		}
		return cdMakeTime;
	}

	private CheckResult checkBlock(FileInputStream fis) throws Exception {

		BlockMask blockMask = new BlockMask();
		blockMask.set_pointer(Preparer.Pointer);
		blockMask.set_fileOrder(getFileOrder());

		CheckResult checkResult = new CheckResult();

		byte [] b8 = new byte[8];
		byte [] b4;

		if(fis.read(b8) == FILE_END) {
			System.out.println("File end when reading magic. pointer: "+Preparer.Pointer);
			log.error("File end when reading magic. ");
			checkResult.setBlockLength(FILE_END);
			return checkResult;
		}

		if(b8[0]==0) {
			if(!BlockFileUtils.getLastBlockFileName(Preparer.Path).equals(Preparer.CurrentFile)) {
				FileInputStream fisTemp = new FileInputStream(new File(Preparer.Path,Preparer.CurrentFile));
				long newPointer = Pointer + 8;
				fisTemp.skip(newPointer);
				b8 = new byte[8];
				fisTemp.read(b8);
				b4 = Arrays.copyOfRange(b8, 0, 4);
				if(Arrays.equals(b4, Constants.MAGIC_BYTES)) {
					fisTemp.close();
					checkResult.setBlockLength(BLANK_8);
					return checkResult;
				}
				fisTemp.close();
			}
			checkResult.setBlockLength(WAIT_MORE);
			return checkResult;
		}

		b4 = Arrays.copyOfRange(b8, 0, 4);
		if(!Arrays.equals(b4, Constants.MAGIC_BYTES)) {
			checkResult.setBlockLength(WRONG);
			return checkResult;
		}

		b4 = Arrays.copyOfRange(b8, 4, 8);
		long blockSize = BytesUtils.bytes4ToLongLE(b4);
		blockMask.setSize(blockSize);

		if(blockSize==0) {
			checkResult.setBlockLength(WAIT_MORE);
			return checkResult;
		}

		//Check valid header fork
		if(blockSize == 80) {
			checkResult.setBlockLength(HEADER_FORK);
			System.out.println("Header valid fork was found. Height: "+Preparer.BestHeight+1);
			log.info("Header valid fork was found. Height: "+Preparer.BestHeight+1);
			return checkResult;
		}

		byte[] blockBytes = new byte[Math.toIntExact(blockSize)];
		if(fis.read(blockBytes)== FILE_END) {
			System.out.println("File end when reading block. pointer: "+Preparer.Pointer);
			log.info("File end when reading block. Pointer:"+ Preparer.Pointer);
			checkResult.setBlockLength(FILE_END);
			return checkResult;
		}

		ByteArrayInputStream blockInputStream = new ByteArrayInputStream(blockBytes);

		byte[] blockHeadBytes = new byte[80];
		int result = blockInputStream.read(blockHeadBytes);
		if(result== -1) {
			System.out.println("Failed to read blockHeadBytes. the result is -1.");
			log.info("Failed to read blockHeadBytes. the result is -1.");
			checkResult.setBlockLength(WRONG);
			return checkResult;
		}

		String blockId = BytesUtils.bytesToHexStringLE(Hash.sha256x2(blockHeadBytes));
		blockMask.setId(blockId);

		String preId =  BytesUtils.bytesToHexStringLE(Arrays.copyOfRange(blockHeadBytes, 4, 4+32));
		blockMask.setPreBlockId(preId);

		Long time = BytesUtils.bytes4ToLongLE(Arrays.copyOfRange(blockHeadBytes, 4+32+32, 4+32+32+4));
		blockMask.setTime(time);

		byte[] blockBodyBytes = new byte[(int) (blockSize-80)];
		result = blockInputStream.read(blockBodyBytes);
		if(result== -1) {
			System.out.println("Failed to read blockBodyBytes. the result is -1.");
			log.info("Failed to read blockBodyBytes. the result is -1.");
			checkResult.setBlockLength(WRONG);
			return checkResult;
		}

		//Check valid header fork
		b4 = Arrays.copyOfRange(blockBodyBytes, 0, 4);
//		String b4Hash = BytesTools.bytesToHexStringBE(b4) ;
//		if(b4Hash.equals(Constants.MAGIC)) {
		if(Arrays.equals(b4, Constants.MAGIC_BYTES)) {
			System.out.println("Found valid header fork. Pointer: "+Preparer.Pointer);
			log.info("Found valid header fork. Pointer: {}",Preparer.Pointer);
			checkResult.setBlockLength(HEADER_FORK);
			return checkResult;
		}
		checkResult.setBlockLength(blockSize+8);
		checkResult.setBlockMark(blockMask);
		checkResult.setBlockBytes(blockBytes);
		return checkResult;
	}

	private int getFileOrder() {
		return BlockFileUtils.getFileOrder(Preparer.CurrentFile);
	}

	private static class CheckResult{
		long blockLength;
		BlockMask blockMask;
		byte[] blockBytes;

		public long getBlockLength() {
			return blockLength;
		}

		public void setBlockLength(long blockLength) {
			this.blockLength = blockLength;
		}

		public BlockMask getBlockMark() {
			return blockMask;
		}

		public void setBlockMark(BlockMask blockMask) {
			this.blockMask = blockMask;
		}

		public byte[] getBlockBytes() {
			return blockBytes;
		}

		public void setBlockBytes(byte[] blockBytes) {
			this.blockBytes = blockBytes;
		}
	}

	private void linkToChain(ElasticsearchClient esClient, BlockMask blockMask, byte[] blockBytes) throws Exception {

		if(isRepeatBlockIgnore(blockMask))
			return;
		if(isLinkToMainChainWriteItToEs(esClient, blockMask, blockBytes))
			return;
		if(isNewForkAddMarkToEs(esClient, blockMask))
			return;
		if(isLinkedToForkWriteMarkToEs(esClient, blockMask)){

			if(isForkOverMain(blockMask)) {
				HashMap<String, ArrayList<BlockMask>> chainMap = findLoseChainAndWinChain(blockMask);
				if(chainMap == null)return;
				reorganize(esClient,chainMap);
			}
			return;
		}
		writeOrphanMark(esClient, blockMask);

		int orphanListSize = Preparer.orphanList.size();

		System.out.println("Orphan block"
				+". Orphan: "+ orphanListSize
				+". Fork: "+ Preparer.forkList.size()
				+". Height: "+ Preparer.BestHeight);
	}

	private void writeBlockMark(ElasticsearchClient esClient, BlockMask blockMask) throws ElasticsearchException, IOException {
		esClient.index(i->i
				.index(BLOCK_MARK).id(blockMask.getId()).document(blockMask));
	}

	private boolean isForkOverMain(BlockMask blockMask) {

		return blockMask.getHeight() > Preparer.BestHeight;
	}

	private HashMap<String, ArrayList<BlockMask>> findLoseChainAndWinChain(BlockMask blockMask) {
		System.out.println("findLoseChainAndWinChain");

		BlockMask forkBlock;
		BlockMask mainBlock;

		ArrayList<BlockMask> winList = new ArrayList<>();
		ArrayList<BlockMask> loseList = new ArrayList<>();

		HashMap<String, ArrayList<BlockMask>> findMap = new HashMap<>();

		winList.add(blockMask);
		String preId = blockMask.getPreBlockId();

		boolean foundFormerForkBlockMark;

		while(true) {
			foundFormerForkBlockMark = false;
			for(int i=Preparer.forkList.size()-1;i>=0;i--) {
				forkBlock = Preparer.forkList.get(i);
				if(forkBlock.getId().equals(preId)) {
					winList.add(forkBlock);
					for(int j=Preparer.mainList.size()-1; j>=Preparer.mainList.size()-31; j--) {
						mainBlock = Preparer.mainList.get(j);
						if(forkBlock.getPreBlockId().equals(mainBlock.getId())){
							findMap.put("lose", loseList);
							findMap.put("win", winList);

							System.out.println("Got loseList size: "+ loseList.size()
									+ " last id:"+loseList.get(0).getId()
									+ " winList size:"+winList.size()
									+ " last id:"+winList.get(0).getId());
							return findMap;
						}
						loseList.add(mainBlock);
					}
					foundFormerForkBlockMark = true;
					preId = forkBlock.getPreBlockId();
				}
				if(foundFormerForkBlockMark)break;
			}
			if(!foundFormerForkBlockMark) {
				return null;
			}
		}
	}

	private void reorganize(ElasticsearchClient esClient, HashMap<String, ArrayList<BlockMask>> chainMap) throws Exception {

		ArrayList<BlockMask> loseList = chainMap.get("lose");
		ArrayList<BlockMask> winList = chainMap.get("win");

		if(loseList == null || loseList.isEmpty()) throw new Exception("loseList is null when reorganizing. ");

		long heightBeforeFork = winList.get(winList.size()-1).getHeight()-1;

		System.out.println("Reorganization happen after height: "+heightBeforeFork);
		log.info("Reorganization happen after height: "+heightBeforeFork);

		treatLoseList(esClient, loseList);

		new RollBacker().rollback(esClient,heightBeforeFork);

		treatWinList(esClient,winList);

		System.out.println("Reorganized. Fork: "+Preparer.forkList.size()+" Height: "+heightBeforeFork);
	}

	private void treatLoseList( ElasticsearchClient esClient,ArrayList<BlockMask> loseList) throws ElasticsearchException, IOException {

		BulkRequest.Builder br = new BulkRequest.Builder();
		Preparer.mainList.removeAll(loseList);

		for(int i=loseList.size()-1;i>=0;i--) {
			BlockMask bm = loseList.get(i);
			bm.setStatus(Preparer.FORK);
			Preparer.forkList.add(bm);
			br.operations(op->op.index(in->in
					.index(BLOCK_MARK)
					.id(bm.getId())
					.document(bm)));
		}
		esClient.bulk(br.build());
	}

	private void treatWinList(ElasticsearchClient esClient, ArrayList<BlockMask> winList) throws Exception {

		Preparer.forkList.removeAll(winList);

		for(int i=winList.size()-1;i>=0;i--) {
			BlockMask blockMask = winList.get(i);

			blockMask.setStatus(Preparer.MAIN);
			Preparer.mainList.add(blockMask);

			byte[] blockBytes = getBlockBytes(blockMask);
			ReadyBlock rawBlock = new BlockParser().parseBlock(blockBytes, blockMask);
			ReadyBlock readyBlock = new BlockMaker().makeReadyBlock(esClient, rawBlock);
			new BlockWriter().writeIntoEs(esClient, readyBlock,opReFile);

			System.out.println("writeWinListToEs. i:"+i
					+" blockId:"+ blockMask.getId()
					+" height"+ blockMask.getHeight()
					+" blockSize:"+ blockMask.getSize()
					+" pointer:"+ blockMask.get_pointer()
					+" blockBytes length:"+blockBytes.length);
		}

		dropOldFork(winList.get(0).getHeight());
	}

	private boolean isRepeatBlockIgnore(BlockMask blockMask) {

		if(Preparer.mainList!=null && !Preparer.mainList.isEmpty())
			for (BlockMask mark : Preparer.mainList) {
				if (blockMask.getId().equals(mark.getId())) {
					System.out.println("Repeat block...");
					log.info("Repeat block...");
					return true;
				}
			}
		return false;
	}

	private boolean isLinkToMainChainWriteItToEs(ElasticsearchClient esClient, BlockMask blockMask, byte[] blockBytes) throws Exception {

		if(blockMask.getPreBlockId().equals(Preparer.BestHash)){
			blockMask.setStatus(Preparer.MAIN);
			long newHeight = Preparer.BestHeight+1;
			blockMask.setHeight(newHeight);
			ReadyBlock rawBlock = new BlockParser().parseBlock(blockBytes, blockMask);
			ReadyBlock readyBlock = new BlockMaker().makeReadyBlock(esClient, rawBlock);
			new BlockWriter().writeIntoEs(esClient, readyBlock,opReFile);
			dropOldFork(newHeight);
			return true;
		}
		return false;
	}

	private void dropOldFork(long newHeight) {
		Preparer.forkList.removeIf(bm -> bm.getHeight() < newHeight - 30);
	}

	private boolean isNewForkAddMarkToEs(ElasticsearchClient esClient, BlockMask blockMask) throws ElasticsearchException, IOException {

		for(BlockMask bm:Preparer.mainList) {

			if(blockMask.getPreBlockId().equals(bm.getId()) && !bm.getId().equals(Preparer.BestHash)){

				blockMask.setHeight(bm.getHeight()+1);
				blockMask.setStatus(Preparer.FORK);
				Preparer.forkList.add(blockMask);
				writeBlockMark(esClient, blockMask);

				System.out.println("New fork block. Height: "+ blockMask.getHeight()+"forkList size:" + Preparer.forkList.size());
				log.info("New fork block. Height: "+ blockMask.getHeight());
				return true;
			}
		}
		return false;
	}

	private boolean isLinkedToForkWriteMarkToEs(ElasticsearchClient esClient, BlockMask blockMask1) throws ElasticsearchException, IOException {

		for (BlockMask bm : Preparer.forkList) {
			if (blockMask1.getPreBlockId().equals(bm.getId())) {
				blockMask1.setHeight(bm.getHeight() + 1);
				blockMask1.setStatus(Preparer.FORK);
				writeBlockMark(esClient, blockMask1);
				Preparer.forkList.add(blockMask1);

				System.out.println("Linked to fork block. Height: " + blockMask1.getHeight() + " fork size:" + Preparer.forkList.size());
				log.info("Linked to fork block. Height: " + blockMask1.getHeight());

				return true;
			}
		}

		return false;
	}

	private byte[] getBlockBytes(BlockMask bm) throws IOException {

		File file = new File(Preparer.Path, BlockFileUtils.getFileNameWithOrder(bm.get_fileOrder()));
		FileInputStream fis = new FileInputStream(file);
		fis.skip(bm.get_pointer()+8);
		byte[] blockBytes = new byte[Math.toIntExact(bm.getSize())];
		fis.read(blockBytes);
		fis.close();
		return blockBytes;
	}

	private void writeOrphanMark(ElasticsearchClient esClient, BlockMask blockMask) throws ElasticsearchException, IOException {

		blockMask.setStatus(Preparer.ORPHAN);
		blockMask.setOrphanHeight(Preparer.BestHeight);
		writeBlockMark(esClient, blockMask);
		Preparer.orphanList.add(blockMask);


	}

	private void recheckOrphans(ElasticsearchClient esClient) throws Exception {

		boolean found = false;

		BlockMask bestBlockMask;

		while(!found) {
			Iterator<BlockMask> iter = Preparer.orphanList.iterator();
			while(iter.hasNext()){
				BlockMask blockMask = iter.next();

				//If linked to main;
				if(blockMask.getPreBlockId().equals(Preparer.BestHash)) {
					blockMask.setHeight(Preparer.BestHeight+1);
					blockMask.setStatus(Preparer.MAIN);
					byte[] blockBytes = getBlockBytes(blockMask);

					ReadyBlock rawBlock = new BlockParser().parseBlock(blockBytes, blockMask);
					ReadyBlock readyBlock = new BlockMaker().makeReadyBlock(esClient, rawBlock);
					new BlockWriter().writeIntoEs(esClient, readyBlock,opReFile);

					bestBlockMask = Preparer.mainList.get(Preparer.mainList.size()-1);

					if(!bestBlockMask.getId().equals(BestHash)) {
						System.out.println("BestHash "+Preparer.BestHash+" is not the same as mainList:"+ bestBlockMask.getId());
						throw new Exception("BestHash "+Preparer.BestHash+" is not the same as mainList:"+ bestBlockMask.getId());
					}
					iter.remove();
					found = true;
					continue;
				}
				//If new fork
				for(BlockMask bm:Preparer.mainList) {
					if (blockMask.getId().equals(bm.getId())) {
						iter.remove();
						found=true;
						break;
					}
					if(blockMask.getPreBlockId().equals(bm.getId()) && !bm.getId().equals(Preparer.BestHash)){
						blockMask.setHeight(bm.getHeight()+1);
						blockMask.setStatus(Preparer.FORK);
						Preparer.forkList.add(blockMask);
						iter.remove();
						writeBlockMark(esClient, blockMask);

						System.out.println("New fork block. Height: "+ blockMask.getHeight()+". ForkList size:" + Preparer.forkList.size());
						log.info("New fork block. Height: "+ blockMask.getHeight());
						found = true;
						break;
					}
				}
				if(found)continue;

				//If linked to a fork;
				for(BlockMask fm: Preparer.forkList) {
					if (blockMask.getId().equals(fm.getId())) {
						iter.remove();
						found=true;
						break;
					}
					if(blockMask.getPreBlockId().equals(fm.getId())) {
						blockMask.setHeight(fm.getHeight()+1);
						blockMask.setStatus(Preparer.FORK);
						Preparer.forkList.add(blockMask);
						iter.remove();
						if(isForkOverMain(blockMask)) {
							HashMap<String, ArrayList<BlockMask>> chainMap = findLoseChainAndWinChain(blockMask);
							if(chainMap == null)return;
							reorganize(esClient,chainMap);
						}
						found = true;
						break;
					}
				}
			}
			if(!found)break;
		}
	}
}
