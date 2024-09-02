package appTools;

import crypto.KeyTools;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.NumberTools;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static constants.Strings.CONFIG;
import static constants.Values.FALSE;


public class Inputer {

    private static char[] inputPassword(String ask) {
        System.out.println(ask);
        Console console = System.console();
        if (console == null) {
            System.out.println("Couldn't get Console instance. Maybe you're running this from within an IDE, which doesn't support Console.");
            return null;
        }
        return console.readPassword(ask);
    }

    public static char[] inputPassword(BufferedReader br, String ask) {
        System.out.println(ask);
        char[] input = new char[64];
        int num = 0;
        try {
            num = br.read(input);
        } catch (IOException e) {
            System.out.println("BufferReader wrong.");
            return null;
        }
        if (num == 0) return null;
        char[] password = new char[num - 1];
        System.arraycopy(input, 0, password, 0, num - 1);
        if(password.length==0)password=null;
        return password;
    }

    public static String inputString(BufferedReader br) {
        String input = null;
        try {
            input = br.readLine();
        } catch (IOException e) {
            System.out.println("BufferedReader is wrong. Can't read.");
        }
        return input;
    }

    public static String inputString(BufferedReader br, String ask) {
        System.out.println(ask);
        return inputString(br);
    }

    public static Double inputGoodShare(BufferedReader br) {
        while (true) {
            String ask = "Input the share(0~1). Enter to quit.";
            Double share = inputDouble(br, ask);
            if (share == null) return null;
            if (share > 1) {
                System.out.println("A share should less than 1. ");
                continue;
            }
            return NumberTools.roundDouble4(share);
        }
    }

    public static Double inputDouble(BufferedReader br, String ask) {

        while (true) {
            System.out.println(ask);
            String inputStr;
            double input;
            try {
                inputStr = br.readLine();
            } catch (IOException e) {
                System.out.println("br.readLine() wrong.");
                return null;
            }
            if ("".equals(inputStr)) return null;
            try {
                input = Double.parseDouble(inputStr);
                return input;
            } catch (Exception e) {
                System.out.println("Input a number. Try again.");
            }
        }
    }

    public static String inputDoubleAsString(BufferedReader br, String ask) {

        while (true) {
            System.out.println(ask);
            String inputStr;
            try {
                inputStr = br.readLine();
            } catch (IOException e) {
                System.out.println("br.readLine() wrong.");
                return null;
            }
            if ("".equals(inputStr)) return null;
            try {
                Double.parseDouble(inputStr);
                return inputStr;
            } catch (Exception e) {
                System.out.println("Input a number. Try again.");
            }
        }
    }

    public static String[] inputStringArray(BufferedReader br, String ask, int len) {
        ArrayList<String> itemList = inputStringList(br, ask, len);
        if (itemList.isEmpty()) return new String[0];
        return itemList.toArray(new String[itemList.size()]);
    }

    @NotNull
    public static ArrayList<String> inputStringList(BufferedReader br, String ask, int len) {
        ArrayList<String> itemList = new ArrayList<String>();
        System.out.println(ask);
        while (true) {
            String item = Inputer.inputString(br);
            if (item.equals("")) break;
            if (len > 0) {
                if (item.length() != len) {
                    System.out.println("The length does not match.");
                    continue;
                }
            }
            itemList.add(item);
            System.out.println("Input next item if you want or enter to end:");
        }
        return itemList;
    }


    public static Map<String, String> inputStringStringMap(BufferedReader br, String askKey, String askValue) {
        Map<String, String> stringStringMap = new HashMap<>();
        while (true) {
            System.out.println(askKey);
            String key = Inputer.inputString(br);
            if (key.equals("")) break;
            System.out.println(askValue);
            String value = inputString(br);
            stringStringMap.put(key, value);
        }
        return stringStringMap;
    }

    public static String inputShare(BufferedReader br, String share) {
        float flo;
        String str;
        while (true) {
            System.out.println("Input the " + share + " if you need. Enter to ignore:");
            str = Inputer.inputString(br);
            if ("".equals(str)) return null;
            try {
                flo = Float.valueOf(str);
                if (flo > 1) {
                    System.out.println("A share should less than 1. Input again:");
                    continue;
                }
                flo = (float) NumberTools.roundDouble4(flo);
                return String.valueOf(flo);
            } catch (Exception e) {
                System.out.println("It isn't a number. Input again:");
            }
        }
    }

    public static String inputIntegerStr(BufferedReader br, String ask) {
        String str;
        int num = 0;
        while (true) {
            System.out.println(ask);
            try {
                str = br.readLine();
            } catch (IOException e) {
                System.out.println("BufferReader wrong.");
                return null;
            }
            if (!("".equals(str))) {
                try {
                    num = Integer.parseInt(str);
                    return String.valueOf(num);
                } catch (Exception e) {
                    System.out.println("It isn't a integer. Input again:");
                }
            } else return "";
        }
    }

    public static int inputInteger(BufferedReader br, String ask, int maximum) {
        String str;
        int num = 0;
        while (true) {
            System.out.println(ask);
            try {
                str = br.readLine();
            } catch (IOException e) {
                System.out.println("BufferReader wrong.");
                return 0;
            }

            if ("".equals(str)) return 0;

            try {
                num = Integer.parseInt(str);
                if (maximum > 0) {
                    if (num > maximum) {
                        System.out.println("It's bigger than " + maximum + ".");
                        continue;
                    }
                }
                return num;
            } catch (Exception e) {
                System.out.println("It isn't a integer. Input again:");
            }
        }
    }

    public static Integer inputIntegerWithNull(BufferedReader br, String ask, int maximum) {
        String str;
        Integer num = null;
        while (true) {
            System.out.println(ask);
            try {
                str = br.readLine();
            } catch (IOException e) {
                System.out.println("BufferReader wrong.");
                return null;
            }

            if ("".equals(str)) return null;

            try {
                num = Integer.parseInt(str);
                if (maximum > 0) {
                    if (num > maximum) {
                        System.out.println("It's bigger than " + maximum + ".");
                        continue;
                    }
                }
                return num;
            } catch (Exception e) {
                System.out.println("It isn't a integer. Input again:");
            }
        }
    }

    public static long inputLong(BufferedReader br, String ask) {
        String str;
        long num = 0;
        while (true) {
            System.out.println(ask);
            try {
                str = br.readLine();
            } catch (IOException e) {
                System.out.println("BufferReader wrong.");
                return -1;
            }
            if (!("".equals(str))) {
                try {
                    num = Long.parseLong(str);
                    return num;
                } catch (Exception e) {
                    System.out.println("It isn't a long integer. Input again:");
                }
            } else return 0;
        }
    }

    public static Long inputLongWithNull(BufferedReader br, String ask) {
        String str;
        Long num = null;
        while (true) {
            System.out.println(ask);
            try {
                str = br.readLine();
            } catch (IOException e) {
                System.out.println("BufferReader wrong.");
                return null;
            }
            if (!("".equals(str))) {
                try {
                    num = Long.parseLong(str);
                    return num;
                } catch (Exception e) {
                    System.out.println("It isn't a long integer. Input again:");
                }
            } else return null;
        }
    }

    public static char[] input32BytesKey(BufferedReader br, String ask) {
        System.out.println(ask);
        char[] symKey = new char[64];
        int num = 0;
        try {
            num = br.read(symKey);

            if (num != 64 || !Hex.isHexCharArray(symKey)) {
                System.out.println("The key should be 32 bytes in hex.");
                return null;
            }
            br.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return symKey;
    }

    public static byte[] inputSymKey32(BufferedReader br, String ask) {
        char[] symKey = input32BytesKey(br,ask);
        if(symKey==null)return null;
        return BytesTools.hexCharArrayToByteArray(symKey);
    }


    public static String inputMsg(BufferedReader br) {
        System.out.println("Input the plaintext:");
        String msg = null;
        try {
            msg = br.readLine();
        } catch (IOException e) {
            System.out.println("BufferedReader wrong.");
            return null;
        }
        return msg;
    }

    public static byte[] getPasswordBytes(BufferedReader br) {
        String ask = "Input the password:";
        char[] password = inputPassword(br, ask);
        byte[] passwordBytes = BytesTools.utf8CharArrayToByteArray(password);
        BytesTools.clearCharArray(password);
        return passwordBytes;
    }

    public static byte[] resetNewPassword(BufferedReader br) {
        while (true) {
            String ask = "Input a new password:";
            char[] password = inputPassword(br, ask);
            if (password == null) return null;
            ask = "Input the new password again:";
            char[] passwordAgain = inputPassword(br, ask);
            if (passwordAgain == null) return null;
            if (Arrays.equals(password, passwordAgain)) {
                byte[] passwordBytes = BytesTools.utf8CharArrayToByteArray(password);
                BytesTools.clearCharArray(password);
                return passwordBytes;
            }
            if (!Inputer.askIfYes(br, "Different inputs. Try again?")) return null;
        }
    }

    @NotNull
    public static byte[] inputAndCheckNewPassword(BufferedReader br) {
        byte[] passwordBytesNew;
        while (true) {
            System.out.print("Set the new password. ");
            passwordBytesNew = getPasswordBytes(br);
            System.out.print("Recheck the new password.");
            byte[] checkPasswordByte = getPasswordBytes(br);
            if (Arrays.equals(passwordBytesNew, checkPasswordByte)) break;
            System.out.println("They are not the same. Try again.");
        }
        return passwordBytesNew;
    }

    //    public static String inputStringMultiLine(BufferedReader br) {
//        StringBuilder input = new StringBuilder();
//
//        String line;
//
//        while (true) {
//            try {
//                line = br.readLine();
//            } catch (IOException e) {
//                System.out.println("BufferReader wrong.");
//                return null;
//            }
//            if("".equals(line)){
//                break;
//            }
//            input.append(line).append("\n");
//        }
//
//        // Access the complete input as a string
//        String text = input.toString();
//
//        if(text.endsWith("\n")) {
//            text = text.substring(0, input.length()-1);
//        }
//        return text;
//    }
    public static String inputStringMultiLine(BufferedReader br) {
        StringBuilder input = new StringBuilder();
        String line;

        while (true) {
            try {
                line = br.readLine();
            } catch (IOException e) {
                System.out.println("BufferReader wrong.");
                return null;
            }

            // Check for a special delimiter or condition
            if (line == null || line.trim().isEmpty()) {
                break;
            }

            input.append(line).append("\n");
        }

        // Remove the last newline character if present
        if (input.length() > 0 && input.charAt(input.length() - 1) == '\n') {
            input.deleteCharAt(input.length() - 1);
        }

        return input.toString();
    }

    public static boolean askIfYes(BufferedReader br, String ask) {
        System.out.println(ask+" 'y' to confirm. Other to ignore:");
        String input;
        try {
            input = br.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return "y".equals(input);
    }

    public static boolean confirmDefault(BufferedReader br, String name) {
        System.out.println("The only one is: "+ name+".\nEnter to choose it. 'n' or others to ignore it: ");
        String input;
        try {
            input = br.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return "".equals(input);
    }


    public static String[] promptAndSet(BufferedReader reader, String fieldName, String[] currentValues) throws IOException {
        String ask = "Enter " + fieldName + " (Press Enter to skip): ";
        String[] newValue = inputStringArray(reader, ask, 0);
        return newValue.length == 0 ? currentValues : newValue;
    }

    public static String promptAndSet(BufferedReader reader, String fieldName, String currentValue) throws IOException {
        System.out.print("Enter " + fieldName + " (Press Enter to skip): ");
        String newValue = reader.readLine();
        return newValue.isEmpty() ? currentValue : newValue;
    }

    public static long promptAndSet(BufferedReader reader, String fieldName, long currentValue) throws IOException {
        System.out.print("Enter " + fieldName + " (Press Enter to skip): ");
        String newValue = reader.readLine();
        return newValue.isEmpty() ? currentValue : Long.parseLong(newValue);
    }

    public static Boolean promptAndSet(BufferedReader reader, String fieldName, Boolean currentValue) throws IOException {
        if(currentValue!=null)System.out.print("Enter " + fieldName + "(true or false). It is '"+currentValue+ "' now. (Press Enter to keep it): ");
        else System.out.println("Enter " + fieldName + "(true or false). Enter to set as 'false':");
        String input = reader.readLine();
        if("".equals(input)) input = FALSE;
        return Boolean.parseBoolean(input);
    }
    public static long promptForLong(BufferedReader reader, String fieldName, long currentValue) throws IOException {
        System.out.print("Enter " + fieldName + " (Press Enter to skip): ");
        String newValue = reader.readLine();
        return newValue.isEmpty() ? currentValue : Long.parseLong(newValue);
    }

    public static String[] promptAndUpdate(BufferedReader reader, String fieldName, String[] currentValue) throws IOException {
        System.out.println(fieldName + " current value: " + Arrays.toString(currentValue));
        System.out.print("Do you want to update it? (y/n): ");

        if ("y".equalsIgnoreCase(reader.readLine())) {
            String ask = "Enter new values for " + fieldName + ": ";
            return inputStringArray(reader, ask, 0);
        }
        return currentValue;
    }

    public static String promptAndUpdate(BufferedReader reader, String fieldName, String currentValue) throws IOException {
        System.out.println("\nThe " + fieldName + " is :" + currentValue);
        System.out.print("Update it? Input new value to update. Enter to skip: ");
        String input = reader.readLine();
        if ("".equalsIgnoreCase(input))
            return currentValue;
        return input;
    }

    public static long promptAndUpdate(BufferedReader reader, String fieldName, long currentValue) throws IOException {
        System.out.println("The " + fieldName + " is :" + currentValue);
        System.out.print("Do you want to update it? Input a integer to update it. Enter to ignore: ");
        String input = reader.readLine();
        if("".equals(input))return currentValue;
        try{
            return Long.parseLong(input);
        }catch (Exception ignore){
            return currentValue;
        }
    }

    public static byte[] getPasswordStrFromEnvironment() {
        String password = System.getenv("PASSWORD");
        if (password != null) {
            // The password is available
            System.out.println("Password retrieved successfully.");
            return password.getBytes();
        } else {
            // The password is not set in the environment variables
            System.out.println("Password not found. \nYou can set it with '$ export PASSWORD='your_password_here''");
            return null;
        }
    }

    public static String[] inputFidArray(BufferedReader br, String ask, int len) {
        ArrayList<String> itemList = new ArrayList<String>();
        System.out.println(ask);
        while(true) {
            String item =Inputer.inputString(br);
            if(item.equals(""))break;
            if(!KeyTools.isValidFchAddr(item)){
                System.out.println("Invalid FID. Try again.");
                continue;
            }
            if(item.startsWith("3")){
                System.out.println("Multi-sign FID can not used to make new multi-sign FID. Try again.");
                continue;
            }
            if(len>0) {
                if(item.length()!=len) {
                    System.out.println("The length does not match.");
                    continue;
                }
            }
            itemList.add(item);
            System.out.println("Input next item if you want or enter to end:");
        }
        if(itemList.isEmpty())return new String [0];

        String[] items = itemList.toArray(new String[itemList.size()]);

        return items;
    }

    public static String inputPath(BufferedReader br,String ask) {
        String path;
        while(true) {
            System.out.println(ask);
            path = inputString(br);
            if(new File(path).exists())break;
            System.out.println("The path doesn't exist. Try again.");
        }
        return path;
    }

    public static <T> T chooseOne(T[] values, @Nullable String showStringFieldName, String ask, BufferedReader br) {
        if(values==null || values.length==0)return null;
        Field keyField = null;
        System.out.println(ask);
        Shower.printUnderline(10);
        try {
            if (showStringFieldName != null) {
                keyField = values[0].getClass().getDeclaredField(showStringFieldName);
                keyField.setAccessible(true);
            }
            String showing;
            for (int i = 0; i < values.length; i++) {
                if (showStringFieldName == null) {
                    showing = values[i].toString();
                } else showing = (String) keyField.get(values[i]);

                if (values.length == 1) {
                    if (confirmDefault(br, showing)) return values[0];
                    else return null;
                }

                System.out.println((i + 1) + " " + showing);
            }
        }catch (NoSuchFieldException | IllegalAccessException e) {
            System.out.println(e.getMessage());
            return null;
        }
        Shower.printUnderline(10);
        int choice = inputInteger(br,"Choose the number. 0 to skip:",values.length);
        if(choice==0)return null;
        return values[choice-1];
    }

    public static <T> T chooseOne(List<T> values, @Nullable String showStringFieldName, String ask, BufferedReader br) {
        if(values==null || values.isEmpty())return null;

        Field keyField = null;
        String showing;
        try {
            if(showStringFieldName!=null) {
                keyField = values.get(0).getClass().getDeclaredField(showStringFieldName);
                keyField.setAccessible(true);
            }
            System.out.println(ask);
            Shower.printUnderline(10);
            for(int i=0;i<values.size();i++){
                if(showStringFieldName==null) {
                    showing = values.get(i).toString();
                } else showing = (String) keyField.get(values.get(i));

                if(values.size()==1){
                    if(confirmDefault(br,showing))return values.get(0);
                    else return null;
                }
                System.out.println((i+1)+" "+ showing);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            System.out.println(e.getMessage());
            return null;
        }
        Shower.printUnderline(10);
        int choice = inputInteger(br,"Choose the number. 0 to skip:",values.size());
        if(choice==0)return null;
        return values.get(choice-1);
    }

    public static <T> String chooseOne(Map<String,T> stringTMap, boolean showValue, @Nullable String showStringFieldName, String ask, BufferedReader br) {
        if(stringTMap==null || stringTMap.isEmpty())return null;
        System.out.println(ask);
        Shower.printUnderline(10);
        List<String> keyList = stringTMap.keySet().stream().toList();
        Field keyField = null;
        String showing;
        try {
            if (showStringFieldName != null) {
                String key = (String) stringTMap.keySet().toArray()[0];
                keyField = stringTMap.get(key).getClass().getDeclaredField(showStringFieldName);
                keyField.setAccessible(true);
            }

            for (int i = 0; i < keyList.size(); i++) {
                String key = keyList.get(i);
                if (showValue) {
                    if (showStringFieldName == null)
                        showing = stringTMap.get(key).toString();
                    else
                        showing = (String) keyField.get(stringTMap.get(key));
                } else
                    showing = key;
                if(stringTMap.size()==1&&i==0){
                    if(confirmDefault(br,showing))return keyList.get(0);
                    else return null;
                }
                System.out.println((i + 1) + " " + showing);
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            System.out.println("Failed to get value from Class T when choosing one from stringMap.");
            return null;
        }

        Shower.printUnderline(10);
        int choice = inputInteger(br,"Choose the number. Enter to skip:",stringTMap.size());
        if(choice==0)return null;
        return keyList.get(choice-1);
    }

    public static boolean isGoodShare(Map<String, String> map) {
        float sum=0;
        for(String key:map.keySet()){
            float value = 0;
            try{
                value = Float.parseFloat(map.get(key));
                if(value<0)return false;
                sum+=value;
            }catch (Exception ignore){
                return false;
            }
        }
        return sum==1;
    }

    public static Long inputDate(BufferedReader br,String pattern,String ask)  {
        System.out.println(ask+"("+pattern+")");

        Long timestamp=null;
        try {
            String inputDate = br.readLine();
            if("".equals(inputDate))return null;
            timestamp = convertDateToTimestamp(inputDate, pattern);
        } catch (ParseException e) {
            System.out.println("Invalid date format. Please use '" + pattern + "'.");
        } catch (IOException e) {
            return null;
        }
        return timestamp;
    }

    public static long convertDateToTimestamp(String dateStr, String pattern) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
        Date date = dateFormat.parse(dateStr);
        return date.getTime();
    }
}
