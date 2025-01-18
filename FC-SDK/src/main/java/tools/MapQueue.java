package tools;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MapQueue<K, V> {
    private final ConcurrentHashMap<K, V> map;
    private final ConcurrentLinkedQueue<K> queue;
    private final int maxSize;

    public MapQueue(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("Queue size must be greater than 0");
        }
        this.maxSize = maxSize;
        this.map = new ConcurrentHashMap<>();
        this.queue = new ConcurrentLinkedQueue<>();
    }

    public void put(K key, V value) {
        if (map.containsKey(key)) {
            queue.remove(key);
        }
        map.put(key, value);
        queue.offer(key);
        
        while (map.size() > maxSize) {
            K oldestKey = queue.poll();
            if (oldestKey != null) {
                map.remove(oldestKey);
            }
        }
    }

    public Map<K, V> putAll(Map<K, V> map) {
        Map<K, V> pulledMap = new HashMap<>();
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if(map.size()>maxSize){
            Map.Entry<K, V> peekedEntry = peek();
                if(peekedEntry!=null)
                    pulledMap.put(peekedEntry.getKey(), peekedEntry.getValue());
            }
            put(entry.getKey(), entry.getValue());
        }
        return pulledMap;
    }

    /**
     * Gets the oldest key and its associated value without removing them
     * @return Map.Entry containing the oldest key-value pair, or null if the queue is empty
     */
    public Map.Entry<K, V> peek() {
        K oldestKey = queue.peek();
        if (oldestKey == null) {
            return null;
        }
        V value = map.get(oldestKey);
        if (value == null) {
            return null;
        }
        return new Map.Entry<K, V>() {
            @Override
            public K getKey() {
                return oldestKey;
            }

            @Override
            public V getValue() {
                return value;
            }

            @Override
            public V setValue(V value) {
                throw new UnsupportedOperationException();
            }
        };
    }

    public V get(K key) {
        return map.get(key);
    }

    public V remove(K key) {
        V value = map.remove(key);
        if (value != null) {
            queue.remove(key);
        }
        return value;
    }

    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
    }

    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    public ConcurrentHashMap<K, V> getMap() {
        return map;
    }

    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Removes the key and returns its associated value
     * @param key the key to remove
     * @return the value associated with the key, or null if the key was not present
     */
    public V removeAndGet(K key) {
        V value = map.remove(key);
        if (value != null) {
            queue.remove(key);
        }
        return value;
    }
}
