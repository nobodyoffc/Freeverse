package clients.redisClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import crypto.KeyTools;
import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
public class RedisTools {

    public static long readHashLong(Jedis jedis, String key, String filed) {
        long var = 0;
        String varStr;
        try {
            varStr = jedis.hget(key, filed);
        } catch (Exception e) {
            varStr = null;
        }
        if (varStr != null) {
            var = Long.parseLong(varStr);
        }
        return var;
    }

    public static void setNumber(String key, String field, BufferedReader br, Jedis jedis) throws IOException {
        while (true) {
            String value = jedis.hget(key, field);
            System.out.println("The " + field + " is " + value + ". Input to set it. Enter to ignore it:");
            String input = br.readLine();
            if ("".equals(input)) return;
            try {
                Double.parseDouble(input);
            } catch (Exception e) {
                System.out.println("Input a number please. Try again. Enter to ignore.");
                continue;
            }
            jedis.hset(key, field, input);
            return;
        }

    }
    public static double readHashDouble(Jedis jedis, String key, String filed) {

        double var = 0;
        String varStr;
        try {
            varStr = jedis.hget(key, filed);
        } catch (Exception e) {
            varStr = null;
        }
        if (varStr != null) {
            var = Double.parseDouble(varStr);
        }
        return var;
    }

    public static long readLong(String key) {

        long var = 0;
        String varStr;
        try (Jedis jedis = new Jedis()) {
            varStr = jedis.get(key);
        } catch (Exception e) {
            varStr = null;
        }
        if (varStr != null) {
            var = Long.parseLong(varStr);
        }
        return var;
    }

    public static void setFid(String key, String field, BufferedReader br, Jedis jedis) throws IOException {
        while (true) {
            String value = jedis.hget(key, field);
            System.out.println("The " + field + " is " + value + ". Input to set it. Enter to ignore it:");
            String input = br.readLine();
            if ("".equals(input)) return;
            if (KeyTools.isValidFchAddr(input)) {
                jedis.hset(key, field, input);
                return;
            } else {
                System.out.println("It's not a FCH address.Input again. Enter to ignore");
            }
        }
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static <T> void writeToRedis(Object obj, String key, Jedis jedis, Class<T> tClass) {
    Map<String, String> settingMap = new HashMap<>();

    Class<?> currentClass = tClass;
    while (currentClass != null) {
        for (Field field : currentClass.getDeclaredFields()) {
            field.setAccessible(true);

            if (Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            try {
                if (field.getType() == org.slf4j.Logger.class) {
                    continue;
                }
                Object value = field.get(obj);
                if (value != null) {
                    settingMap.put(field.getName(), serializeValue(value));
                }
            } catch (IllegalAccessException | JsonProcessingException e) {
                System.out.println("Failed to write "+tClass+"into redis.");// Log the exception or handle it as needed
            }
        }
        currentClass = currentClass.getSuperclass();
    }
    jedis.hmset(key, settingMap);
}

public static String serializeValue(Object value) throws JsonProcessingException {
    if (value instanceof String[] array) {
        return String.join(",", array);
    } else if (isPrimitiveOrWrapper(value.getClass())) {
        return String.valueOf(value);
    } else if (value instanceof List || value instanceof Map) {
        return objectMapper.writeValueAsString(value);
    } else {
        return objectMapper.writeValueAsString(value);
    }
}

public static <T> T readFromRedis(String key, Jedis jedis, Class<T> tClass) throws Exception {
    Map<String, String> redisMap = jedis.hgetAll(key);
    T obj = tClass.getDeclaredConstructor().newInstance();

    Class<?> currentClass = tClass;
    while (currentClass != null) {
        for (Field field : currentClass.getDeclaredFields()) {
            field.setAccessible(true);

            if (Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            String fieldName = field.getName();
            String value = redisMap.get(fieldName);

            if (value != null) {
                Object deserializedValue = deserializeValue(value, field.getType());
                field.set(obj, deserializedValue);
            }
        }
        currentClass = currentClass.getSuperclass();
    }

    return obj;
}

public static Object deserializeValue(String value, Class<?> type) throws JsonProcessingException {
    if (type.isArray() && type.getComponentType() == String.class) {
        return value.split(",");
    } else if (isPrimitiveOrWrapper(type)) {
        return parsePrimitiveOrWrapper(value, type);
    } else if (type == List.class) {
        return objectMapper.readValue(value, List.class);
    } else if (type == Map.class) {
        return objectMapper.readValue(value, Map.class);
    } else {
        return objectMapper.readValue(value, type);
    }
}

private static Object parsePrimitiveOrWrapper(String value, Class<?> type) {
    if (type == Boolean.class || type == boolean.class) return Boolean.parseBoolean(value);
    if (type == Byte.class || type == byte.class) return Byte.parseByte(value);
    if (type == Short.class || type == short.class) return Short.parseShort(value);
    if (type == Integer.class || type == int.class) return Integer.parseInt(value);
    if (type == Long.class || type == long.class) return Long.parseLong(value);
    if (type == Float.class || type == float.class) return Float.parseFloat(value);
    if (type == Double.class || type == double.class) return Double.parseDouble(value);
    if (type == Character.class || type == char.class) return value.charAt(0);
    return value; // for String
}



    // public static <T> void writeToRedis(Object obj, String key, Jedis jedis, Class<T> tClass) {
    //     Map<String, String> settingMap = new HashMap<>();

    //     Class<?> currentClass = tClass;
    //     while (currentClass != null) {
    //         for (Field field : currentClass.getDeclaredFields()) {
    //             field.setAccessible(true); // to access private fields

    //             // Skip transient fields
    //             if (Modifier.isTransient(field.getModifiers())) {
    //                 continue;
    //             }

    //             try {
    //                 if (field.getType() == org.slf4j.Logger.class) {
    //                     // Skip Logger fields
    //                     continue;
    //                 }
    //                 Object value = field.get(obj);
    //                 if (value != null) {
    //                     if (value instanceof String[] array) {
    //                         String joinedString = String.join(",", array);
    //                         settingMap.put(field.getName(), joinedString);
    //                     } else if (isPrimitiveOrWrapper(value.getClass())) {
    //                         settingMap.put(field.getName(), String.valueOf(value));
    //                     } else {
    //                         String jsonString = objectMapper.writeValueAsString(value);
    //                         settingMap.put(field.getName(), jsonString);
    //                     }
    //                 }
    //             } catch (IllegalAccessException | com.fasterxml.jackson.core.JsonProcessingException ignore) {
    //                 // Handle exceptions if needed
    //             }
    //         }
    //         currentClass = currentClass.getSuperclass();
    //     }
    //     jedis.hmset(key, settingMap);
    // }

//
//    public static <T> void writeToRedis(Object obj, String key, Jedis jedis, Class<T> tClass) {
//        Map<String, String> settingMap = new HashMap<>();
//
//        Class<?> currentClass = tClass;
//        while (currentClass != null) {
//            for (Field field : currentClass.getDeclaredFields()) {
//                field.setAccessible(true); // to access private fields
//                try {
//                    if (field.getType() == org.slf4j.Logger.class) {
//                        // Skip Logger fields
//                        continue;
//                    }
//                    Object value = field.get(obj);
//                    if (value != null) {
//                        if (value instanceof String[] array) {
//                            String joinedString = String.join(",", array);
//                            settingMap.put(field.getName(), joinedString);
//                        } else if (isPrimitiveOrWrapper(value.getClass())) {
//                            settingMap.put(field.getName(), String.valueOf(value));
//                        } else {
//                            String jsonString = objectMapper.writeValueAsString(value);
//                            settingMap.put(field.getName(), jsonString);
//                        }
//                    }
//                } catch (IllegalAccessException | com.fasterxml.jackson.core.JsonProcessingException ignore) {
//                }
//            }
//            currentClass = currentClass.getSuperclass();
//        }
//
//        jedis.hmset(key, settingMap);
//    }
    private static boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() ||
                type == Boolean.class ||
                type == Byte.class ||
                type == Character.class ||
                type == Double.class ||
                type == Float.class ||
                type == Integer.class ||
                type == Long.class ||
                type == Short.class ||
                type == String.class;
    }

}