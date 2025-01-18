package fchData;

public class BlockMark {
    private String blockId;
    private String preBlockId;
    private long height;
    private long size;        //block size
    private String status;

    //parsing info
    private int _fileOrder;        //The order number of the file that the block is located in.
    private long _pointer;        //The position of the beginning of the block in the file.
    private long orphanHeight;        //The number of orphan when writing this block to es. Only the point with _pend being 0 can be rollback to.

    //orphanHeight<=rollHeight  && height>rollHeight 的blockMark恢复为orphan

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public String getPreBlockId() {
        return preBlockId;
    }

    public void setPreBlockId(String preBlockId) {
        this.preBlockId = preBlockId;
    }

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }

    public int get_fileOrder() {
        return _fileOrder;
    }

    public void set_fileOrder(int _fileOrder) {
        this._fileOrder = _fileOrder;
    }

    public long get_pointer() {
        return _pointer;
    }

    public void set_pointer(long _pointer) {
        this._pointer = _pointer;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getOrphanHeight() {
        return orphanHeight;
    }

    public void setOrphanHeight(long orphanHeight) {
        this.orphanHeight = orphanHeight;
    }

}
