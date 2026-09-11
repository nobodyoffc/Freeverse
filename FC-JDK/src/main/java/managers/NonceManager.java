package managers;
import static constants.Strings.WINDOW_TIME;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import config.Settings;
import data.fcData.FcEntity;

public class NonceManager extends Manager<FcEntity> {
    private final Map<Integer, Long> nonceMap;
    private final Long windowTime;
    private final ReadWriteLock lock;
    private static final Random random = new Random();

    public NonceManager(Settings settings) {
        super(ManagerType.NONCE);
        this.windowTime = ((Number) settings.getSettingMap().get(WINDOW_TIME)).longValue();
        this.nonceMap = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public static  boolean isBadTime(long userTime, long windowTime){
        if(windowTime==0)return false;
        long currentTime = System.currentTimeMillis();
        return Math.abs(currentTime - userTime) > windowTime;
    }

    public void putNonce(Integer nonce) {
        if (nonce == null) return;
        lock.writeLock().lock();
        try {
            nonceMap.put(nonce, System.currentTimeMillis());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Past this many remembered nonces, expired ones are swept on the next use. */
    private static final int SWEEP_THRESHOLD = 100_000;

    /**
     * Accept `nonce` once within the time window: record it and return true, or return false if
     * it was already used. Check and record are one atomic step, so two concurrent requests
     * carrying the same nonce cannot both pass.
     *
     * Nothing used to record a nonce -- putNonce had no caller -- so isBadNonce always found an
     * empty map and every signed request could be replayed for as long as its time was accepted.
     */
    public boolean useNonce(Integer nonce) {
        if (nonce == null) return false;
        long now = System.currentTimeMillis();
        boolean[] fresh = {false};
        lock.readLock().lock();
        try {
            nonceMap.compute(nonce, (k, birth) -> {
                if (birth != null && (windowTime <= 0 || now - birth <= windowTime)) return birth;
                fresh[0] = true;
                return now;
            });
        } finally {
            lock.readLock().unlock();
        }
        if (windowTime > 0 && nonceMap.size() > SWEEP_THRESHOLD) removeTimeOutNonce();
        return fresh[0];
    }

    public void removeTimeOutNonce() {
        long currentTime = System.currentTimeMillis();
        lock.writeLock().lock();
        try {
            nonceMap.entrySet().removeIf(entry -> 
                currentTime - entry.getValue() > windowTime);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isBadNonce(Integer nonce) {
        if (nonce == null) return true;
        
        lock.readLock().lock();
        try {
            Long birthTime = nonceMap.get(nonce);
            if (birthTime == null) return false;

            long currentTime = System.currentTimeMillis();
            if (currentTime - birthTime > windowTime) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    nonceMap.remove(nonce);
                    return false;
                } finally {
                    lock.writeLock().unlock();
                    lock.readLock().lock();
                }
            }
            return true;
        } finally {
            lock.readLock().unlock();
        }
    }

    public static Integer newNonce() {
        return random.nextInt();
    }
}