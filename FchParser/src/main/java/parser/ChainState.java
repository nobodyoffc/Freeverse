package parser;

import data.fchData.BlockMask;

import java.util.*;

/**
 * Encapsulates all mutable chain state for the FchParser.
 * Replaces the static mutable fields previously in Preparer.
 * <p>
 * Uses HashMap-backed collections for O(1) lookups by block ID,
 * plus an ordered ArrayList for the main chain (needed for reorg tail access).
 */
public class ChainState {

	// Configurable reorg protection depth (default 30)
	private int reorgProtect = Preparer.DEFAULT_REORG_PROTECT;

	// --- Scalar state (volatile for cross-thread visibility) ---

	private volatile String path;
	private volatile String currentFile;
	private volatile long pointer;
	private volatile String bestHash;
	private volatile long bestHeight;
	private volatile BlockMask bestBlockMask;
	private volatile BlockMask beforeBestBlockMask;

	// Lock for compound operations on collections
	public final Object chainLock = new Object();

	// Main chain: ordered list + HashMap index for O(1) ID lookups
	private final ArrayList<BlockMask> mainList = new ArrayList<>();
	private final HashMap<String, BlockMask> mainById = new HashMap<>();

	// Fork blocks: HashMap for O(1) lookups by ID
	private final HashMap<String, BlockMask> forkById = new HashMap<>();

	// Orphan blocks: LinkedHashMap preserves insertion order for iteration
	private final LinkedHashMap<String, BlockMask> orphanById = new LinkedHashMap<>();

	// Block bytes cache for reorg optimization (LRU, bounded)
	private static final int MAX_CACHE_SIZE = 200;
	private final LinkedHashMap<String, byte[]> blockBytesCache =
			new LinkedHashMap<String, byte[]>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
					return size() > MAX_CACHE_SIZE;
				}
			};

	// --- Scalar getters/setters ---

	public String getPath() { return path; }
	public void setPath(String path) { this.path = path; }

	public String getCurrentFile() { return currentFile; }
	public void setCurrentFile(String currentFile) { this.currentFile = currentFile; }

	public long getPointer() { return pointer; }
	public void setPointer(long pointer) { this.pointer = pointer; }

	public String getBestHash() { return bestHash; }
	public void setBestHash(String bestHash) { this.bestHash = bestHash; }

	public long getBestHeight() { return bestHeight; }
	public void setBestHeight(long bestHeight) { this.bestHeight = bestHeight; }

	public BlockMask getBestBlockMask() { return bestBlockMask; }
	public void setBestBlockMask(BlockMask bestBlockMask) { this.bestBlockMask = bestBlockMask; }

	public BlockMask getBeforeBestBlockMask() { return beforeBestBlockMask; }
	public void setBeforeBestBlockMask(BlockMask m) { this.beforeBestBlockMask = m; }

	public int getReorgProtect() { return reorgProtect; }
	public void setReorgProtect(int reorgProtect) { this.reorgProtect = reorgProtect; }

	// --- Main chain operations ---

	public void addToMain(BlockMask bm) {
		synchronized (chainLock) {
			mainList.add(bm);
			mainById.put(bm.getId(), bm);
		}
	}

	public void removeFromMain(Collection<BlockMask> bms) {
		synchronized (chainLock) {
			mainList.removeAll(bms);
			for (BlockMask bm : bms) {
				mainById.remove(bm.getId());
			}
		}
	}

	public void trimMainToSize(int maxSize) {
		synchronized (chainLock) {
			while (mainList.size() > maxSize) {
				BlockMask removed = mainList.remove(0);
				mainById.remove(removed.getId());
			}
		}
	}

	public boolean mainContainsId(String id) {
		synchronized (chainLock) {
			return mainById.containsKey(id);
		}
	}

	public BlockMask getMainById(String id) {
		synchronized (chainLock) {
			return mainById.get(id);
		}
	}

	public BlockMask getLastMain() {
		synchronized (chainLock) {
			return mainList.isEmpty() ? null : mainList.get(mainList.size() - 1);
		}
	}

	public int mainSize() {
		synchronized (chainLock) {
			return mainList.size();
		}
	}

	/**
	 * Returns main chain blocks from the best tip going backwards until the block
	 * with the given ID is found (exclusive). Used to build the "lose list" during reorgs.
	 * @return list in reverse order (best tip first), or null if blockId not found within maxDepth.
	 */
	public List<BlockMask> getMainTailUntil(String blockId, int maxDepth) {
		synchronized (chainLock) {
			List<BlockMask> result = new ArrayList<>();
			int start = Math.max(0, mainList.size() - maxDepth);
			for (int i = mainList.size() - 1; i >= start; i--) {
				if (mainList.get(i).getId().equals(blockId)) {
					return result;
				}
				result.add(mainList.get(i));
			}
			return null;
		}
	}

	public void initMainList(ArrayList<BlockMask> list) {
		synchronized (chainLock) {
			mainList.clear();
			mainById.clear();
			mainList.addAll(list);
			for (BlockMask bm : list) {
				mainById.put(bm.getId(), bm);
			}
		}
	}

	// --- Fork operations ---

	public void addToFork(BlockMask bm) {
		synchronized (chainLock) {
			forkById.put(bm.getId(), bm);
		}
	}

	public void removeFromFork(Collection<BlockMask> bms) {
		synchronized (chainLock) {
			for (BlockMask bm : bms) {
				forkById.remove(bm.getId());
			}
		}
	}

	public BlockMask getForkById(String id) {
		synchronized (chainLock) {
			return forkById.get(id);
		}
	}

	public boolean forkContainsId(String id) {
		synchronized (chainLock) {
			return forkById.containsKey(id);
		}
	}

	public void dropOldForks(long newHeight) {
		synchronized (chainLock) {
			forkById.values().removeIf(bm -> bm.getHeight() < newHeight - reorgProtect);
		}
	}

	public int forkSize() {
		synchronized (chainLock) {
			return forkById.size();
		}
	}

	public void initForkList(ArrayList<BlockMask> list) {
		synchronized (chainLock) {
			forkById.clear();
			for (BlockMask bm : list) {
				forkById.put(bm.getId(), bm);
			}
		}
	}

	// --- Orphan operations ---

	public void addToOrphan(BlockMask bm) {
		synchronized (chainLock) {
			orphanById.put(bm.getId(), bm);
		}
	}

	public void removeOrphan(String id) {
		synchronized (chainLock) {
			orphanById.remove(id);
		}
	}

	/** Returns a snapshot of orphan values for safe iteration. */
	public List<BlockMask> getOrphanSnapshot() {
		synchronized (chainLock) {
			return new ArrayList<>(orphanById.values());
		}
	}

	public int orphanSize() {
		synchronized (chainLock) {
			return orphanById.size();
		}
	}

	public void initOrphanList(ArrayList<BlockMask> list) {
		synchronized (chainLock) {
			orphanById.clear();
			for (BlockMask bm : list) {
				orphanById.put(bm.getId(), bm);
			}
		}
	}

	// --- Block bytes cache (for reorg optimization) ---

	public void cacheBlockBytes(String blockId, byte[] bytes) {
		synchronized (blockBytesCache) {
			blockBytesCache.put(blockId, bytes);
		}
	}

	public byte[] getCachedBlockBytes(String blockId) {
		synchronized (blockBytesCache) {
			return blockBytesCache.get(blockId);
		}
	}

	public void evictBlockBytes(String blockId) {
		synchronized (blockBytesCache) {
			blockBytesCache.remove(blockId);
		}
	}
}
