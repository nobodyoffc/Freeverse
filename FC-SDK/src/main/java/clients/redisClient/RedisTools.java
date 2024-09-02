package clients.redisClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import crypto.KeyTools;
import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class RedisTools {

    public <T> void writeObjectToRedis(Object obj,String key, Jedis jedis,Class<T> tClass) {
        Map<String, String> objMap = new HashMap<>();
        for (Field field : tClass.getDeclaredFields()) {
            field.setAccessible(true); // to access private fields
            try {
                Object value = field.get(obj);
                if(value!=null)
                    objMap.put(field.getName(), String.valueOf(value));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        jedis.hmset(key, objMap);
    }

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
                field.setAccessible(true); // to access private fields

                // Skip transient fields
                if (Modifier.isTransient(field.getModifiers())) {
                    continue;
                }

                try {
                    if (field.getType() == org.slf4j.Logger.class) {
                        // Skip Logger fields
                        continue;
                    }
                    Object value = field.get(obj);
                    if (value != null) {
                        if (value instanceof String[] array) {
                            String joinedString = String.join(",", array);
                            settingMap.put(field.getName(), joinedString);
                        } else if (isPrimitiveOrWrapper(value.getClass())) {
                            settingMap.put(field.getName(), String.valueOf(value));
                        } else {
                            String jsonString = objectMapper.writeValueAsString(value);
                            settingMap.put(field.getName(), jsonString);
                        }
                    }
                } catch (IllegalAccessException | com.fasterxml.jackson.core.JsonProcessingException ignore) {
                    // Handle exceptions if needed
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        jedis.hmset(key, settingMap);
    }

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
