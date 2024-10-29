package fch;

import fch.fchData.OpReturn;

public class opReReadResult {
    private OpReturn opReturn;
    private int length;
    private boolean fileEnd;
    private boolean rollback;

    public OpReturn getOpReturn() {
        return opReturn;
    }

    public void setOpReturn(OpReturn opReturn) {
        this.opReturn = opReturn;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public boolean isFileEnd() {
        return fileEnd;
    }

    public void setFileEnd(boolean fileEnd) {
        this.fileEnd = fileEnd;
    }

    public boolean isRollback() {
        return rollback;
    }

    public void setRollback(boolean rollback) {
        this.rollback = rollback;
    }


}
