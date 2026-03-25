package utils;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Freeverse Date — a human-readable time representation based on block height,
 * as defined in FVEP4.
 * <p>
 * Format: {@code Y.D.H.M} where:
 * <ul>
 *   <li>Y = year (1 year = 400 days = 576,000 blocks)</li>
 *   <li>D = day within year (0–399, 1 day = 1,440 blocks)</li>
 *   <li>H = hour within day (0–23, 1 hour = 60 blocks)</li>
 *   <li>M = minute within hour (0–59, 1 minute = 1 block)</li>
 * </ul>
 * Epoch: block 0 = 2020-01-01 00:00:02 UTC (Freecash genesis block).
 */
public class FcDate {

    public static final int DAYS_PER_YEAR = 400;
    public static final int HOURS_PER_DAY = 24;
    public static final int MINUTES_PER_HOUR = 60;
    public static final int BLOCKS_PER_MINUTE = 1;

    public static final int BLOCKS_PER_HOUR = MINUTES_PER_HOUR;
    public static final int BLOCKS_PER_DAY = HOURS_PER_DAY * BLOCKS_PER_HOUR;
    public static final int BLOCKS_PER_YEAR = DAYS_PER_YEAR * BLOCKS_PER_DAY;

    public static final long GENESIS_UNIX_SECONDS = 1577836802L;

    private long year;
    private long day;
    private long hour;
    private long minute;

    public FcDate(long year, long day, long hour, long minute) {
        this.year = year;
        this.day = day;
        this.hour = hour;
        this.minute = minute;
    }

    @NotNull
    public static FcDate fromHeight(long height) {
        if (height < 0) {
            throw new IllegalArgumentException("Block height must not be negative");
        }
        long year = height / BLOCKS_PER_YEAR;
        long rest = height % BLOCKS_PER_YEAR;

        long day = rest / BLOCKS_PER_DAY;
        rest = rest % BLOCKS_PER_DAY;

        long hour = rest / BLOCKS_PER_HOUR;
        long minute = rest % BLOCKS_PER_HOUR;

        return new FcDate(year, day, hour, minute);
    }

    @NotNull
    public static FcDate parse(String fcDateString) {
        if (fcDateString == null || fcDateString.isEmpty()) {
            throw new IllegalArgumentException("FcDate string must not be null or empty");
        }
        String[] parts = fcDateString.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException(
                    "Invalid FcDate format. Expected Y.D.H.M, got: " + fcDateString);
        }
        long year = Long.parseLong(parts[0]);
        long day = Long.parseLong(parts[1]);
        long hour = Long.parseLong(parts[2]);
        long minute = Long.parseLong(parts[3]);
        return new FcDate(year, day, hour, minute);
    }

    public long toHeight() {
        return year * BLOCKS_PER_YEAR + day * BLOCKS_PER_DAY + hour * BLOCKS_PER_HOUR + minute;
    }

    public static long toHeight(String fcDateString) {
        return parse(fcDateString).toHeight();
    }

    /**
     * Approximate conversion to Unix timestamp in seconds.
     * Actual block times vary; this uses the target interval of 60 seconds per block.
     */
    public long toApproxUnixSeconds() {
        return GENESIS_UNIX_SECONDS + toHeight() * 60;
    }

    /**
     * Create an FcDate from an approximate Unix timestamp (seconds).
     * The result is approximate because actual block intervals vary.
     */
    @NotNull
    public static FcDate fromApproxUnixSeconds(long unixSeconds) {
        long height = (unixSeconds - GENESIS_UNIX_SECONDS) / 60;
        return fromHeight(height);
    }

    @Override
    public String toString() {
        return year + "." + day + "." + hour + "." + minute;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FcDate fcDate = (FcDate) o;
        return year == fcDate.year && day == fcDate.day
                && hour == fcDate.hour && minute == fcDate.minute;
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, day, hour, minute);
    }

    public long getYear() { return year; }
    public void setYear(long year) { this.year = year; }

    public long getDay() { return day; }
    public void setDay(long day) { this.day = day; }

    public long getHour() { return hour; }
    public void setHour(long hour) { this.hour = hour; }

    public long getMinute() { return minute; }
    public void setMinute(long minute) { this.minute = minute; }
}
