package clients.fcspClient;

import fcData.TransferUnit;

public class TalkUnit extends TransferUnit {
    private TransferUnit.DataType dataType;
    private String data;

    public TransferUnit.DataType getDataType() {
        return dataType;
    }

    public void setDataType(TransferUnit.DataType dataType) {
        this.dataType = dataType;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
