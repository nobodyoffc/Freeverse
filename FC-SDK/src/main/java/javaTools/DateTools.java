package javaTools;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateTools {
    public static String longToTime(long timestamp,String pattern) {
        Date date = new Date(timestamp);
        return getNiceDate(date,pattern);
    }
    public static long dayToLong(long days) {
        // 1 day = 24 hours = 24 * 60 minutes = 24 * 60 * 60 seconds = 24 * 60 * 60 * 1000 milliseconds
        return days * 24L * 60 * 60 * 1000;
    }
    @NotNull
    public static String getNiceDate(Date date) {
        String pattern ="yyyy/MM/dd HH:mm:ss";
        return getNiceDate(date, pattern);
    }

    @NotNull
    public static String getNiceDate(Date date, String pattern) {
        // Create a SimpleDateFormat with the desired format
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        // Format the date as a string
        return sdf.format(date);
    }
}
