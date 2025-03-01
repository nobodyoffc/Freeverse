package appTools;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import tools.DateTools;
import tools.StringTools;
import constants.Constants;

public class Shower {
    public static final int DEFAULT_SIZE = 2;

    public static <T> void showDataTable(String title, List<T> dataList, int beginFrom, List<String> hideFields, boolean isShortTimestamp) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }
        // Get fields from first element
        T firstElement = dataList.get(0);
        java.lang.reflect.Field[] classFields = firstElement.getClass().getDeclaredFields();
        
        // Filter out hidden fields
        List<java.lang.reflect.Field> visibleFields = Arrays.stream(classFields)
            .filter(field -> hideFields == null || !hideFields.contains(field.getName()))
            .toList();
        
        String[] fields = visibleFields.stream()
            .map(java.lang.reflect.Field::getName)
            .toArray(String[]::new);

        // Calculate max width for each field
        int[] widths = new int[fields.length];
        Arrays.fill(widths, 0);

        // Initialize with field name lengths
        for (int i = 0; i < fields.length; i++) {
            widths[i] = Math.max(widths[i], fields[i].length());
        }

        // Find max value length for each field
        for (T element : dataList) {
            for (int i = 0; i < visibleFields.size(); i++) {
                visibleFields.get(i).setAccessible(true);
                try {
                    Object value = visibleFields.get(i).get(element);
                    if (value != null) {
                        widths[i] = Math.max(widths[i], value.toString().length());
                    }
                } catch (IllegalAccessException e) {
                    // Skip if can't access
                }
            }
        }

        // Convert data to List<List<Object>> format
        List<List<Object>> valueListList = new ArrayList<>();
        for (T element : dataList) {
            List<Object> row = new ArrayList<>();
            for (java.lang.reflect.Field field : visibleFields) {
                field.setAccessible(true);
                try {
                    row.add(field.get(element));
                } catch (IllegalAccessException e) {
                    row.add(null);
                }
            }
            valueListList.add(row);
        }

        // Call existing showDataTable method
        showDataTable(title, fields, widths, valueListList, beginFrom, isShortTimestamp);
    }

    // Add overloaded method for backward compatibility
    public static <T> void showDataTable(String title, List<T> dataList, int beginFrom, boolean isShortTimestamp) {
        showDataTable(title, dataList, beginFrom, null, isShortTimestamp);
    }

    public static void showDataTable(String title, Map<String, Integer> fieldWidthMap, List<List<Object>> valueListList, int beginFrom) {
        String[] fields = fieldWidthMap.keySet().toArray(new String[0]);
        int[] widths = fieldWidthMap.values().stream().mapToInt(Integer::intValue).toArray();
        showDataTable(title, fields, widths, valueListList, beginFrom, true);
    }

    public static void showDataTable(String title, String[] fields, int[] widths, List<List<Object>> valueListList, int beginFrom, boolean isShortTimestamp) {
        if(valueListList==null || valueListList.isEmpty()){
            System.out.println("Nothing to show.");
            return;
        }

        if (fields == null || fields.length == 0) {
            System.out.println("Empty fields.");
            return;
        }

        if (widths == null || widths.length == 0) {
            widths = new int[fields.length];
            Arrays.fill(widths, 20);
        }

        if (fields.length != widths.length || fields.length != valueListList.get(0).size()) {
            System.out.println("Wrong fields number.");
            return;
        }

        int totalWidth = 0;
        for (int width : widths) totalWidth += (width + 2);

        System.out.println("\n<" + title + ">");
        int orderWidth = String.valueOf(valueListList.size()).length() + 2;
        printUnderline(totalWidth + orderWidth);
        System.out.print(formatString(" ", orderWidth));
        for (int i = 0; i < fields.length; i++) {
            System.out.print(formatString(StringTools.capitalize(fields[i]), widths[i] + 2));
        }
        System.out.println();
        printUnderline(totalWidth + String.valueOf(valueListList.size()).length() + 2);

        for(int i = 0; i < valueListList.size(); i++){
            int ordinal = i+beginFrom+1;
            System.out.print(formatString(String.valueOf(ordinal), orderWidth));
            List<Object> valueList = valueListList.get(i);
            for (int j = 0; j < valueList.size(); j++) {
                String str;
                Object value = valueList.get(j);
                // Special handling for numeric values
                if (value instanceof Double || value instanceof Float) {
                    str = String.format("%.10f", value).replaceAll("0*$", "").replaceAll("\\.$", "");
                } else {
                    str = String.valueOf(value);
                }

                // Check if value is a timestamp (Long value greater than year 2000 in milliseconds)
                if (value instanceof Long) {  // 2000-01-01 00:00:00
                    try {
                        long timestamp;
                        if(isShortTimestamp && (Long)value > Constants.TIMESTAMP_2000_SECONDS && (Long)value < Constants.TIMESTAMP_2100_SECONDS){
                            timestamp = ((Long) value)*1000;
                            str = DateTools.longToTime(timestamp, DateTools.SHORT_FORMAT);
                        }
                        else if((Long)value > Constants.TIMESTAMP_2000 && (Long)value < Constants.TIMESTAMP_2100){ //From 2000-01-01 00:00:00 to 2100-01-01 00:00:00
                            timestamp = (Long) value;
                            str = DateTools.longToTime(timestamp, DateTools.SHORT_FORMAT);
                        }
                        else{
                            str = String.valueOf(value);
                        }
                    } catch (Exception e) {
                        // Keep original string if date conversion fails
                    }
                }

                // Omit middle part if too long
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
        printUnderline(totalWidth + String.valueOf(valueListList.size()).length() + 2);
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
        
        // Add first half
        for (char ch : str.substring(0, str.length()/2).toCharArray()) {
            int charWidth = Character.isIdeographic(ch) ? 2 : 1;
            if (currentWidth + charWidth <= halfMaxWidth) {
                result.append(ch);
                currentWidth += charWidth;
            } else {
                break;
            }
        }
        
        result.append("...");
        
        // Add last half (starting from middle)
        int remainingWidth = maxWidth - getVisualWidth(result.toString());
        String lastHalf = str.substring(str.length()/2);
        int startPos = Math.max(0, lastHalf.length() - remainingWidth);
        result.append(lastHalf.substring(startPos));
        
        return result.toString();
    }

    public static void showStringList(List<String> strList, int startWith) {
        if (strList == null || strList.isEmpty()) {
            System.out.println("Nothing to show.");
            return;
        }

        // Calculate the width needed for order numbers
        int orderWidth = String.valueOf(strList.size() + startWith).length() + 2;

        // Print each string with its order number
        for (int i = 0; i < strList.size(); i++) {
            int ordinal = i + startWith + 1;
            System.out.print(formatString(String.valueOf(ordinal), orderWidth));
            System.out.println(strList.get(i));
        }
    }
}
