package javaTools;

import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateTools {
    public static String longToTime(long timestamp,String format) {
        Date date = new Date(timestamp);
        return getNiceDate(date,format);
    }
    public static long dateToLong(String dateString, String format) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(format);
        try {
            Date date = dateFormat.parse(dateString);
            return date.getTime();
        } catch (ParseException e) {
            System.err.println("Error parsing date: " + e.getMessage());
            return -1; // Return -1 or handle the error as needed
        }
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
