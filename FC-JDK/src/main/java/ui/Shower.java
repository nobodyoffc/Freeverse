package ui;

import java.io.BufferedReader;
import java.util.*;
import java.lang.reflect.Field;

import data.fcData.FcEntity;
import data.fcData.FcObject;
import utils.*;

import javax.annotation.Nullable;

import static constants.FieldNames.ME;

public class Shower {
    public static final int DEFAULT_PAGE_SIZE = 40;

    public static <T> List<T> showOrChooseListInPages(String title, List<T> list, Integer pageSize, String myFid, boolean choose, Class<T> tClass, BufferedReader br) {
        if (list == null || list.isEmpty()) {
            System.out.println("Empty list to show");
            return null;
        }
        System.out.println("Total: "+list.size() + " items.");
        List<T> allChosenItems = new ArrayList<>();
        int totalPages = (int) Math.ceil((double) list.size() / pageSize);

        for (int page = 0; page < totalPages; page++) {
            int startIndex = page * pageSize;
            int endIndex = Math.min((page + 1) * pageSize, list.size());
            List<T> pageItems = list.subList(startIndex, endIndex);

            String pageTitle = String.format("%s (Page %d/%d)", title, page + 1, totalPages);
            List<T> chosenItems;
            if(choose && br!=null){
                chosenItems = showOrChooseList(pageTitle, pageItems, myFid, choose, tClass, br);
                if (chosenItems != null) {
                    allChosenItems.addAll(chosenItems);
                }
            }else{
                showOrChooseList(pageTitle, pageItems, myFid, choose, tClass, br);
            }

            // Only ask to continue if there are more pages
            if (br!=null && page < totalPages - 1 && !Inputer.askIfYes(br, "There are "+ (totalPages - page - 1) +" pages left. Continue?")) break;
        }

        return allChosenItems.isEmpty() ? null : allChosenItems;
    }

    public static <T> List<T> showOrChooseList(String title, List<T> objectList, String myFid, boolean choose, Class<T> tClass, BufferedReader br) {
        FcEntity.ShowingRules result = FcEntity.getRules(tClass);
        LinkedHashMap<String, Integer> fieldWidthMap = result.fieldWidthMap();
        List<String> timestampFieldList = result.timestampFieldList();
        List<String> satoshiField = result.satoshiField();
        Map<String, String> heightToTimeFieldMap = result.heightToTimeFieldMap();
        Map<String, String> showFieldNameAs = result.showFieldNameAsMap();
        List<String> replaceWithMeFieldList = result.replaceWithMeFieldList();

        return showOrChooseList(title, objectList, myFid, choose, br, fieldWidthMap, timestampFieldList, satoshiField, heightToTimeFieldMap, showFieldNameAs, replaceWithMeFieldList);
    }

    public static <T> List<T> showOrChooseList(@Nullable String title, List<T> objectList, @Nullable String myFid, boolean choose, @Nullable BufferedReader br, @Nullable LinkedHashMap<String, Integer> fieldWidthMap, @Nullable List<String> timestampFieldList, @Nullable List<String> satoshiField, @Nullable Map<String, String> heightToTimeFieldMap, @Nullable Map<String, String> showFieldNameAs, @Nullable List<String> replaceWithMeFieldList) {
        String[] fields;
        if(fieldWidthMap!=null) fields = fieldWidthMap.keySet().toArray(new String[0]);
        else fields = new String[0];
        int[] widths;
        if(fieldWidthMap!=null) widths = fieldWidthMap.values().stream().mapToInt(Integer::intValue).toArray();
        else widths = new int[0];
        List<List<Object>> valueListList = new ArrayList<>();

        for(T t: objectList) {
            List<Object> valueList = new ArrayList<>();
            for (String field : fields) {
                try {
                    Field fieldObj = getField(t, field);

                    if (fieldObj == null) {
                        valueList.add(null);
                        continue;
                    }

                    fieldObj.setAccessible(true);
                    Object value = fieldObj.get(t);

                    if (value instanceof Boolean) {
                        value = (Boolean)value ? "✓" : "×";
                    }

                    if(timestampFieldList!=null && timestampFieldList.contains(field))
                        value = formatTimestamp(value);

                    if(satoshiField!=null && satoshiField.contains(field)) {
                        value = formatSatoshi(value);
                    }

                    if(heightToTimeFieldMap!=null && heightToTimeFieldMap.containsKey(field)){
                        value = FcUtils.heightToShortDate((long) value);
                    }

                    if(replaceWithMeFieldList!=null && replaceWithMeFieldList.contains(field)){
                        if(myFid !=null && myFid.equals(value))value = ME;
                    }

                    valueList.add(value);
                } catch (Exception e) {
                    valueList.add(null);
                }
            }
            valueListList.add(valueList);
        }
        showOrChooseList(title, fields, widths, valueListList, showFieldNameAs);

        if(choose && br !=null) return choose(objectList, br);
        return null;
    }

    public static <T> Field getField(T t, String field) {
        Field fieldObj = null;
        Class<?> currentClass = t.getClass();
        while (currentClass != null && fieldObj == null) {
            try {
                fieldObj = currentClass.getDeclaredField(field);
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return fieldObj;
    }

    public static <T> List<T> choose(List<T> list, BufferedReader br) {
        List<T> chosenList = new ArrayList<>();
        List<Integer> chosen = core.fch.Inputer.chooseMulti(br,0, list.size());
        if(chosen.size()>0 && chosen.get(0) == -1) return list;
        for(Integer i : chosen){
            chosenList.add(list.get(i-1));
        }
        return chosenList;
    }

    private static Object formatSatoshi(Object value) {
        try{
            value = FchUtils.satoshiToCoin((long)value);
        }catch (Exception ignore){}
        return value;
    }

    public static Object formatTimestamp(Object value) {
        try{
            String valueStr = String.valueOf(value);
            long valueLong = Long.parseLong(valueStr);
            if(valueStr.length()==10) value = DateUtils.longShortToTime(valueLong,DateUtils.TO_MINUTE);
            else value = DateUtils.longToTime(valueLong,DateUtils.LONG_FORMAT);
        }catch (Exception ignore){}

        return value;
    }

    public static void showOrChooseList(String title, String[] fields, int[] widths, List<List<Object>> valueListList, Map<String, String> showFieldNameAs) {
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

        System.out.println("\n" + title);
        int orderWidth = String.valueOf(valueListList.size()).length() + 2;
        int underLineSize = totalWidth + orderWidth+2;
        printUnderline(underLineSize);
        System.out.print(formatString(" ", orderWidth));
        for (int i = 0; i < fields.length; i++) {
            String field = fields[i];
            if(showFieldNameAs!=null && showFieldNameAs.get(field)!=null)field = showFieldNameAs.get(field);
            System.out.print(formatString(StringUtils.capitalize(field), widths[i] + 2));
        }
        System.out.println();
        printUnderline(underLineSize);

        for(int i = 0; i < valueListList.size(); i++){
            int ordinal = i+1;
            System.out.print(formatString(String.valueOf(ordinal), orderWidth));
            List<Object> valueList = valueListList.get(i);
            for (int j = 0; j < valueList.size(); j++) {
                String str;
                Object value = valueList.get(j);
//                // Special handling for numeric values
//                if (value instanceof Double || value instanceof Float) {
//                    str = String.format("%.10f", value).replaceAll("0*$", "").replaceAll("\\.$", "");
//                } else {
//                    str = String.valueOf(value);
//                }

                // Omit middle part if too long
                int width = widths[j];
                if(value instanceof  Number number)
                    str = NumberUtils.formatNumberValue(number,width);
                else{
                    str = String.valueOf(value);
                    if(getVisualWidth(str) > width){
                        if(getVisualWidth(str) < 6){
                            width = getVisualWidth(str);
                        }else {
                            str = omitMiddle(str, width);
                        }
                    }
                }
                if(str.equals("null"))str="";
                System.out.print(formatStringLeft(str, width + 2));
            }
            System.out.println();
        }
        printUnderline(underLineSize);
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

    @Nullable
    public static List<String> showAndChooseFromStringLongMap(BufferedReader br, Map<String, Long> removedItems, String ask) {
        if (removedItems == null || removedItems.isEmpty()) {
            System.out.println("No locally removed items found.");
            return null;
        }

        // Show items and let user choose
        Map<String, Long> selectedDisplayItems = Inputer.chooseMultiFromMapGeneric(
                removedItems,
                null,
                0,
                DEFAULT_PAGE_SIZE,
                ask,
                br
        );

        if (selectedDisplayItems == null || selectedDisplayItems.isEmpty()) {
            return null;
        }

        // Extract IDs from selected display strings
        return selectedDisplayItems.keySet().stream().toList();
    }

    public static Boolean alert(String msg, @Nullable String ok, @Nullable String cancel, @Nullable BufferedReader br) {
        System.out.println(msg);
        if(br ==null)return null;
        if(ok !=null && cancel!=null) {
            String chosenOp = Inputer.chooseOne(new String[]{ok, cancel}, null, "Select an operation", br);
            if (chosenOp.equals(ok)) return true;
            if (chosenOp.equals(cancel)) return false;
        }if(ok!=null){
            Menu.anyKeyToContinue(br);
            return true;
        }else {
            Menu.anyKeyToContinue(br);
            return null;
        }
    }

    public static void showTextAndQR(String text, String promote) {
        System.out.println(promote);
        Shower.printUnderline(10);
        System.out.println(text);
        Shower.printUnderline(10);
        System.out.println("The QR codes:");
        QRCodeUtils.generateQRCode(text);
    }

    public static <T extends FcObject> void replaceMyFidWithMe(List<T> list, String myFid, List<String> fieldList) {
        if (list == null || fieldList == null) return;

        for (T item : list) {
            for (String field : fieldList) {
                try {
                    // Get the field from the class or its superclasses
                    Field fieldObj = getField(item, field);

                    if (fieldObj == null) continue;

                    fieldObj.setAccessible(true);
                    Object value = fieldObj.get(item);

                    if (myFid.equals(value)) {
                        fieldObj.set(item, ME);
                    }
                } catch (Exception e) {
                    // Skip any fields that can't be accessed
                    continue;
                }
            }
        }
    }
}
