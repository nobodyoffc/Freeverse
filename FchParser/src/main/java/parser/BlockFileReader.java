package parser;

import constants.Constants;
import core.crypto.Hash;
import core.fch.BlockFileUtils;
import data.fchData.BlockMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.BytesUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Handles block file I/O: reading raw block data from blk*.dat files,
 * validating magic bytes, extracting block headers and bodies.
 * <p>
 * Separated from ChainParser (P3-6.2) to isolate file I/O concerns
 * from chain state management and linking logic.
 * <p>
 * IMPORTANT: Uses readFully() for all reads to prevent short-read corruption.
 * The fullnode writes to blk files concurrently. A partially-written block
 * is treated as WAIT_MORE (not yet available) rather than corrupted data.
 */
public class BlockFileReader {

	public static final int FILE_END = -1;
	public static final int WRONG = -2;
	public static final int HEADER_FORK = -3;
	public static final int BLANK_8 = 8;
	public static final int WAIT_MORE = 0;
	public static final long MAX_BLOCK_SIZE = 32 * 1024 * 1024; // 32MB max block size

	private static final Logger log = LoggerFactory.getLogger(BlockFileReader.class);

	private final ChainState state;

	public BlockFileReader(ChainState state) {
		this.state = state;
	}

	/**
	 * Reads exactly {@code len} bytes from the input stream into {@code buf}.
	 * Unlike InputStream.read(), this method guarantees all bytes are read
	 * or returns the actual number of bytes read if EOF is reached.
	 *
	 * @return the number of bytes actually read, or -1 if the stream is at EOF before any bytes are read
	 */
	private static int readFully(InputStream is, byte[] buf, int len) throws IOException {
		int totalRead = 0;
		while (totalRead < len) {
			int bytesRead = is.read(buf, totalRead, len - totalRead);
			if (bytesRead == -1) {
				return totalRead == 0 ? -1 : totalRead;
			}
			totalRead += bytesRead;
		}
		return totalRead;
	}

	/**
	 * Reads and validates the next block from the file input stream.
	 * Checks magic bytes, parses block header to extract blockId, preBlockId, and time.
	 * Returns a CheckResult with the block data or a status code.
	 * <p>
	 * If the block is only partially written (fullnode still flushing), returns WAIT_MORE
	 * so the parser retries after the file is updated.
	 */
	public CheckResult checkBlock(FileInputStream fis) throws Exception {

		BlockMask blockMask = new BlockMask();
		blockMask.set_pointer(state.getPointer());
		blockMask.set_fileOrder(getFileOrder());

		CheckResult checkResult = new CheckResult();

		byte[] b8 = new byte[8];
		byte[] b4;

		int bytesRead = readFully(fis, b8, 8);
		if (bytesRead == -1) {
			System.out.println("File end when reading magic. pointer: " + state.getPointer());
			log.error("File end when reading magic.");
			checkResult.setBlockLength(FILE_END);
			return checkResult;
		}
		if (bytesRead < 8) {
			// Partial magic+size header — block is being written, wait for completion
			log.debug("Short read on magic+size header: got {} of 8 bytes at pointer {}", bytesRead, state.getPointer());
			checkResult.setBlockLength(WAIT_MORE);
			return checkResult;
		}

		if (b8[0] == 0) {
			if (!BlockFileUtils.getLastBlockFileName(state.getPath()).equals(state.getCurrentFile())) {
				try (FileInputStream fisTemp = new FileInputStream(new File(state.getPath(), state.getCurrentFile()))) {
					long newPointer = state.getPointer() + 8;
					fisTemp.skip(newPointer);
					b8 = new byte[8];
					int tempRead = readFully(fisTemp, b8, 8);
					if (tempRead == 8) {
						b4 = Arrays.copyOfRange(b8, 0, 4);
						if (Arrays.equals(b4, Constants.MAGIC_BYTES)) {
							checkResult.setBlockLength(BLANK_8);
							return checkResult;
						}
					}
				}
			}
			checkResult.setBlockLength(WAIT_MORE);
			return checkResult;
		}

		b4 = Arrays.copyOfRange(b8, 0, 4);
		if (!Arrays.equals(b4, Constants.MAGIC_BYTES)) {
			checkResult.setBlockLength(WRONG);
			return checkResult;
		}

		b4 = Arrays.copyOfRange(b8, 4, 8);
		long blockSize = BytesUtils.bytes4ToLongLE(b4);
		blockMask.setSize(blockSize);

		if (blockSize == 0) {
			checkResult.setBlockLength(WAIT_MORE);
			return checkResult;
		}

		if (blockSize > MAX_BLOCK_SIZE) {
			log.error("Block size {} exceeds maximum {} at pointer {}. Possible corrupted data.",
					blockSize, MAX_BLOCK_SIZE, state.getPointer());
			checkResult.setBlockLength(WRONG);
			return checkResult;
		}

		// Check valid header fork
		if (blockSize == 80) {
			checkResult.setBlockLength(HEADER_FORK);
			System.out.println("Header valid fork was found. Height: " + (state.getBestHeight() + 1));
			log.info("Header valid fork was found. Height: {}", state.getBestHeight() + 1);
			return checkResult;
		}

		byte[] blockBytes = new byte[Math.toIntExact(blockSize)];
		bytesRead = readFully(fis, blockBytes, blockBytes.length);
		if (bytesRead == -1) {
			System.out.println("File end when reading block. pointer: " + state.getPointer());
			log.info("File end when reading block. Pointer: {}", state.getPointer());
			checkResult.setBlockLength(FILE_END);
			return checkResult;
		}
		if (bytesRead < blockBytes.length) {
			// Block partially written — fullnode hasn't finished flushing.
			// Return WAIT_MORE so the parser retries after the file is updated.
			log.warn("Short read on block data: got {} of {} bytes at pointer {}. Block partially written, will retry.",
					bytesRead, blockBytes.length, state.getPointer());
			checkResult.setBlockLength(WAIT_MORE);
			return checkResult;
		}

		ByteArrayInputStream blockInputStream = new ByteArrayInputStream(blockBytes);

		byte[] blockHeadBytes = new byte[80];
		int result = blockInputStream.read(blockHeadBytes);
		if (result == -1) {
			System.out.println("Failed to read blockHeadBytes. the result is -1.");
			log.info("Failed to read blockHeadBytes. the result is -1.");
			checkResult.setBlockLength(WRONG);
			return checkResult;
		}

		String blockId = BytesUtils.bytesToHexStringLE(Hash.sha256x2(blockHeadBytes));
		blockMask.setId(blockId);

		String preId = BytesUtils.bytesToHexStringLE(Arrays.copyOfRange(blockHeadBytes, 4, 4 + 32));
		blockMask.setPreBlockId(preId);

		Long time = BytesUtils.bytes4ToLongLE(Arrays.copyOfRange(blockHeadBytes, 4 + 32 + 32, 4 + 32 + 32 + 4));
		blockMask.setTime(time);

		byte[] blockBodyBytes = new byte[(int) (blockSize - 80)];
		result = blockInputStream.read(blockBodyBytes);
		if (result == -1) {
			System.out.println("Failed to read blockBodyBytes. the result is -1.");
			log.info("Failed to read blockBodyBytes. the result is -1.");
			checkResult.setBlockLength(WRONG);
			return checkResult;
		}

		// Check valid header fork in body
		b4 = Arrays.copyOfRange(blockBodyBytes, 0, 4);
		if (Arrays.equals(b4, Constants.MAGIC_BYTES)) {
			System.out.println("Found valid header fork. Pointer: " + state.getPointer());
			log.info("Found valid header fork. Pointer: {}", state.getPointer());
			checkResult.setBlockLength(HEADER_FORK);
			return checkResult;
		}

		checkResult.setBlockLength(blockSize + 8);
		checkResult.setBlockMark(blockMask);
		checkResult.setBlockBytes(blockBytes);
		return checkResult;
	}

	/** Reads block bytes from cache first, falls back to disk with full read guarantee. */
	public byte[] getBlockBytes(BlockMask bm) throws IOException {
		// Check cache first (P2 optimization)
		byte[] cached = state.getCachedBlockBytes(bm.getId());
		if (cached != null) {
			log.debug("Block bytes cache hit for {}", bm.getId());
			return cached;
		}

		// Read from disk
		File file = new File(state.getPath(), BlockFileUtils.getFileNameWithOrder(bm.get_fileOrder()));
		try (FileInputStream fis = new FileInputStream(file)) {
			fis.skip(bm.get_pointer() + 8);
			byte[] blockBytes = new byte[Math.toIntExact(bm.getSize())];
			int bytesRead = readFully(fis, blockBytes, blockBytes.length);
			if (bytesRead < blockBytes.length) {
				throw new IOException("Short read on block " + bm.getId()
						+ ": got " + bytesRead + " of " + blockBytes.length + " bytes");
			}
			return blockBytes;
		}
	}

	public int getFileOrder() {
		return BlockFileUtils.getFileOrder(state.getCurrentFile());
	}

	/** Result of a checkBlock operation. */
	public static class CheckResult {
		private long blockLength;
		private BlockMask blockMask;
		private byte[] blockBytes;

		public long getBlockLength() { return blockLength; }
		public void setBlockLength(long blockLength) { this.blockLength = blockLength; }

		public BlockMask getBlockMark() { return blockMask; }
		public void setBlockMark(BlockMask blockMask) { this.blockMask = blockMask; }

		public byte[] getBlockBytes() { return blockBytes; }
		public void setBlockBytes(byte[] blockBytes) { this.blockBytes = blockBytes; }
	}
}
