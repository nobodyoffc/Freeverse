package core.fch;

import constants.Constants;
import data.fchData.OpReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.BytesUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class OpReFileUtils {

    private static final Logger log = LoggerFactory.getLogger(OpReFileUtils.class);

    private static int readFully(RandomAccessFile raf, byte[] buf, int len) throws IOException {
        int totalRead = 0;
        while (totalRead < len) {
            int bytesRead = raf.read(buf, totalRead, len - totalRead);
            if (bytesRead == -1) {
                return totalRead == 0 ? -1 : totalRead;
            }
            totalRead += bytesRead;
        }
        return totalRead;
    }

    /** A rollback marker record: 32 bytes of padding followed by an 8-byte height. */
    private static final int ROLLBACK_RECORD_SIZE = 40;
    /** Records at or below this length are rollback markers, not full OpReturn records. */
    private static final int ROLLBACK_MAX_LENGTH = 40;
    /** txid(32) + height(8) + time(8) + txIndex(4) + signer(34) + recipient(34) + cdd(8) + paid(8). */
    private static final int FULL_RECORD_HEADER_SIZE = 136;
    /** Generous upper bound; real records are orders of magnitude smaller than this. */
    private static final int MAX_OP_RECORD_SIZE = 4 * 1024 * 1024;

    public static opReReadResult readOpReFromFile(RandomAccessFile raf) throws IOException {

        opReReadResult result = new opReReadResult();

        boolean fileEnd = false;

        OpReturn op = new OpReturn();

        long startPos = raf.getFilePointer();
        byte[] length = new byte[4];
        int bytesRead = readFully(raf, length, 4);
        if (bytesRead == -1) {
            System.out.println("OpReturn File was parsed completely.");
            fileEnd = true;
            result.setFileEnd(fileEnd);
            return result;
        }
        if (bytesRead < 4) {
            System.out.println("OpReturn File: short read on length header, treating as file end.");
            fileEnd = true;
            result.setFileEnd(fileEnd);
            raf.seek(startPos);
            return result;
        }

        int opLength = BytesUtils.bytesToIntBE(length);

        // The length is read straight off disk, so it has to be treated as untrusted: a negative
        // or absurd value would otherwise reach `new byte[opLength]` and throw
        // NegativeArraySizeException or exhaust the heap. A record is either a rollback marker
        // (exactly ROLLBACK_RECORD_SIZE) or a full record whose fixed header alone is
        // FULL_RECORD_HEADER_SIZE bytes; anything else is corruption, not a torn write.
        if (opLength < ROLLBACK_RECORD_SIZE || opLength > MAX_OP_RECORD_SIZE
                || (opLength > ROLLBACK_MAX_LENGTH && opLength < FULL_RECORD_HEADER_SIZE)) {
            log.error("OpReturn File: implausible record length {} at position {}. Treating as end "
                    + "of file; the file is corrupt at this offset.", opLength, startPos);
            result.setFileEnd(true);
            raf.seek(startPos);
            return result;
        }

        byte[] opbytes = new byte[opLength];
        bytesRead = readFully(raf, opbytes, opLength);
        if (bytesRead < opLength) {
            System.out.println("OpReturn File: short read on record data (" + bytesRead + "/" + opLength + "), treating as file end.");
            fileEnd = true;
            result.setFileEnd(fileEnd);
            raf.seek(startPos);
            return result;
        }

        int offset = 0;

        //If rollback record?
        //如果不是回滚记录点
        if (opLength > ROLLBACK_MAX_LENGTH) {

            byte[] txidArr = Arrays.copyOfRange(opbytes, offset, offset + 32);
            offset += 32;
            op.setId(BytesUtils.bytesToHexStringBE(txidArr));

            byte[] heiArr = Arrays.copyOfRange(opbytes, offset, offset + 8);
            offset += 8;
            op.setHeight(BytesUtils.bytes8ToLong(heiArr, false));

            byte[] timeArr = Arrays.copyOfRange(opbytes, offset, offset + 8);
            offset += 8;
            op.setTime(BytesUtils.bytes8ToLong(timeArr, false));

            byte[] txIndexArr = Arrays.copyOfRange(opbytes, offset, offset + 4);
            offset += 4;
            op.setTxIndex(BytesUtils.bytesToIntBE(txIndexArr));

            byte[] signerArr = Arrays.copyOfRange(opbytes, offset, offset + 34);
            offset += 34;
            op.setSigner(new String(signerArr));

            byte[] recipientArr = Arrays.copyOfRange(opbytes, offset, offset + 34);
            offset += 34;
            op.setRecipient(new String(recipientArr));
            if (op.getRecipient().equals("                                  ")) op.setRecipient(null);

            byte[] cddArr = Arrays.copyOfRange(opbytes, offset, offset + 8);
            offset += 8;
            op.setCdd(BytesUtils.bytes8ToLong(cddArr, false));

            byte[] paidArr = Arrays.copyOfRange(opbytes, offset, offset + 8);
            offset += 8;
            op.setPaid(BytesUtils.bytes8ToLong(paidArr, false));

            byte[] opReArr = Arrays.copyOfRange(opbytes, offset, opLength);
            op.setOpReturn(new String(opReArr));
        } else {
            byte[] heiArr = Arrays.copyOfRange(opbytes, 32, 40);
            op.setHeight(BytesUtils.bytes8ToLong(heiArr, false));
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

            opArrList.add(BytesUtils.intToByteArray(136 + op.getOpReturn().getBytes().length));
            opArrList.add(BytesUtils.hexToByteArray(op.getId()));
            opArrList.add(BytesUtils.longToBytes(op.getHeight()));
            opArrList.add(BytesUtils.longToBytes(op.getTime()));
            opArrList.add(BytesUtils.intToByteArray(op.getTxIndex()));
            opArrList.add(op.getSigner().getBytes());
            if (op.getRecipient() == null || op.getRecipient().equals("nobody")) {
                opArrList.add("                                  ".getBytes());
            } else {
                opArrList.add(op.getRecipient().getBytes());
            }
            opArrList.add(BytesUtils.longToBytes(op.getCdd()));
            opArrList.add(BytesUtils.longToBytes(op.getPaid()!=null? op.getPaid() : 0L));
            opArrList.add(op.getOpReturn().getBytes());

            opos.write(BytesUtils.bytesMerger(opArrList));
        }
        opos.flush();
        opos.close();
    }

}
