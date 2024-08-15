package fch;

import constants.Constants;
import fch.fchData.OpReturn;
import javaTools.BytesTools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class OpReFileTools {

    public static opReReadResult readOpReFromFile(FileInputStream opis) throws IOException {

        opReReadResult result = new opReReadResult();

        boolean fileEnd = false;

        OpReturn op = new OpReturn();

        byte[] length = new byte[4];
        int end = opis.read(length);
        if (end == -1) {
            System.out.println("OpReturn File was parsed completely.");
            fileEnd = true;
            result.setFileEnd(fileEnd);
            opis.close();
            return result;
        }

        int opLength = BytesTools.bytesToIntBE(length);

        byte[] opbytes = new byte[opLength];
        opis.read(opbytes);

        int offset = 0;

        //If rollback record?
        //如果不是回滚记录点
        if (opLength > 40) {

            byte[] txidArr = Arrays.copyOfRange(opbytes, offset, offset + 32);
            offset += 32;
            op.setTxId(BytesTools.bytesToHexStringBE(txidArr));

            byte[] heiArr = Arrays.copyOfRange(opbytes, offset, offset + 8);
            offset += 8;
            op.setHeight(BytesTools.bytes8ToLong(heiArr, false));

            byte[] timeArr = Arrays.copyOfRange(opbytes, offset, offset + 8);
            offset += 8;
            op.setTime(BytesTools.bytes8ToLong(timeArr, false));

            byte[] txIndexArr = Arrays.copyOfRange(opbytes, offset, offset + 4);
            offset += 4;
            op.setTxIndex(BytesTools.bytesToIntBE(txIndexArr));

            byte[] signerArr = Arrays.copyOfRange(opbytes, offset, offset + 34);
            offset += 34;
            op.setSigner(new String(signerArr));

            byte[] recipientArr = Arrays.copyOfRange(opbytes, offset, offset + 34);
            offset += 34;
            op.setRecipient(new String(recipientArr));
            if (op.getRecipient().equals("                                  ")) op.setRecipient(null);

            byte[] cddArr = Arrays.copyOfRange(opbytes, offset, offset + 8);
            offset += 8;
            op.setCdd(BytesTools.bytes8ToLong(cddArr, false));

            byte[] opReArr = Arrays.copyOfRange(opbytes, offset, opLength);
            op.setOpReturn(new String(opReArr));
        } else {
            byte[] heiArr = Arrays.copyOfRange(opbytes, 32, 40);
            op.setHeight(BytesTools.bytes8ToLong(heiArr, false));
            result.setRollback(true);
        }
        result.setOpReturn(op);
        result.setLength(opLength + 4);
        result.setFileEnd(fileEnd);

        return result;
    }

    public static String getLastOpReturnFileName(String opReturnFilePath) {
        for (int i = 0; ; i++) {
            File file = new File(opReturnFilePath + "opreturn" + String.valueOf(i) + ".byte");
            if (!file.exists()) {
                if (i > 0) {
                    return "opreturn" + String.valueOf(i - 1) + ".byte";
                }
            }
        }
    }

    private static int getFileOrder(String currentFile) {
        String s = String.copyValueOf(currentFile.toCharArray(), 8, 1);
        return Integer.parseInt(s);
    }

    private static String getFileNameWithOrder(int i) {
        return "opreturn" + String.format("%d", i) + ".byte";
    }

    public static String getNextFile(String currentFile) {
        return getFileNameWithOrder(getFileOrder(currentFile) + 1);
    }

    public void writeOpReturnListIntoFile(ArrayList<OpReturn> opList) throws IOException {

        if (opList == null || opList.isEmpty()) return;
        String fileName = Constants.OPRETURN_FILE_NAME;
        File opFile;
        FileOutputStream opos;

        while (true) {
            opFile = new File(Constants.OPRETURN_FILE_DIR, fileName);
            if (opFile.length() > Constants.MaxOpFileSize) {
                fileName = getNextFile(fileName);
            } else break;
        }
        if (opFile.exists()) {
            opos = new FileOutputStream(opFile, true);
        } else {
            opos = new FileOutputStream(opFile);
        }

        Iterator<OpReturn> iterOp = opList.iterator();
        while (iterOp.hasNext()) {
            ArrayList<byte[]> opArrList = new ArrayList<byte[]>();
            OpReturn op = iterOp.next();

            opArrList.add(BytesTools.intToByteArray(128 + op.getOpReturn().getBytes().length));
            opArrList.add(BytesTools.hexToByteArray(op.getTxId()));
            opArrList.add(BytesTools.longToBytes(op.getHeight()));
            opArrList.add(BytesTools.longToBytes(op.getTime()));
            opArrList.add(BytesTools.intToByteArray(op.getTxIndex()));
            opArrList.add(op.getSigner().getBytes());
            if (op.getRecipient() == null || op.getRecipient().equals("nobody")) {
                opArrList.add("                                  ".getBytes());
            } else {
                opArrList.add(op.getRecipient().getBytes());
            }
            opArrList.add(BytesTools.longToBytes(op.getCdd()));
            opArrList.add(op.getOpReturn().getBytes());

            opos.write(BytesTools.bytesMerger(opArrList));
        }
        opos.flush();
        opos.close();
    }

}
