package appTools;

import javaTools.StringTools;

import java.io.BufferedReader;
import java.util.Arrays;
import java.util.List;

public class Shower {

    public static String showDataTable(String title, String[] fields, int[] widths, List<List<Object>> valueListList, int beginFrom) {
        if (fields == null || valueListList == null || fields.length == 0 || valueListList.size() == 0) {
            System.out.println("Empty fields.");
            return null;
        }

        if (widths == null || widths.length == 0) {
            widths = new int[fields.length];
            Arrays.fill(widths, 20);
        }

        if (fields.length != widths.length || fields.length != valueListList.get(0).size()) {
            System.out.println("Wrong fields number.");
            return null;
        }

        int totalWidth = 0;
        for (int width : widths) totalWidth += (width + 2);

        System.out.println("\n<" + title + ">");
        printUnderline(totalWidth);
        int ordinalLength = String.valueOf(valueListList.size()).length();
        System.out.print(formatString("", ordinalLength +1));
        for (int i = 0; i < fields.length; i++) {
            System.out.print(formatString(fields[i], widths[i] + 2));
        }
        System.out.println();
        printUnderline(totalWidth);

        for(int i = 0; i < valueListList.size(); i++){
            System.out.print(formatString(String.valueOf(i+beginFrom+1),ordinalLength+1));
            List<Object> valueList = valueListList.get(i);
            for (int j = 0; j < valueList.size(); j++) {
                String str = String.valueOf(valueList.get(j));
                int width = widths[j];
                if(getVisualWidth(str) > width){
                    if(getVisualWidth(str) < 6){
                        width = getVisualWidth(str);
                    }else {
                        str = omitMiddle(str, width);
                    }
                }
                System.out.print(formatStringLeft(str, width + 2));
            }
            System.out.println();
        }
        printUnderline(totalWidth);
        return null;
    }

    public static void printUnderline(int num) {
        for (int i = 0; i < num; i++) {
            System.out.print("_");
        }
        System.out.println();
    }

    public static String formatString(String str, int length) {
        return String.format("%-" + length + "s", str);
    }

    public static String formatStringLeft(String str, int length) {
        int visualWidth = getVisualWidth(str);
        int padding = length - visualWidth;
        return str + " ".repeat(Math.max(0, padding));
    }

    public static int choose(BufferedReader br, int min, int max) {
        System.out.println("\nInput the number to choose. '0' to quit:\n");
        int choice = 0;
        while (true) {
            String input = null;
            try {
                input = br.readLine();
                choice = Integer.parseInt(input);
            } catch (Exception e) {
            }
            if (choice <= max && choice >= min) break;
            System.out.println("\nInput an integer within:" + min + "~" + max + ".");
        }
        return choice;
    }

    public static int getVisualWidth(String str) {
        return str.codePoints().map(ch -> Character.isIdeographic(ch) ? 2 : 1).sum();
    }

    public static String omitMiddle(String str, int maxWidth) {
        if (getVisualWidth(str) <= maxWidth) {
            return str;
        }
        int ellipsisWidth = 3; // Width of "..."
        int halfMaxWidth = (maxWidth - ellipsisWidth) / 2;
        StringBuilder result = new StringBuilder();
        int currentWidth = 0;
        for (char ch : str.toCharArray()) {
            int charWidth = Character.isIdeographic(ch) ? 2 : 1;
            if (currentWidth < halfMaxWidth) {
                result.append(ch);
                currentWidth += charWidth;
            } else {
                break;
            }
        }
        result.append("...");
        currentWidth = getVisualWidth(result.toString());
        for (int i = str.length() - 1; i >= 0; i--) {
            char ch = str.charAt(i);
            int charWidth = Character.isIdeographic(ch) ? 2 : 1;
            if (currentWidth + charWidth <= maxWidth) {
                result.append(ch);
                currentWidth += charWidth;
            } else {
                break;
            }
        }
        return result.toString();
    }
}
