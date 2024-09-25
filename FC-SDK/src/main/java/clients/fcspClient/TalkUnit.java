package clients.fcspClient;

public class TalkUnit extends fcData.TalkUnit {
    private fcData.TalkUnit.DataType dataType;
    private String data;

    public fcData.TalkUnit.DataType getDataType() {
        return dataType;
    }

    public void setDataType(fcData.TalkUnit.DataType dataType) {
        this.dataType = dataType;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
