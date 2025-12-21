package utils;

import org.jetbrains.annotations.NotNull;

public class FcDate {
    private long year;
    private long day;
    private long hour;

    private long minute;

    public FcDate(long year, long minute, long day, long hour) {
        this.year = year;
        this.minute = minute;
        this.day = day;
        this.hour = hour;
    }

    public long getYear() {
        return year;
    }

    public void setYear(long year) {
        this.year = year;
    }

    public long getMinute() {
        return minute;
    }

    public void setMinute(long minute) {
        this.minute = minute;
    }

    public long getDay() {
        return day;
    }

    public void setDay(long day) {
        this.day = day;
    }

    public long getHour() {
        return hour;
    }

    public void setHour(long hour) {
        this.hour = hour;
    }

    @NotNull
    public static FcDate fromHeight(long height) {
        long year = height / (400 * 24 * 60);
        long rest = height % (400 * 24 * 60);

        long day = rest / (24 * 60);
        rest = rest % (24 * 60);

        long minute = rest / 60;
        rest = rest % 60;
        return new FcDate(year, rest, day, minute);
    }

    @Override
    public String toString() {
        return year + "." + day + "." + hour + "." + minute;
    }

    public long toHeight() {
        return year * 400 * 24 * 60 + day * 24 * 60 + hour * 60 + minute;
    }

    public static long toHeight(String fcDateString) {
        String[] parts = fcDateString.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid FcDate string format. Expected format: year.day.hour.minute");
        }
        long year = Long.parseLong(parts[0]);
        long day = Long.parseLong(parts[1]);
        long hour = Long.parseLong(parts[2]);
        long minute = Long.parseLong(parts[3]);
        return year * 400 * 24 * 60 + day * 24 * 60 + hour * 60 + minute;
    }
}
