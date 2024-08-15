package javaTools;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateTools {
    public static String longToTime(long timestamp) {
        Date date = new Date(timestamp);
        return getNiceDate(date);
    }

    @NotNull
    public static String getNiceDate(Date date) {
        // Create a SimpleDateFormat with the desired format
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        // Format the date as a string
        return sdf.format(date);
    }
}
