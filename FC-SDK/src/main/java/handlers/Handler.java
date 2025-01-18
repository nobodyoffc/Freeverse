package handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

/**
 * Base handler class providing common functionality for other handlers
 */
public abstract class Handler {
    
    /**
     * Gets a list of items from a NavigableMap with optional pagination and ordering
     *
     * @param map The source NavigableMap to get items from
     * @param sinceId Start ID (inclusive if fromInclusive is true)
     * @param toId End ID (inclusive if toInclusive is true)  
     * @param size Maximum number of items to return
     * @param fromInclusive Whether to include sinceId in results
     * @param toInclusive Whether to include toId in results
     * @param fromEnd Whether to get items from end of range
     * @return List of items matching criteria
     */
    protected <T> List<T> getUnitList(NavigableMap<String, T> map, String sinceId, String toId,
            Integer size, boolean fromInclusive, boolean toInclusive, boolean fromEnd) {
        NavigableMap<String, T> subMap;
        if (sinceId != null && toId != null) {
            subMap = map.subMap(sinceId, fromInclusive, toId, toInclusive);
        } else if (sinceId != null) {
            // If no toId, get only the requested size of entries after sinceId
            subMap = map.tailMap(sinceId, fromInclusive)
                    .entrySet().stream()
                    .limit(size != null ? size : Long.MAX_VALUE)
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        ConcurrentSkipListMap::new
                    ));
        } else if (toId != null) {
            // If no sinceId, get only the requested size of entries before toId
            NavigableMap<String, T> baseMap = map.headMap(toId, toInclusive);
            if (fromEnd) {
                baseMap = baseMap.descendingMap();
            }
            subMap = baseMap.entrySet().stream()
                    .limit(size != null ? size : Long.MAX_VALUE)
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        ConcurrentSkipListMap::new
                    ));
        } else {
            // If both ids are null, get only the requested size
            NavigableMap<String, T> baseMap = fromEnd ? map.descendingMap() : map;
            subMap = baseMap.entrySet().stream()
                    .limit(size != null ? size : Long.MAX_VALUE)
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        ConcurrentSkipListMap::new
                    ));
        }

        NavigableMap<String, T> resultMap = fromEnd ? subMap.descendingMap() : subMap;
        return new ArrayList<>(resultMap.values());
    }
} 