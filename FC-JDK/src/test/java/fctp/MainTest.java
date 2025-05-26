package fctp;

public class MainTest {

    public static void main(String[] args) {
        FctpWorker fctpWorker = new FctpWorker("127.0.0.1",4455,4466,false,false);
        fctpWorker.start();

        MsgUnit sendMsgUnit = new MsgUnit();
        sendMsgUnit.setFrom("FUmo2eez6VK2sfGWjek9i9aK5y1mdHSnqv");
        sendMsgUnit.setToType(MsgUnit.ToType.FID);
        sendMsgUnit.setTo("F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW");
        sendMsgUnit.setDataType(MsgUnit.DataType.TEXT);
        sendMsgUnit.setData("hello");
        fctpWorker.sendOne(sendMsgUnit);

        MsgUnit msgUnit = fctpWorker.receiveOne();
        if(msgUnit !=null) System.out.println(msgUnit.toJson());
        else System.out.println("Nothing was gotten.");
    }
}
