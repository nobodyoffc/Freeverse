package avatar;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class AvatarMaker {

    private static Map<String, Integer> data = new HashMap<>();

    static {
        data.put("1", 0);
        data.put("2", 1);
        data.put("3", 2);
        data.put("4", 3);
        data.put("5", 4);
        data.put("6", 5);
        data.put("7", 6);
        data.put("8", 7);
        data.put("9", 8);
        data.put("A", 9);
        data.put("B", 10);
        data.put("C", 11);
        data.put("D", 12);
        data.put("E", 13);
        data.put("F", 14);
        data.put("G", 15);
        data.put("H", 16);
        data.put("J", 17);
        data.put("K", 18);
        data.put("L", 19);
        data.put("M", 20);
        data.put("N", 21);
        data.put("P", 22);
        data.put("Q", 23);
        data.put("R", 24);
        data.put("S", 25);
        data.put("T", 26);
        data.put("U", 27);
        data.put("V", 28);
        data.put("W", 29);
        data.put("X", 30);
        data.put("Y", 31);
        data.put("Z", 32);
        data.put("a", 33);
        data.put("b", 34);
        data.put("c", 35);
        data.put("d", 36);
        data.put("e", 37);
        data.put("f", 38);
        data.put("g", 39);
        data.put("h", 40);
        data.put("i", 41);
        data.put("j", 42);
        data.put("k", 43);
        data.put("m", 44);
        data.put("n", 45);
        data.put("o", 46);
        data.put("p", 47);
        data.put("q", 48);
        data.put("r", 49);
        data.put("s", 50);
        data.put("t", 51);
        data.put("u", 52);
        data.put("v", 53);
        data.put("w", 54);
        data.put("x", 55);
        data.put("y", 56);
        data.put("z", 57);
    }


    public static String[] getAvatars(String[] addrArray, String basePath, String filePath) throws IOException {

        if (!filePath.endsWith("/")) filePath = filePath + "/";
        if (!basePath.endsWith("/")) basePath = basePath + "/";

        String[] pngFilePaths = new String[addrArray.length];
        for (int i = 0; i < addrArray.length; i++) {
            String addr = addrArray[i];
            pngFilePaths[i] = getAvatar(addr, basePath, filePath + addr + ".png");
        }
        return pngFilePaths;
    }

    private static String getAvatar(String addr, String basePath, String filePath) throws IOException {

        String[] keys = getPathByAddress(addr);

        File fileFile = new File(filePath);
        if (!fileFile.getParentFile().exists()) {
            boolean success = fileFile.getParentFile().mkdirs();
            if (!success) {
                return null;
            }
        }
        return addImgs(keys, basePath, filePath);
    }

    /**
     * 根据地址获取图片
     *
     * @param address 图片key
     * @return 图片路径
     */
    private static String[] getPathByAddress(String address) {
        String[] tempStr = address.split("");
        for (int i = 0; i < 10; i++) {
            tempStr[i] = getType(tempStr[33 - 4 - i], i);
        }
        return tempStr;
    }

    /**
     * 获取头像类型
     */
    private static String getType(String c, Integer i) {
        return i + "/" + data.get(c) + ".png";
    }

    /**
     * 合并图片
     *
     * @param keys 图片获取key
     * @return
     */
    private static String addImgs(String[] keys, String basePath, String filePath) throws IOException {
        //完成后的图片
        File toPicBig = new File(filePath);
        //检查路径
        if (!toPicBig.getParentFile().exists()) {
            boolean success = toPicBig.getParentFile().mkdirs();
            if (!success) {
                return null;
            }
        }

        OutputStream outputStream = new ByteArrayOutputStream();
        Thumbnails.of(basePath + keys[0]).size(150, 150)
                .watermark(Positions.TOP_LEFT, ImageIO.read(new File(basePath + keys[1])), 1f)
                .watermark(Positions.TOP_LEFT, ImageIO.read(new File(basePath + keys[2])), 1f)
                .watermark(Positions.TOP_LEFT, ImageIO.read(new File(basePath + keys[3])), 1f)
                .watermark(Positions.TOP_LEFT, ImageIO.read(new File(basePath + keys[4])), 1f)
                .watermark(Positions.TOP_LEFT, ImageIO.read(new File(basePath + keys[5])), 1f)
                .watermark(Positions.TOP_LEFT, ImageIO.read(new File(basePath + keys[6])), 1f)
                .watermark(Positions.TOP_LEFT, ImageIO.read(new File(basePath + keys[7])), 1f)
                .watermark(Positions.TOP_LEFT, ImageIO.read(new File(basePath + keys[8])), 1f)
                .watermark(Positions.TOP_LEFT, ImageIO.read(new File(basePath + keys[9])), 1f)
                .outputQuality(0.5f)
                .toFile(filePath);
        return filePath;
    }
}

