package tools;

import tools.DateTools;
import org.junit.jupiter.api.Test;

import java.util.Date;

public class FchTools {
    public static final long genesisTime = 1577836802L *1000;
    public static Date heightToDate(long height){
        return new Date(genesisTime+height*60*1000);
    }

    public static String heightToShortDate(long height){
        return DateTools.longToTime(genesisTime+height*60*1000,DateTools.SHORT_FORMAT);
    }

    public static String heightToLongDate(long height){
        return DateTools.longToTime(genesisTime+height*60*1000,DateTools.LONG_FORMAT);
    }

    public static String heightToNiceDate(long height){
        return DateTools.getNiceDate(new Date(genesisTime+height*60*1000));
    }

    public static long dateToHeight(Date date){
        long time = date.getTime();
        return (time-genesisTime)/(60*1000);
    }

    public long timeToHeight(long time){
        return (time-genesisTime)/(60*1000);
    }
    @Test
    public void test(){
        long height = 2500000;
        Date date = heightToDate(height);
        System.out.println(DateTools.getNiceDate(date));

        System.out.println(DateTools.longToTime(genesisTime+height*60*1000,"yyyy/MM/dd HH:mm:ss"));
        System.out.println(dateToHeight(date));
        System.out.println(timeToHeight(date.getTime()));
    }


}
