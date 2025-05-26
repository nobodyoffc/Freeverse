package utils;

import ui.Shower;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public class NumberUtils {


    public static boolean isInt(String numberStr) {
        try{
            Integer.parseInt(numberStr);
        }catch (Exception ignore){
            return false;
        }
        return true;
    }

    public static boolean isBoolean(String boolStr, boolean strictly) {
        if(strictly) {
            return boolStr.equals("true") || boolStr.equals("false");
        }

        try{
            Boolean.parseBoolean(boolStr);
        }catch (Exception ignore){
            return false;
        }
        return true;
    }
    public static double roundDouble8(double raw){
        BigDecimal bd = new BigDecimal(raw);
        bd = bd.setScale(8, RoundingMode.HALF_UP); // Choose the rounding mode as needed
        return bd.doubleValue();
    }

    public static double roundDouble2(double raw){
        BigDecimal bd = new BigDecimal(raw);
        bd = bd.setScale(2, RoundingMode.HALF_UP); // Choose the rounding mode as needed
        return bd.doubleValue();
    }

    public static double roundDouble16(double raw){
        BigDecimal bd = new BigDecimal(raw);
        bd = bd.setScale(16, RoundingMode.HALF_UP); // Choose the rounding mode as needed
        return bd.doubleValue();
    }

    public static int getDecimalPlaces(double number) {
        if (number == (long) number) {
            // The number has no significant decimal places
            return 0;
        } else {
            String numberAsString = Double.toString(Math.abs(number));
            // Remove the integer part and the decimal point
            String decimalPart = numberAsString.substring(numberAsString.indexOf('.') + 1);
            // Remove trailing zeros
            String significantDecimalPart = decimalPart.replaceAll("0*$", "");
            return significantDecimalPart.length();
        }
    }

    public static double roundDouble4(double raw){
        BigDecimal bd = new BigDecimal(raw);
        bd = bd.setScale(4, RoundingMode.FLOOR); // Choose the rounding mode as needed
        return bd.doubleValue();
    }

    public static double roundDouble(double number,int decimal, RoundingMode mode){
        BigDecimal bd = new BigDecimal(number);
        bd = bd.setScale(decimal, mode); // Choose the rounding mode as needed
        return bd.doubleValue();
    }

    public static String numberToPlainString(String number,String deci){
        number.replaceAll(",","");

        BigDecimal bigDecimal = new BigDecimal(number);

        // Get a NumberFormat instance for formatting numbers with commas
        NumberFormat formatter = NumberFormat.getInstance(Locale.US);

        // Set the maximum number of fraction digits to avoid unnecessary decimal places
        // This is important if your BigDecimal value has non-zero fraction part
        if(deci!=null)formatter.setMaximumFractionDigits(Integer.parseInt(deci));

        // Format the BigDecimal number with commas
        return formatter.format(bigDecimal);
    }

    public static String formatNumberValue(Number value, int width) {
        // First try with maximum precision
        String str = String.valueOf(value);
        if(str.contains(".")){
            if (str.length() <= width) return str;

            int decimalIndex = str.indexOf('.');

            if(decimalIndex < width)return str.substring(0, width);

            str = str.substring(0, decimalIndex);
        }

        if (str.length() <= width) return str;

        long longvalue = Long.parseLong(str);
        // Try K format
        if (longvalue >= 1000) {
            str = String.valueOf(longvalue / 1000) + "K";
            if (str.length() <= width) return str;
        }

        // Try M format
        if (longvalue >= 1000000) {
            str = String.valueOf(longvalue / 1000000) + "M";
            if (str.length() <= width) return str;
        }

        // If still too long, use omitMiddle
        return Shower.omitMiddle(String.valueOf(value), width);
    }

    public static long doubleToLong(double amount, int decimal) {
        BigDecimal coins = BigDecimal.valueOf(amount);
        BigDecimal satoshis = coins.multiply(BigDecimal.valueOf(Math.pow(10, decimal))); // Convert BTC to Satoshis
        return satoshis.setScale(0, RoundingMode.HALF_UP).longValueExact(); // Set scale to 0 (no fractional part) and use HALF_UP rounding
    }
}
