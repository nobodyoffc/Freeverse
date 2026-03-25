package fapi;

import data.feipData.ApiGroupType;
import fapi.components.BaseComponent;
import fapi.components.DiskComponent;
import fapi.components.DockComponent;
import fapi.components.MapComponent;
import fapi.components.RoadComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 组件注册表
 * 管理服务类型到组件类的映射
 */
public class ComponentRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(ComponentRegistry.class);
    

    /**
     * 服务类型到组件类的映射
     */
    private static final Map<String, Class<? extends FapiComponent>> COMPONENT_MAP = new HashMap<>();
    
    /**
     * 已注册的组件类型（有对应组件实现的类型）
     */
    private static final Set<String> COMPONENT_TYPES = new HashSet<>();

    static {
        // 注册组件类型
        registerComponent(ApiGroupType.BASE_NO1_NRC7, BaseComponent.class);
        registerComponent(ApiGroupType.DISK_NO1_NRC7, DiskComponent.class);
        registerComponent(ApiGroupType.DOCK_NO1_NRC7, DockComponent.class);
        registerComponent(ApiGroupType.MAP_NO1_NRC7, MapComponent.class);
        registerComponent(ApiGroupType.ROAD_NO1_NRC7, RoadComponent.class);
    }
    
    /**
     * 注册组件类型
     */
    public static void registerComponent(String type, Class<? extends FapiComponent> componentClass) {
        String upperType = type.toUpperCase();
        COMPONENT_MAP.put(upperType, componentClass);
        COMPONENT_TYPES.add(upperType);
        log.info("Registered component type: {} -> {}", upperType, componentClass.getSimpleName());
    }
    
    /**
     * 获取组件类
     */
    public static Class<? extends FapiComponent> getComponentClass(String type) {
        return COMPONENT_MAP.get(type.toUpperCase());
    }
    
    /**
     * 判断是否为已注册的组件类型
     */
    public static boolean isComponent(String type) {
        return type != null && COMPONENT_TYPES.contains(type.toUpperCase());
    }
    
    /**
     * 判断是否有对应的组件类实现
     */
    public static boolean hasImplementation(String type) {
        return type != null && COMPONENT_MAP.containsKey(type.toUpperCase());
    }
    
    /**
     * 获取所有已注册的组件类型
     */
    public static Set<String> getComponentTypes() {
        return Collections.unmodifiableSet(COMPONENT_TYPES);
    }
    
    /**
     * 根据service.types加载组件实例
     * 
     * @param types 服务类型数组
     * @return 组件实例列表
     */
    public List<FapiComponent> loadComponents(String[] types) {
        List<FapiComponent> components = new ArrayList<>();
        
        if (types == null || types.length == 0) {
            return components;
        }
        
        for (String type : types) {

            String upperType = type.toUpperCase();
            
            // 跳过非组件类型（如FAPI是框架类型）
            if (upperType.startsWith("FAPI")) {
                continue;
            }
            
            Class<? extends FapiComponent> clazz = COMPONENT_MAP.get(upperType);
            if (clazz != null) {
                try {
                    FapiComponent component = clazz.getDeclaredConstructor().newInstance();
                    components.add(component);
                    log.debug("Loaded component: {}", upperType);
                } catch (Exception e) {
                    log.error("Failed to instantiate component {}: {}", upperType, e.getMessage(), e);
                }
            } else if (COMPONENT_TYPES.contains(upperType)) {
                log.warn("Component type {} is registered but has no implementation yet", upperType);
            }
        }
        
        return components;
    }
    
    /**
     * 合并组件类型（配置指定 + 链上声明）
     * 
     * @param configured 配置指定的类型
     * @param onChain 链上声明的类型
     * @return 合并后的类型数组
     */
    public static String[] mergeComponentTypes(String[] configured, String[] onChain) {
        Set<String> types = new LinkedHashSet<>();
        
        if (configured != null) {
            for (String t : configured) {
                if (t != null) {
                    types.add(t.toUpperCase());
                }
            }
        }
        
        if (onChain != null) {
            for (String t : onChain) {
                if (t != null && isComponent(t)) {
                    types.add(t.toUpperCase());
                }
            }
        }
        
        return types.toArray(new String[0]);
    }
}

