package ui;

import core.crypto.KeyTools;
import data.fcData.FcEntity;
import data.feipData.AppOpData;
import utils.BytesUtils;
import utils.DateUtils;
import utils.Hex;
import utils.NumberUtils;
import utils.ObjectUtils;
import utils.StringUtils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import constants.Constants;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static constants.Values.FALSE;


public class Inputer {

    @SuppressWarnings("unused")
    public static char[] inputPassword(String ask) {
        Console console = System.console();
        if (console == null) {
            System.out.println("Couldn't get Console instance. Maybe you're running this from within an IDE, which doesn't support Console.");
            return null;
        }
        return console.readPassword(ask);
    }
//    public static char[] inputPassword(BufferedReader br, String ask) {
//        return inputPassword(ask);
//    }
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

    public static String inputString(BufferedReader br, String fieldName,String defaultValue) {
        System.out.println("Input the " + fieldName + ". "+"Enter to set the default '"+defaultValue+"':");
        String input = inputString(br);
        if("".equals(input))return defaultValue;
        return input;
    }

    public static Long inputLong(BufferedReader br, String fieldName,Long defaultValue) {
        String ask = "Input the " + fieldName + ". "+"Enter to set the default '"+defaultValue+"':";
        Long input = inputLongWithNull(br,ask);
        if(input==null)return defaultValue;
        return input;
    }

    public static String inputLongStr(BufferedReader br, String ask) {
        while (true) {
            System.out.println(ask);
            String inputStr;
            try {
                inputStr = br.readLine();
                if ("".equals(inputStr)) return null;
                // Verify it can be parsed as long
                Long.parseLong(inputStr);
                return inputStr;
            } catch (IOException e) {
                System.out.println("BufferedReader is wrong. Can't read.");
                return null;
            } catch (NumberFormatException e) {
                System.out.println("Input a valid number. Try again.");
            }
        }
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
            return NumberUtils.roundDouble4(share);
        }
    }

    public static Boolean inputBoolean(BufferedReader br, String fieldName,Boolean defaultValue) {
        String ask = "Input the " + fieldName + ". "+"Enter to set the default '"+defaultValue+"':";
        Boolean input = inputBoolean(br,ask);
        if(input==null)return defaultValue;
        return input;
    }
    public static Boolean inputBoolean(BufferedReader br, String ask) {

        while (true) {
            System.out.println(ask);
            String inputStr;
            boolean input;
            try {
                inputStr = br.readLine();
            } catch (IOException e) {
                System.out.println("br.readLine() wrong.");
                return null;
            }
            if ("".equals(inputStr)) return null;
            try {
                input = Boolean.parseBoolean(inputStr);
                return input;
            } catch (Exception e) {
                System.out.println("Input a number. Try again.");
            }
        }
    }


    public static Float inputFloat(BufferedReader br, String fieldName,Float defaultValue) {
        String ask = "Input the " + fieldName + ". "+"Enter to set the default '"+defaultValue+"':";
        Float input = inputFloat(br,ask);
        if(input==null)return defaultValue;
        return input;
    }
    public static Float inputFloat(BufferedReader br, String ask) {

        while (true) {
            System.out.println(ask);
            String inputStr;
            float input;
            try {
                inputStr = br.readLine();
            } catch (IOException e) {
                System.out.println("br.readLine() wrong.");
                return null;
            }
            if ("".equals(inputStr)) return null;
            try {
                input = Float.parseFloat(inputStr);
                return input;
            } catch (Exception e) {
                System.out.println("Input a number. Try again.");
            }
        }
    }

    public static Double inputDouble(BufferedReader br, String fieldName,Double defaultValue) {
        String ask = "Input the " + fieldName + ". "+"Enter to set the default '"+defaultValue+"':";
        Double input = inputDouble(br,ask);
        if(input==null)return defaultValue;
        return input;
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

    public static String[] inputMultiLineStringArray(BufferedReader br, String ask) {
        ArrayList<String> itemList = inputMultiLineStringList(br, ask);
        if (itemList.isEmpty()) return new String[0];
        return itemList.toArray(new String[0]);
    }

    @NotNull
    public static ArrayList<String> inputMultiLineStringList(BufferedReader br, String ask) {
        ArrayList<String> itemList = new ArrayList<>();
        System.out.println(ask);
        while (true) {
            String item = Inputer.inputStringMultiLine(br);
            if ("".equals(item)) break;
            itemList.add(item);
            System.out.println("Input next item if you want or enter to end:");
        }
        return itemList;
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
                flo = (float) NumberUtils.roundDouble4(flo);
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

    public static int inputInt(BufferedReader br, String ask, int maximum) {
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

    public static byte[] inputSymkey32(BufferedReader br, String ask) {
        char[] symKey = input32BytesKey(br,ask);
        if(symKey==null)return null;
        return BytesUtils.hexCharArrayToByteArray(symKey);
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
        byte[] passwordBytes = BytesUtils.utf8CharArrayToByteArray(password);
        BytesUtils.clearCharArray(password);
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
                byte[] passwordBytes = BytesUtils.utf8CharArrayToByteArray(password);
                BytesUtils.clearCharArray(password);
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
        System.out.println("Input the lines. Double enter to confirm:");
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
        if(currentValue!=null)System.out.print("Set " + fieldName + "(true or false). It is '"+currentValue+ "' now. (Press Enter to keep it): ");
        else System.out.println("Set " + fieldName + "(true or false). Enter to set as 'false':");
        String input = reader.readLine();
        if("".equals(input)) input = FALSE;
        return Boolean.parseBoolean(input);
    }
    public static long promptForLong(BufferedReader reader, String fieldName, long currentValue) throws IOException {
        System.out.print("Set " + fieldName + " (Enter to skip): ");
        String newValue = reader.readLine();
        return newValue.isEmpty() ? currentValue : Long.parseLong(newValue);
    }

    public static String[] promptAndUpdate(BufferedReader reader, String fieldName, String[] currentValue) throws IOException {
        System.out.println(fieldName + " current value: " + Arrays.toString(currentValue));
        System.out.print("Do you want to update it? (y/n): ");

        if ("y".equalsIgnoreCase(reader.readLine())) {
            String ask = "Set new values for " + fieldName + ": ";
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

    public static Boolean promptAndUpdate(BufferedReader reader, String fieldName, Boolean currentValue) throws IOException {
        System.out.println("The " + fieldName + " is :" + currentValue);
        System.out.print("Do you want to update it? Input a boolean to update it. Enter to ignore: ");
        String input = reader.readLine();
        if("".equals(input))return currentValue;
        try{
            return Boolean.parseBoolean(input);
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

    public static String inputFid(BufferedReader br, String ask) {
        String[] fids = inputFidArray(br, ask, 1);
        if(fids.length==0)return "";
        return fids[0];
    }

    public static String inputPubKey(BufferedReader br, String ask) {
        while(true) {
            String input = inputString(br, ask);
            if(KeyTools.isPubkey(input))return input;
            if(!askIfYes(br,"Illegal pubKey. Try again?"))return null;
        }
    }

    public static String[] inputFidArray(BufferedReader br, String ask, int len) {
        ArrayList<String> itemList = new ArrayList<String>();
        System.out.println(ask);
        while(true) {
            String item =Inputer.inputString(br);
            if(item.equals(""))break;
            if(!KeyTools.isGoodFid(item)){
                System.out.println("Invalid FID. Try again.");
                continue;
            }
            if(item.startsWith("3")){
                System.out.println("Multi-sign FID can not used to make new multi-sign FID. Try again.");
                continue;
            }
            itemList.add(item);
            if(len>0) {
                if(itemList.size()==len) {
                    break;
                }
            }
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
                keyField = findField(values[0].getClass(), showStringFieldName);
                if (keyField == null) {
                    System.out.println("Field '" + showStringFieldName + "' not found in class hierarchy");
                    return null;
                }
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
        }catch (IllegalAccessException e) {
            System.out.println(e.getMessage());
            return null;
        }
        Shower.printUnderline(10);
        int choice = inputInt(br,"Choose the number. 0 to skip:",values.length);
        if(choice==0)return null;
        return values[choice-1];
    }


    public static <T> T chooseOneFromList(List<T> values, @Nullable String showStringFieldName, String ask, BufferedReader br) {
        if(values==null || values.isEmpty())return null;

        Field keyField = null;
        String showing;
        try {
            if(showStringFieldName!=null) {
                keyField = findField(values.get(0).getClass(), showStringFieldName);
                if (keyField == null) {
                    System.out.println("Field '" + showStringFieldName + "' not found in class hierarchy");
                    return null;
                }
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
        } catch ( IllegalAccessException e) {
            System.out.println(e.getMessage());
            return null;
        }
        Shower.printUnderline(10);
        int choice = inputInt(br,"Choose the number. 0 to skip:",values.size());
        if(choice==0)return null;
        return values.get(choice-1);
    }



    public static <T> String chooseOneKeyFromMap(Map<String,T> stringTMap, boolean showValue, @Nullable String showStringFieldName, String ask, BufferedReader br) {
        return (String) chooseOneFromMap(stringTMap,showValue,false,showStringFieldName,ask,br);
    }
    public static <T> Object chooseOneValueFromMap(Map<String,T> stringTMap, boolean showValue, @Nullable String showStringFieldName, String ask, BufferedReader br) {
        return chooseOneFromMap(stringTMap,showValue,true,showStringFieldName,ask,br);
    }

    private static <T> Object chooseOneFromMap(Map<String,T> stringTMap, boolean showValue,boolean returnValue, @Nullable String showStringFieldName, String ask, BufferedReader br) {
        if(stringTMap==null || stringTMap.isEmpty())return null;
        System.out.println(ask);
        Shower.printUnderline(10);
        List<String> keyList = stringTMap.keySet().stream().toList();

        Field keyField = null;
        String showing;
        try {
            if (showStringFieldName != null) {
                String key = (String) stringTMap.keySet().toArray()[0];
                keyField = findField(stringTMap.get(key).getClass(), showStringFieldName);
                if (keyField == null) {
                    System.out.println("Field '" + showStringFieldName + "' not found in class hierarchy");
                    return null;
                }
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
        } catch ( IllegalAccessException e) {
            System.out.println("Failed to get value from Class T when choosing one from stringMap.");
            return null;
        }

        Shower.printUnderline(10);
        int choice = inputInt(br,"Choose the number. Enter to skip:",stringTMap.size());
        if(choice==0)return null;
        String key = keyList.get(choice - 1);
        if(returnValue)return stringTMap.get(key);
        return key;
    }

public static <K, V> Object chooseOneFromMapArray(Map<K, V> map, boolean showValue, boolean returnValue, String ask, BufferedReader br) {
    if (map == null || map.isEmpty()) return null;
    System.out.println(ask);
    Shower.printUnderline(10);
    List<K> keyList = new ArrayList<>(map.keySet());

    for (int i = 0; i < keyList.size(); i++) {
        K key = keyList.get(i);
        String showing = showValue ? formatValue(map.get(key)) : key.toString();
        
        if (map.size() == 1 && i == 0) {
            if (confirmDefault(br, showing)) return returnValue ? map.get(key) : key;
            else return null;
        }
        System.out.println((i + 1) + " " + showing);
    }

    Shower.printUnderline(10);
    int choice = inputInt(br, "Choose the number. Enter to skip:", map.size());
    if (choice == 0) return null;
    K key = keyList.get(choice - 1);
    return returnValue ? map.get(key) : key;
}

private static String formatTimestamp(Object value) {
    if (value == null) return "null";
    if (!(value instanceof Long)) return value.toString();
    
    long timestamp = (Long) value;
    if (timestamp > Constants.TIMESTAMP_2000 && timestamp < Constants.TIMESTAMP_2100) { // Valid range: Jan 1, 2000 to ~2100
        return DateUtils.longToTime(timestamp, DateUtils.SHORT_FORMAT);
    }
    return String.valueOf(timestamp);
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

    public static <T> T createFromInput(
        BufferedReader reader,
        Class<T> tClass) throws IOException, ReflectiveOperationException {
        return createFromUserInput(reader, tClass, null, null);
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = clazz;
        
        while (currentClass != null) {
            fields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
            currentClass = currentClass.getSuperclass();
        }
        
        return fields;
    }

    public static <T> T createFromUserInput(BufferedReader reader, Class<T> tClass, String opName, Map<String, String[]> opFieldsMap) throws IOException, ReflectiveOperationException {
        if(tClass.equals(Object.class))return null;

        // Check for static getInputFieldDefaultValueMap method
        Map<String, Object> inputFieldDefaultValueMap = null;
        try {
            Method getDefaultValueMapMethod = tClass.getDeclaredMethod(FcEntity.METHOD_GET_INPUT_FIELD_DEFAULT_VALUE_MAP);
            inputFieldDefaultValueMap = (Map<String, Object>) getDefaultValueMapMethod.invoke(null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Method not found or not static, continue without default values
        }

        System.out.println("Enter " + tClass.getSimpleName() + " Data:");

        if (tClass.isArray()) {
            return (T) createArrayFromUserInput(reader, tClass.getComponentType(), tClass.getSimpleName(), null);
        }

        T instance = tClass.getDeclaredConstructor().newInstance();

        // Only use optionValueMap if opName is not null
        if (opName != null && opFieldsMap != null && !opFieldsMap.isEmpty()) {
            // Handle classes with operation-specific fields
            Field opField = findField(tClass, opName);
            if (opField == null) {
                System.out.println("Field '" + opName + "' not found in class hierarchy");
                return null;
            }
            opField.setAccessible(true);

            // Get the Op enum from the correct class
            Class<?> enumClass = null;
            try {
                // First try to get the inner enum class
                enumClass = Class.forName(tClass.getName() + "$Op");
            } catch (ClassNotFoundException e) {
                // If inner enum not found, try to get the enum class from the same package
                try {
                    String packageName = tClass.getPackage().getName();
                    String className = tClass.getSimpleName();
                    enumClass = Class.forName(packageName + "." + className + "Op");
                } catch (ClassNotFoundException ex) {
                    System.out.println("Could not find enum class for " + tClass.getName());
                    return null;
                }
            }
            
            if (enumClass == null || !enumClass.isEnum()) {
                System.out.println("No valid enum class found for " + tClass.getName());
                return null;
            }
            
            Object[] operations = enumClass.getEnumConstants();
            
            // Use Inputer.chooseOne with the correct enum type
            Object selectedOp = chooseOne(operations, null, "Choose operation", reader);
            String opValue = selectedOp.toString().toLowerCase();
            opField.set(instance, opValue);

            String[] fieldsToPrompt = opFieldsMap.get(opValue);
            if (fieldsToPrompt == null) {
                throw new IllegalArgumentException("Invalid operation: " + opValue);
            }

            for (String fieldName : fieldsToPrompt) {
                Field field = findField(tClass, fieldName);
                if (field == null) {
                    System.out.println("Field '" + fieldName + "' not found in class hierarchy");
                    return null;
                }
                field.setAccessible(true);
                Class<?> fieldType = field.getType();

                Object value = null;
                if (fieldType.isArray()) {
                    value = createArrayFromUserInput(reader, fieldType.getComponentType(), fieldName, null);
                } else if (fieldType.equals(List.class)) {
                    // Handle List type by reusing array logic
                    Class<?> genericType = getGenericType(field);
                    if (genericType != null) {
                        System.out.print("Input " + fieldName + " (comma-separated values): ");
                        String input = reader.readLine().trim();
                        if (!input.isEmpty()) {
                            String[] values = input.split(",");
                            List<Object> list = new ArrayList<>();
                            for (String value1 : values) {
                                try {
                                    Object convertedValue = ObjectUtils.convertToType(value1.trim(), genericType);
                                    if (convertedValue != null) {
                                        list.add(convertedValue);
                                    }
                                } catch (Exception e) {
                                    System.out.println("Error converting value: " + value1);
                                }
                            }
                            if (!list.isEmpty()) {
                                field.set(instance, list);
                            }
                        }
                    } else {
                        // If generic type is unknown, treat as String list
                        System.out.print("Input " + fieldName + " (comma-separated strings): ");
                        String input = reader.readLine().trim();
                        if (!input.isEmpty()) {
                            String[] values = input.split(",");
                            List<String> list = new ArrayList<>();
                            for (String value1 : values) {
                                String trimmedValue = value1.trim();
                                if (!trimmedValue.isEmpty()) {
                                    list.add(trimmedValue);
                                }
                            }
                            if (!list.isEmpty()) {
                                field.set(instance, list);
                            }
                        }
                    }
                } else if (fieldType.equals(Map.class)) {
                    // Handle Map type by reusing array logic for keys and values
                    Class<?>[] genericTypes = getMapGenericTypes(field);
                    Class<?> keyType = genericTypes[0] != null ? genericTypes[0] : String.class;
                    Class<?> valueType = genericTypes[1] != null ? genericTypes[1] : String.class;
                    
                    System.out.println("Enter keys for " + fieldName + ":");
                    Object keyArray = createArrayFromUserInput(reader, keyType, "keys", null);
                    if (keyArray != null) {
                        System.out.println("Enter values for " + fieldName + ":");
                        Object valueArray = createArrayFromUserInput(reader, valueType, "values", null);
                        if (valueArray != null) {
                            Map<Object, Object> map = new HashMap<>();
                            Object[] keys = (Object[]) keyArray;
                            Object[] values = (Object[]) valueArray;
                            int length = Math.min(keys.length, values.length);
                            for (int i = 0; i < length; i++) {
                                map.put(keys[i], values[i]);
                            }
                            field.set(instance, map);
                        }
                    }
                } else if (ObjectUtils.isComplexType(fieldType)) {
                    value = createFromUserInput(reader, fieldType, opName, opFieldsMap);
                } else {
                    // Use default value from map if available
                    Object defaultValue = inputFieldDefaultValueMap != null ? inputFieldDefaultValueMap.get(fieldName) : null;
                    value = promptInput(reader, fieldName, fieldType, defaultValue);
                }
                if (value != null) field.set(instance, value);
            }
        } else {
            // For classes without operation-specific fields or when opName is null, prompt for all fields
            List<Field> allFields = getAllFields(tClass);
            for (Field field : allFields) {
                // Skip static and transient fields
                int modifiers = field.getModifiers();
                if (java.lang.reflect.Modifier.isStatic(modifiers) || 
                    java.lang.reflect.Modifier.isTransient(modifiers) ||
                    java.lang.reflect.Modifier.isFinal(modifiers)) {
                    continue;
                }

                field.setAccessible(true);
                String fieldName = field.getName();
                if(inputFieldDefaultValueMap != null && !inputFieldDefaultValueMap.containsKey(fieldName)) continue;
                Class<?> fieldType = field.getType();

                Object value = null;
                if (fieldType.isArray()) {
                    value = createArrayFromUserInput(reader, fieldType.getComponentType(), fieldName, null);
                } else if (fieldType.equals(List.class)) {
                    // Handle List type by reusing array logic
                    Class<?> genericType = getGenericType(field);
                    if (genericType != null) {
                        System.out.print("Input " + fieldName + " (comma-separated values): ");
                        String input = reader.readLine().trim();
                        if (!input.isEmpty()) {
                            String[] values = input.split(",");
                            List<Object> list = new ArrayList<>();
                            for (String value1 : values) {
                                try {
                                    Object convertedValue = ObjectUtils.convertToType(value1.trim(), genericType);
                                    if (convertedValue != null) {
                                        list.add(convertedValue);
                                    }
                                } catch (Exception e) {
                                    System.out.println("Error converting value: " + value1);
                                }
                            }
                            if (!list.isEmpty()) {
                                field.set(instance, list);
                            }
                        }
                    } else {
                        // If generic type is unknown, treat as String list
                        System.out.print("Input " + fieldName + " (comma-separated strings): ");
                        String input = reader.readLine().trim();
                        if (!input.isEmpty()) {
                            String[] values = input.split(",");
                            List<String> list = new ArrayList<>();
                            for (String value1 : values) {
                                String trimmedValue = value1.trim();
                                if (!trimmedValue.isEmpty()) {
                                    list.add(trimmedValue);
                                }
                            }
                            if (!list.isEmpty()) {
                                field.set(instance, list);
                            }
                        }
                    }
                } else if (fieldType.equals(Map.class)) {
                    // Handle Map type by reusing array logic for keys and values
                    Class<?>[] genericTypes = getMapGenericTypes(field);
                    Class<?> keyType = genericTypes[0] != null ? genericTypes[0] : String.class;
                    Class<?> valueType = genericTypes[1] != null ? genericTypes[1] : String.class;
                    
                    System.out.println("Enter keys for " + fieldName + ":");
                    Object keyArray = createArrayFromUserInput(reader, keyType, "keys", null);
                    if (keyArray != null) {
                        System.out.println("Enter values for " + fieldName + ":");
                        Object valueArray = createArrayFromUserInput(reader, valueType, "values", null);
                        if (valueArray != null) {
                            Map<Object, Object> map = new HashMap<>();
                            Object[] keys = (Object[]) keyArray;
                            Object[] values = (Object[]) valueArray;
                            int length = Math.min(keys.length, values.length);
                            for (int i = 0; i < length; i++) {
                                map.put(keys[i], values[i]);
                            }
                            field.set(instance, map);
                        }
                    }
                } else if (ObjectUtils.isComplexType(fieldType)) {
                    value = createFromUserInput(reader, fieldType, opName, opFieldsMap);
                } else {
                    // Use default value from map if available
                    Object defaultValue = inputFieldDefaultValueMap != null ? inputFieldDefaultValueMap.get(fieldName) : null;
                    value = promptInput(reader, fieldName, fieldType, defaultValue);
                }
                if (value != null) field.set(instance, value);
            }
        }

        return instance;
    }

    private static Object createArrayFromUserInput(BufferedReader reader, Class<?> componentType, String fieldName, Map<String, String[]> opFields) throws IOException, ReflectiveOperationException {
        if(componentType.equals(Object.class))return null;
        while (true) {
            try {
                if (ObjectUtils.isComplexType(componentType) ) {
                    System.out.print(fieldName + " (" + componentType.getSimpleName() + "[]. Enter 'y' to input, or any other key to skip): ");
                    String response = reader.readLine().trim().toLowerCase();
                    if (!response.equals("y")) {
                        return null;  // Skip this field
                    }

                    System.out.print("Enter number of elements for " + fieldName + ": ");
                    int size = Integer.parseInt(reader.readLine().trim());
                    Object array = Array.newInstance(componentType, size);

                    for (int i = 0; i < size; i++) {
                        System.out.println("Enter details for " + fieldName + " element " + i + ":");
                        Object value = createFromUserInput(reader, componentType, "op", opFields);
                        Array.set(array, i, value);
                    }

                    return array;
                } else {
                    System.out.print("Input "+fieldName + " (" + componentType.getSimpleName() + "[] separated by commas, or press Enter for null): ");
                    String input = reader.readLine().trim();

                    if (input.isEmpty()) {
                        return null;  // Return null if no input
                    }

                    String[] elements = input.split(",");
                    Object array = Array.newInstance(componentType, elements.length);

                    for (int i = 0; i < elements.length; i++) {
                        String element = elements[i].trim();
                        Object value = ObjectUtils.convertToType(element, componentType);
                        Array.set(array, i, value);
                    }

                    return array;
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid input: " + e.getMessage() + ". Please try again.");
            }
        }
    }

    private static <T> T promptInput(BufferedReader reader, String prompt, Class<T> tClass, Object defaultValue) throws IOException {
        // Skip if the type is Object.class since we can't determine its structure
        if (tClass == Object.class) {
            return null;
        }

        String promptText = "Input " + prompt + " (" + tClass.getSimpleName() + ")";
        if (defaultValue != null&& !"".equals(defaultValue)) {
            promptText += " [default: " + defaultValue + "]";
        }
        promptText += " or press Enter to skip: ";
        while(true){
            
            System.out.print(promptText);
            String input = reader.readLine().trim();

            if (input.isEmpty()) {
                return defaultValue != null ? (T) defaultValue : null;  // Return default value if available, otherwise skip
            }
            try {
                if (tClass == Integer.class || tClass == int.class) {
                    return (T) Integer.valueOf(input);
                } else if (tClass == Long.class || tClass == long.class) {
                    return (T) Long.valueOf(input);
                } else if (tClass == Double.class || tClass == double.class) {
                    return (T) Double.valueOf(input);
                } else if (tClass == Float.class || tClass == float.class) {
                    return (T) Float.valueOf(input);
                } else if (tClass == Boolean.class || tClass == boolean.class) {
                    if(!input.toLowerCase().equals("true")&&!input.equals("false"))throw new IllegalArgumentException();
                    return (T) Boolean.valueOf(input);
                } else if (tClass == String.class) {
                    return (T) input;
                } else if (tClass.isEnum()) {
                    return (T) Enum.valueOf((Class<Enum>) tClass, input.toUpperCase());
                } else {
                    System.out.println("Unsupported type: " + tClass.getSimpleName());
                    return null;
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid input for " + tClass.getSimpleName() + ". try again.");
                continue;
            }
        }
    }

    // Keep the original promptInput method for backward compatibility
    private static <T> T promptInput(BufferedReader reader, String prompt, Class<T> tClass) throws IOException {
        return promptInput(reader, prompt, tClass, null);
    }

    public static <T, E extends Enum<E>> T updateFromUserInput(
            BufferedReader reader,
            T instance,
            String opName,
            Class<E> enumClass,
            Map<String, String[]> opFieldsMap) throws IOException, ReflectiveOperationException {

        Class<?> tClass = instance.getClass();

        if (opFieldsMap != null && !opFieldsMap.isEmpty()) {
            Field opField = findField(tClass, opName);
            if (opField == null) {
                System.out.println("Field '" + opName + "' not found in class hierarchy");
                return null;
            }
            opField.setAccessible(true);
            String currentOp = (String) opField.get(instance);

            System.out.println("Current operation: " + currentOp);
            System.out.print("Update operation? (y/n): ");
            if (reader.readLine().trim().toLowerCase().equals("y")) {
                E[] operations = enumClass.getEnumConstants();
                E selectedOp = chooseOne(operations, null, "Choose new operation", reader);
                String newOpValue = selectedOp.toString().toLowerCase();

                if (!newOpValue.equals(currentOp)) {
                    System.out.println("Warning: Changing operation from '" + currentOp + "' to '" + newOpValue + "'");
                    System.out.print("Do you want to clear fields that are not used in the new operation? (y/n): ");
                    if (reader.readLine().trim().toLowerCase().equals("y")) {
                        for (Field field : tClass.getDeclaredFields()) {
                            field.setAccessible(true);
                            String fieldName = field.getName();

                            if (fieldName.equals(opName) ||
                                java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                                continue;
                            }

                            String[] newOpFields = opFieldsMap.get(newOpValue);
                            if (newOpFields == null || !Arrays.asList(newOpFields).contains(fieldName)) {
                                if (field.getType().isPrimitive()) {
                                    // Set default values for primitive types
                                    if (field.getType() == boolean.class) {
                                        field.setBoolean(instance, false);
                                    } else if (field.getType() == byte.class) {
                                        field.setByte(instance, (byte) 0);
                                    } else if (field.getType() == short.class) {
                                        field.setShort(instance, (short) 0);
                                    } else if (field.getType() == int.class) {
                                        field.setInt(instance, 0);
                                    } else if (field.getType() == long.class) {
                                        field.setLong(instance, 0L);
                                    } else if (field.getType() == float.class) {
                                        field.setFloat(instance, 0.0f);
                                    } else if (field.getType() == double.class) {
                                        field.setDouble(instance, 0.0);
                                    } else if (field.getType() == char.class) {
                                        field.setChar(instance, '\u0000');
                                    }
                                } else {
                                    field.set(instance, null);
                                }
                            }
                        }
                        System.out.println("Fields not used in new operation have been cleared.");
                    } else {
                        System.out.println("Fields retained. Note: Some fields may not be valid for the new operation.");
                    }

                    opField.set(instance, newOpValue);
                    currentOp = newOpValue;
                }
            }

            String[] fieldsToPrompt = opFieldsMap.get(currentOp);
            if (fieldsToPrompt == null) {
                throw new IllegalArgumentException("Invalid operation: " + currentOp);
            }

            for (String fieldName : fieldsToPrompt) {
                updateField(reader, instance, tClass, fieldName, opFieldsMap);
            }
        } else {
            for (Field field : tClass.getDeclaredFields()) {
                // Skip static and transient fields
                int modifiers = field.getModifiers();
                if (java.lang.reflect.Modifier.isStatic(modifiers) || 
                    java.lang.reflect.Modifier.isTransient(modifiers)||
                    java.lang.reflect.Modifier.isFinal(modifiers)) {
                    continue;
                }

                field.setAccessible(true);
                updateField(reader, instance, tClass, field.getName(), opFieldsMap);
            }
        }
        return instance;
    }

    public static <T> void updateFromInput(BufferedReader reader, T instance, Class<?> tClass) throws IOException, ReflectiveOperationException {
        updateFromUserInput(reader, instance, null, null, null);
    }

    public static <T> void updateField(BufferedReader reader, T instance, Class<?> tClass, String fieldName, Map<String, String[]> opFieldsMap) throws IOException, ReflectiveOperationException {
        Field field = findField(tClass, fieldName);
        if (field == null) {
            System.out.println("Field '" + fieldName + "' not found in class hierarchy");
            return;
        }
        field.setAccessible(true);
        
        // Skip if the type is Object.class since we can't determine its structure
        if (field.getType() == Object.class) {
            return;
        }
        
        Object currentValue = field.get(instance);
        Class<?> fieldType = field.getType();
        Object newValue;

        if (fieldType.isArray()) {
            System.out.println("Current " + fieldName + ": " + formatValue(currentValue));
            System.out.print("Update this array? (y/Enter to skip): ");
            String input = reader.readLine().trim();
            if (input.equalsIgnoreCase("y")) {
                newValue = createArrayFromUserInput(reader, fieldType.getComponentType(), fieldName, null);
                if (newValue != null) field.set(instance, newValue);
            }
        } else if (fieldType.equals(List.class)) {
            // Handle List type by reusing array logic
            System.out.println("Current " + fieldName + ": " + formatValue(currentValue));
            Class<?> genericType = getGenericType(field);
            if (genericType != null) {
                Object array = createArrayFromUserInput(reader, genericType, fieldName, null);
                if (array != null) {
                    field.set(instance, Arrays.asList((Object[]) array));
                }
            } else {
                // If generic type is unknown, treat as String array
                Object array = createArrayFromUserInput(reader, String.class, fieldName, null);
                if (array != null) {
                    field.set(instance, Arrays.asList((String[]) array));
                }
            }
        } else if (fieldType.equals(Map.class)) {
            // Handle Map type by reusing array logic for keys and values
            System.out.println("Current " + fieldName + ": " + formatValue(currentValue));
            Class<?>[] genericTypes = getMapGenericTypes(field);
            Class<?> keyType = genericTypes[0] != null ? genericTypes[0] : String.class;
            Class<?> valueType = genericTypes[1] != null ? genericTypes[1] : String.class;
            
            System.out.println("Enter keys for " + fieldName + ":");
            Object keyArray = createArrayFromUserInput(reader, keyType, "keys", null);
            if (keyArray != null) {
                System.out.println("Enter values for " + fieldName + ":");
                Object valueArray = createArrayFromUserInput(reader, valueType, "values", null);
                if (valueArray != null) {
                    Map<Object, Object> map = new HashMap<>();
                    Object[] keys = (Object[]) keyArray;
                    Object[] values = (Object[]) valueArray;
                    int length = Math.min(keys.length, values.length);
                    for (int i = 0; i < length; i++) {
                        map.put(keys[i], values[i]);
                    }
                    field.set(instance, map);
                }
            }
        } else if (ObjectUtils.isComplexType(fieldType)) {
            System.out.println("Current " + fieldName + ": " + formatValue(currentValue));
            System.out.print("Update this complex object? (y/Enter to skip): ");
            String input = reader.readLine().trim();
            if (input.equalsIgnoreCase("y")) {
                if (currentValue == null) {
                    newValue = createFromUserInput(reader, fieldType, "op", opFieldsMap);
                    if (newValue != null) field.set(instance, newValue);
                } else {
                    updateFromUserInput(reader, currentValue, "op", AppOpData.Op.class, opFieldsMap);
                }
            }
        } else {
            System.out.println("Current " + fieldName + ": " + formatValue(currentValue));
            newValue = promptInput(reader, fieldName, fieldType, null);
            if (newValue != null) field.set(instance, newValue);
        }
    }

    private static String formatValue(Object value) {
        if (value == null) return "null";
        if (value.getClass().isArray()) {
            if (value.getClass().getComponentType().isPrimitive()) {
                // Handle primitive arrays
                return Arrays.toString((Object[]) value);
            }
            // Handle object arrays including nested arrays
            return Arrays.deepToString((Object[]) value);
        }
        return value.toString();
    }

    private static Class<?> getGenericType(Field field) {
        try {
            java.lang.reflect.ParameterizedType paramType = 
                (java.lang.reflect.ParameterizedType) field.getGenericType();
            return (Class<?>) paramType.getActualTypeArguments()[0];
        } catch (Exception e) {
            return null;
        }
    }

    private static Class<?>[] getMapGenericTypes(Field field) {
        try {
            java.lang.reflect.ParameterizedType paramType = 
                (java.lang.reflect.ParameterizedType) field.getGenericType();
            return new Class<?>[] {
                (Class<?>) paramType.getActualTypeArguments()[0],
                (Class<?>) paramType.getActualTypeArguments()[1]
            };
        } catch (Exception e) {
            return new Class<?>[] { null, null };
        }
    }

    public static <T, E extends Enum<E>> T createOrUpdateFromUserInput(
            BufferedReader reader,
            T instance,
            Class<T> tClass,
            String opName,
            Class<E> opEnumClass,
            Map<String, String[]> opFieldsMap) throws IOException, ReflectiveOperationException {
        
        if (instance == null) {
            // Create new instance
            return createFromUserInput(reader, tClass, opName, opFieldsMap);
        } else {
            // Update existing instance
            updateFromUserInput(reader, instance, opName, opEnumClass, opFieldsMap);
            return instance;
        }
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                Field field = currentClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        // If field not found in class hierarchy, try public fields as last resort
        try {
            return clazz.getField(fieldName);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    public static <T> T chooseOneFromListShowingMultiField(
        List<T> values, 
        @Nullable List<String> showStringFieldNameList,
        List<Integer> widthList,
        String ask, 
        BufferedReader br) {
        
        if (values == null || values.isEmpty()) return null;
        if (showStringFieldNameList == null || widthList == null || 
            showStringFieldNameList.size() != widthList.size()) {
            System.out.println("Invalid field names or width list configuration");
            return null;
        }

        List<Field> fields = new ArrayList<>();
        try {
            // Get all required fields
            if (showStringFieldNameList != null) {
                for (String fieldName : showStringFieldNameList) {
                    Field field = findField(values.get(0).getClass(), fieldName);
                    if (field == null) {
                        System.out.println("Field '" + fieldName + "' not found in class hierarchy");
                        return null;
                    }
                    fields.add(field);
                }
            }

            System.out.println(ask);
            Shower.printUnderline(10);

            // Handle single item case
            if (values.size() == 1) {
                StringBuilder showing = new StringBuilder();
                for (int i = 0; i < fields.size(); i++) {
                    String fieldValue = String.valueOf(fields.get(i).get(values.get(0)));
                    showing.append(String.format("%-" + widthList.get(i) + "s", 
                        fieldValue.length() <= widthList.get(i) ? fieldValue : StringUtils.omitMiddle(fieldValue, widthList.get(i))));
                }
                if (confirmDefault(br, showing.toString())) return values.get(0);
                else return null;
            }

            // Show all items with multiple fields
            for (int i = 0; i < values.size(); i++) {
                StringBuilder line = new StringBuilder();
                line.append(String.format("%-4s", (i + 1) + " "));
                
                for (int j = 0; j < fields.size(); j++) {
                    String fieldValue = String.valueOf(fields.get(j).get(values.get(i)));
                    line.append(String.format("%-" + widthList.get(j) + "s", 
                        fieldValue.length() <= widthList.get(j) ? fieldValue : StringUtils.omitMiddle(fieldValue, widthList.get(j))));
                }
                System.out.println(line);
            }

        } catch (IllegalAccessException e) {
            System.out.println(e.getMessage());
            return null;
        }

        Shower.printUnderline(10);
        int choice = inputInt(br, "Choose the number. 0 to skip:", values.size());
        if (choice == 0) return null;
        return values.get(choice - 1);
    }

    public static <T> List<T> chooseMultiFromListShowingMultiFieldBatch(
        List<T> values, 
        @Nullable List<String> showStringFieldNameList,
        List<Integer> widthList,
        String ask,
        Integer startWith,
        BufferedReader br,int batchSize) {
        if(startWith==null)startWith=0;
        if(batchSize<=0) return new ArrayList<>();

        List<T> selectedItems = new ArrayList<>();
        int totalSize = values.size();
        int batchCount = (int) Math.ceil((double) totalSize / batchSize);
        for(int i=0;i<batchCount;i++){
            List<T> batchItems = chooseMultiFromListShowingMultiField(values, showStringFieldNameList, widthList, ask, startWith, br);
            startWith += batchSize;
            selectedItems.addAll(batchItems);
        }
        return selectedItems;
    }

    public static <T> List<T> chooseMultiFromListShowingMultiField(
        List<T> values, 
        @Nullable List<String> showStringFieldNameList,
        List<Integer> widthList,
        String ask,
        Integer startWith,
        BufferedReader br) {
        
        if (values == null || values.isEmpty()) return new ArrayList<>();
        if (showStringFieldNameList == null || widthList == null || 
            showStringFieldNameList.size() != widthList.size()) {
            System.out.println("Invalid field names or width list configuration");
            return new ArrayList<>();
        }

        List<Field> fields = new ArrayList<>();
        try {
            // Get all required fields
            if (showStringFieldNameList != null) {
                for (String fieldName : showStringFieldNameList) {
                    Field field = findField(values.get(0).getClass(), fieldName);
                    if (field == null) {
                        System.out.println("Field '" + fieldName + "' not found in class hierarchy");
                        return new ArrayList<>();
                    }
                    fields.add(field);
                }
            }

            System.out.println(ask);

            if(values.size()>1) {
                Shower.printUnderline(10);

                // Show header row with field names
                StringBuilder header = new StringBuilder();
                header.append(String.format("%-4s", "No."));
                for (int i = 0; i < showStringFieldNameList.size(); i++) {
                    header.append(String.format("%-" + widthList.get(i) + "s", showStringFieldNameList.get(i)));
                    header.append("  ");
                }
                System.out.println(header);
            }
            // Handle single item case
            if (values.size() == 1) {
                StringBuilder showing = new StringBuilder();
                for (int i = 0; i < fields.size(); i++) {
                    String fieldValue = String.valueOf(fields.get(i).get(values.get(0)));
                    showing.append(String.format("%-" + widthList.get(i) + "s", 
                        fieldValue.length() <= widthList.get(i) ? fieldValue : StringUtils.omitMiddle(fieldValue, widthList.get(i))));
                }
                if (confirmDefault(br, showing.toString())) return List.of(values.get(0));
                else return new ArrayList<>();
            }

            // Show all items with multiple fields
            for (int i = 0; i < values.size(); i++) {
                StringBuilder line = new StringBuilder();
                line.append(String.format("%-4s", (i + startWith) + " "));
                
                for (int j = 0; j < fields.size(); j++) {
                    String fieldValue = String.valueOf(fields.get(j).get(values.get(i)));
                    line.append(String.format("%-" + widthList.get(j) + "s", 
                        fieldValue.length() <= widthList.get(j) ? fieldValue : StringUtils.omitMiddle(fieldValue, widthList.get(j))));
                    line.append("  ");
                }
                System.out.println(line);
            }

            Shower.printUnderline(10);
            System.out.println("Input numbers separated by commas (e.g., 1,3,5). 'a' for all. Enter to skip.");
            String input = inputString(br);
            
            if (input == null || input.trim().isEmpty()) {
                return new ArrayList<>();
            }

            if("a".equals(input)) return values;

            List<T> selectedItems = new ArrayList<>();
            String[] choices = input.split(",");
            
            for (String choice : choices) {
                try {
                    int index = Integer.parseInt(choice.trim()) - startWith;
                    if (index >= 0 && index < values.size()) {
                        selectedItems.add(values.get(index));
                    } else {
                        System.out.println("Warning: Invalid number " + choice + " ignored");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Warning: Invalid input '" + choice + "' ignored");
                }
            }

            return selectedItems;

        } catch (IllegalAccessException e) {
            System.out.println(e.getMessage());
            return new ArrayList<>();
        }
    }

    public static Integer inputInteger(BufferedReader br, String ask, Integer minimum, Integer maximum) {
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
                if (minimum != null && num < minimum) {
                    System.out.println("It's smaller than " + minimum + ".");
                    continue;
                }
                if (maximum != null && maximum > minimum && num > maximum) {
                    System.out.println("It's bigger than " + maximum + ".");
                    continue;
                }
                return num;
            } catch (Exception e) {
                System.out.println("It isn't an integer. Input again:");
            }
        }
    }

    public static List<Integer> chooseMulti(BufferedReader br, int min, int max) {
        List<Integer> choices = new ArrayList<>();
        int choice = 0;

        while (true) {
            System.out.println("\nInput the numbers to choose. Separate by comma. 'a' to choose all. Enter to ignore:");
            try {
                String input = br.readLine();
                if("".equals(input)) break;
                if("a".equals(input)){
                    choices.add(-1);
                    break;
                }
                // Clean up the input by removing spaces
                String[] inputs = input.replaceAll("\s+", "").split(",");
                boolean validInput = true;
                for (String input1 : inputs) {
                    try {
                        choice = Integer.parseInt(input1);
                        if (choice <= max && choice > min) {
                            choices.add(choice);
                        } else {
                            System.out.println("\nInput an integer within: " + (min+1) + "~" + max + ". Try again.");
                            choices.clear();
                            validInput = false;
                            break;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("\nInvalid number format. Try again.");
                        choices.clear();
                        validInput = false;
                        break;
                    }
                }
                if (validInput) break;
            } catch (IOException e) {
                System.out.println("\nError reading input. Try again.");
            }
        }
        return choices;
    }

    public static List<String> chooseMultiFromList(List<String> values, Integer startWith, Integer batchSize, String ask, BufferedReader br) {
        if(startWith==null)startWith=0;
        if(batchSize<=0) return new ArrayList<>();
        List<String> selectedItems = new ArrayList<>();
        int totalSize = values.size();
        int batchCount = (int) Math.ceil((double) totalSize / batchSize);
        Iterator<String> iterator = values.iterator();
        for(int i=0;i<batchCount;i++){
            List<String> subValues = new ArrayList<>();
            while(iterator.hasNext() && subValues.size()<batchSize){
                subValues.add(iterator.next());
            }
            System.out.println();
            System.out.println(ask);
            Shower.printUnderline(10);
            Shower.showStringList(subValues, startWith);
            Shower.printUnderline(10);
            List<Integer> choices = chooseMulti(br, startWith, startWith+subValues.size());
            if(choices.isEmpty()) return selectedItems;
            for(Integer choice:choices){
                if(choice==-1) return values;
                selectedItems.add(subValues.get(choice-1));
            }
            startWith += batchSize;
        }
        return selectedItems;
    }

    public static <T> List<T> chooseMultiFromListGeneric(List<T> values, Integer startWith, Integer batchSize, String ask, BufferedReader br) {
        if(startWith==null)startWith=0;
        if(batchSize<=0) return new ArrayList<>();
        List<T> selectedItems = new ArrayList<>();
        int totalSize = values.size();
        int batchCount = (int) Math.ceil((double) totalSize / batchSize);
        int fieldWidth = 13;
        
        // Check if T is a native type
        boolean isNativeType = false;
        boolean isLongType = false;
        if (!values.isEmpty()) {
            T firstValue = values.get(0);
            isNativeType = firstValue == null || 
                          firstValue instanceof String ||
                          firstValue instanceof Number ||
                          firstValue instanceof Boolean ||
                          firstValue instanceof Character;
            isLongType = firstValue instanceof Long;
        }
        
        Iterator<T> iterator = values.iterator();
        for(int i=0;i<batchCount;i++){
            List<T> subValues = new ArrayList<>();
            while(iterator.hasNext() && subValues.size()<batchSize){
                subValues.add(iterator.next());
            }
            System.out.println();
            System.out.println(ask);
            Shower.printUnderline(10);
            
            // Show each element on a separate line
            for(int j = 0; j < subValues.size(); j++) {
                T value = subValues.get(j);
                String displayValue;
                
                if (isNativeType) {
                    if (isLongType && value != null) {
                        displayValue = formatTimestamp(value);
                    } else {
                        displayValue = value != null ? value.toString() : "N/A";
                        displayValue = displayValue.length() > fieldWidth ? StringUtils.omitMiddle(displayValue, fieldWidth) : displayValue;
                    }
                } else {
                    // Handle non-native type by getting all field values
                    displayValue = makeObjInLine(fieldWidth, value, null);
                }
                
                System.out.println(String.format("%d %s", (startWith + j + 1), displayValue));
            }

            Shower.printUnderline(10);
            List<Integer> choices = chooseMulti(br, startWith, startWith+subValues.size());
            
            if(choices.isEmpty()) return selectedItems;
            
            for(Integer choice:choices){
                if(choice==-1) return values;
                selectedItems.add(subValues.get(choice-startWith-1));
            }
            
            startWith += batchSize;
        }
        
        return selectedItems;
    }

    @NotNull
    public static <T> String makeObjInLine(Integer fieldWidth, T value, List<Object> ignoreValues) {
        String displayValue;
        StringBuilder fieldValues = new StringBuilder();
        if (value != null) {
            Field[] fields = value.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                try {
                    Object fieldValue = field.get(value);
                    if(ignoreValues!=null && ignoreValues.contains(fieldValue)) continue;
                    String fieldStr;
                    
                    // Handle timestamp fields (long/Long type)
                    if (fieldValue != null && (field.getType() == long.class || field.getType() == Long.class)) {
                        fieldStr = formatTimestamp(fieldValue);
                    } else {
                        fieldStr = fieldValue != null ? fieldValue.toString() : "N/A";
                    }
                    
                    // Only apply omitMiddle if fieldWidth is not null
                    if (fieldWidth != null && fieldStr.length() > fieldWidth) {
                        fieldStr = StringUtils.omitMiddle(fieldStr, fieldWidth);
                    }
                    
                    fieldValues.append(fieldStr).append(" ");
                } catch (IllegalAccessException e) {
                    fieldValues.append("ERROR ");
                }
            }
        }
        displayValue = fieldValues.length() > 0 ? fieldValues.toString().trim() : "null";
        return displayValue;
    }

    public static <T> Map<String,T> chooseMultiFromMapGeneric(
            Map<String,T> map, 
            String shownValueField,
            Integer startWith, 
            Integer batchSize, 
            String ask, 
            BufferedReader br) {
        if(startWith==null) startWith=0;
        if(batchSize<=0) return new HashMap<>();
        Integer fieldWidth = 13;
        
        Map<String,T> selectedItems = new HashMap<>();
        List<String> keys = new ArrayList<>(map.keySet());
        if(keys.isEmpty()) return selectedItems;
        
        int totalSize = keys.size();
        int batchCount = (int) Math.ceil((double) totalSize / batchSize);
        
        // Check if T is a native type or complex object
        T firstValue = map.get(keys.get(0));
        boolean isNativeType = firstValue == null || 
                              firstValue instanceof String ||
                              firstValue instanceof Number ||
                              firstValue instanceof Boolean ||
                              firstValue instanceof Character;
        
        // Check if T is specifically Long type
        boolean isLongType = firstValue instanceof Long;
        
        Field valueField = null;
        if (!isNativeType && shownValueField != null) {
            valueField = findField(firstValue.getClass(), shownValueField);
            if (valueField == null) {
                System.out.println("Warning: Field '" + shownValueField + "' not found in value class. Using toString() instead.");
            } else {
                valueField.setAccessible(true);
            }
        }

        Iterator<String> iterator = keys.iterator();
        for(int i=0; i<batchCount; i++) {
            List<String> batchKeys = new ArrayList<>();
            while(iterator.hasNext() && batchKeys.size()<batchSize) {
                batchKeys.add(iterator.next());
            }
            
            System.out.println();
            System.out.println(ask);
            Shower.printUnderline(10);
            
            // Show each entry on a separate line
            for(int j = 0; j < batchKeys.size(); j++) {
                String key = batchKeys.get(j);
                T value = map.get(key);
                String valueDisplay;
                
                if (isNativeType) {
                    if (isLongType && value != null) {
                        valueDisplay = formatTimestamp(value);
                    } else {
                        valueDisplay = value != null ? value.toString() : "N/A";
                    }
                } else {
                    try {
                        Object fieldValue = null;
                        if (valueField != null) {
                            fieldValue = valueField.get(value);
                            if (fieldValue == null) continue;
                        } 
                        valueDisplay = makeObjInLine(fieldWidth, fieldValue,List.of(key));
                    } catch (IllegalAccessException e) {
                        valueDisplay = "Error accessing field";
                    }
                }
                
                // Truncate both key and value if too long
                String displayKey = key.length() > fieldWidth ? StringUtils.omitMiddle(key, fieldWidth) : key;
                valueDisplay = valueDisplay.length() > fieldWidth ? StringUtils.omitMiddle(valueDisplay, fieldWidth) : valueDisplay;
                System.out.printf("%d %s %s%n", (startWith + j + 1), displayKey, valueDisplay);
            }
            
            Shower.printUnderline(10);
            List<Integer> choices = chooseMulti(br, startWith, startWith+batchKeys.size());
            
            if(choices.isEmpty()) return selectedItems;
            
            for(Integer choice:choices) {
                if(choice==-1) return map;
                String selectedKey = batchKeys.get(choice-startWith-1);
                selectedItems.put(selectedKey, map.get(selectedKey));
            }
            
            startWith += batchSize;
        }
        
        return selectedItems;
    }

    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    
        // Test chooseMultiFromListGeneric with different types
        System.out.println("\n=== Testing chooseMultiFromListGeneric ===");
        
        // Test with Strings
        List<String> stringList = Arrays.asList("Apple", "Banana", "Cherry", "Date", "Elderberry");
        System.out.println("\nSelecting from String list:");
        List<String> selectedStrings = chooseMultiFromListGeneric(stringList, 0, 3, "Choose fruits:", br);
        System.out.println("Selected items: " + selectedStrings);
    
        // Test with Integers
        List<Integer> numberList = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8);
        System.out.println("\nSelecting from Integer list:");
        List<Integer> selectedNumbers = chooseMultiFromListGeneric(numberList, 0, 4, "Choose numbers:", br);
        System.out.println("Selected items: " + selectedNumbers);
    
        // Test chooseMultiFromMapGeneric with different types
        System.out.println("\n=== Testing chooseMultiFromMapGeneric ===");
    
        // Test with String values
        Map<String, String> stringMap = new HashMap<>();
        stringMap.put("key1", "Value One");
        stringMap.put("key2", "Value Two");
        stringMap.put("key3", "Value Three");
        System.out.println("\nSelecting from String Map:");
        Map<String, String> selectedStringMap = chooseMultiFromMapGeneric(
            stringMap, null, 0, 2, "Choose string values:", br);
        System.out.println("Selected items: " + selectedStringMap);
    
        // Test with Long timestamps
        Map<String, Long> timestampMap = new HashMap<>();
        timestampMap.put("Today", System.currentTimeMillis());
        timestampMap.put("Yesterday", System.currentTimeMillis() - DateUtils.dayToLong(1));
        timestampMap.put("Last Week", System.currentTimeMillis() - DateUtils.dayToLong(7));
        timestampMap.put("Next Week", System.currentTimeMillis() + DateUtils.dayToLong(7));
        System.out.println("\nSelecting from Timestamp Map:");
        Map<String, Long> selectedTimestamps = chooseMultiFromMapGeneric(
            timestampMap, null, 0, 2, "Choose dates:", br);
        System.out.println("Selected items: " + selectedTimestamps);
    
        // Test with a custom class
        Map<String, TestPerson> personMap = new HashMap<>();
        personMap.put("p1", new TestPerson("John Doe", 25));
        personMap.put("p2", new TestPerson("Jane Smith", 30));
        personMap.put("p3", new TestPerson("Bob Johnson", 35));
        System.out.println("\nSelecting from Person Map (showing names):");
        Map<String, TestPerson> selectedPeople = chooseMultiFromMapGeneric(
            personMap, "name", 0, 2, "Choose people:", br);
        System.out.println("Selected items: " + selectedPeople);
    }
    
    // Test class for demonstration
    private static class TestPerson {
        private String name;
        private int age;
    
        public TestPerson(String name, int age) {
            this.name = name;
            this.age = age;
        }
    
        @Override
        public String toString() {
            return "TestPerson{name='" + name + "', age=" + age + '}';
        }
    }
}


