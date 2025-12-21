package data.fcData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import data.apipData.WebhookInfo;
import data.fchData.*;
import data.feipData.*;
import feature.swap.*;
import org.jetbrains.annotations.NotNull;
import utils.JsonUtils;

import java.io.File;
import java.util.*;

import static constants.Strings.DOT_JSON;


public abstract class FcEntity {

    protected String id;
    protected Meta meta;

    public static final String METHOD_GET_FIELD_WIDTH_MAP = "getFieldWidthMap";
    public static final String METHOD_GET_TIMESTAMP_FIELD_LIST = "getTimestampFieldList";
    public static final String METHOD_GET_SATOSHI_FIELD_LIST = "getSatoshiFieldList";
    public static final String METHOD_GET_HEIGHT_TO_TIME_FIELD_MAP = "getHeightToTimeFieldMap";
    public static final String METHOD_GET_SHOW_FIELD_NAME_AS_MAP = "getShowFieldNameAsMap";
    public static final String METHOD_GET_INPUT_FIELD_DEFAULT_VALUE_MAP = "getInputFieldDefaultValueMap";
    public static final String METHOD_GET_REPLACE_WITH_ME_FIELD_LIST = "getReplaceWithMeFieldList";
    // 实体名到实体 Class 的映射表
    public static final Map<String, Class<?>> entityClassMap = new HashMap<>();
    public static int DEFAULT_ID_LENGTH = 13;
    public static int DEFAULT_TEXT_LENGTH = 33;
    public static int DEFAULT_SHORT_TEXT_LENGTH = 9;
    public static int DEFAULT_TIME_LENGTH = 15;
    public static int DEFAULT_AMOUNT_LENGTH = 10;
    public static int DEFAULT_CD_LENGTH = 5;
    public static int DEFAULT_BOOLEAN_LENGTH = 5;

    static {
        FcEntity.entityClassMap.put(constants.IndicesNames.BLOCK, Block.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.TX, Tx.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.CASH, Cash.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.OPRETURN, OpReturn.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.MULTISIG, Multisig.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.P2SH, P2SH.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.BLOCK_MARK, BlockMark.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.FREER, Freer.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.FREER_HISTORY, CidHist.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.REPUTATION_HISTORY, RepuHist.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.PROTOCOL, Protocol.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.CODE, Code.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.SERVICE, Service.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.APP, App.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.PROTOCOL_HISTORY, ProtocolHistory.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.CODE_HISTORY, CodeHistory.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.SERVICE_HISTORY, ServiceHistory.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.APP_HISTORY, AppHistory.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.CONTACT, Contact.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.MAIL, Mail.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.SECRET, Secret.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.BOX, Box.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.BOX_HISTORY, BoxHistory.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.GROUP, Group.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.TEAM, Team.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.GROUP_HISTORY, GroupHistory.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.TEAM_HISTORY, TeamHistory.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.STATEMENT, Statement.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.TEXT, Text.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.REMARK, Remark.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.TEXT_HISTORY, TextHistory.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.REMARK_HISTORY, RemarkHistory.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.PROOF, Proof.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.PROOF_HISTORY, ProofHistory.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.NID, Nid.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.WEBHOOK, WebhookInfo.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.NOBODY, Nobody.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.NEWS, News.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.SWAP_STATE, SwapStateData.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.SWAP_LP, SwapLpData.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.SWAP_FINISHED, SwapAffair.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.SWAP_PENDING, SwapPendingData.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.SWAP_PRICE, SwapPriceData.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.TOKEN_HISTORY, TokenHistory.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.TOKEN, Token.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.TOKEN_HOLDER, TokenHolder.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.SOUND, Sound.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.SOUND_HISTORY, SoundHistory.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.IMAGE, Image.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.IMAGE_HISTORY, ImageHistory.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.VIDEO, Video.class);
        FcEntity.entityClassMap.put(constants.IndicesNames.VIDEO_HISTORY, VideoHistory.class);
    }


    public static  <T extends FcEntity> int updateIntoListById(T item, List<T> itemList) {
        if(itemList==null)return -1;
        for (int i = 0; i < itemList.size(); i++) {
            if (itemList.get(i).getId().equals(item.getId())) {
                itemList.set(i, item); // Replace old keyInfo with new one
                return i;
            }
        }
        
        itemList.add(item);
        return itemList.size() - 1;
    }

    @NotNull
    public static <T> ShowingRules getRules(Class<T> itemClass) {
        LinkedHashMap<String, Integer> fieldWidthMap;
        List<String> timestampFieldList;
        List<String> satoshiField;
        Map<String, String> heightToTimeFieldMap;
        Map<String, String> showFieldNameAs;
        List<String> replaceWithMeFieldList;
        try {
            // Call static methods on the specific itemClass using reflection
            fieldWidthMap = (LinkedHashMap<String, Integer>) itemClass.getMethod(METHOD_GET_FIELD_WIDTH_MAP).invoke(null);
            timestampFieldList = (List<String>) itemClass.getMethod(METHOD_GET_TIMESTAMP_FIELD_LIST).invoke(null);
            satoshiField = (List<String>) itemClass.getMethod(METHOD_GET_SATOSHI_FIELD_LIST).invoke(null);
            heightToTimeFieldMap = (Map<String, String>) itemClass.getMethod(METHOD_GET_HEIGHT_TO_TIME_FIELD_MAP).invoke(null);
            showFieldNameAs = (Map<String, String>) itemClass.getMethod(METHOD_GET_SHOW_FIELD_NAME_AS_MAP).invoke(null);
            replaceWithMeFieldList = (List<String>) itemClass.getMethod(METHOD_GET_REPLACE_WITH_ME_FIELD_LIST).invoke(null);
        } catch (Exception e) {
            // If reflection fails, fall back to FcEntity defaults
            fieldWidthMap = getFieldWidthMap();
            timestampFieldList = getTimestampFieldList();
            satoshiField = getSatoshiFieldList();
            heightToTimeFieldMap = getHeightToTimeFieldMap();
            showFieldNameAs = getShowFieldNameAsMap();
            replaceWithMeFieldList = getReplaceWithMeFieldList();
        }
        ShowingRules result = new ShowingRules(fieldWidthMap, timestampFieldList, satoshiField, heightToTimeFieldMap, showFieldNameAs, replaceWithMeFieldList);
        return result;
    }

    // 获取实体名对应的实体 Class
    public static Class<?> getEntityClass(String entityName) {
        return entityClassMap.get(entityName);
    }

    /**
     * 获取所有实体名称的集合
     * @return 实体名称的 Set
     */
    public static Set<String> getEntityNames() {
        return entityClassMap.keySet();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
    public String toJson() {
        return new Gson().toJson(this);
    }

    public String toNiceJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(this);
    }

    public static <T extends FcEntity> T fromJson(String json, Class<T> clazz) {
        Gson gson = new Gson();
        return gson.fromJson(json, clazz);
    }

    public byte[] toBytes() {
        Gson gson = new Gson();
        return gson.toJson(this).getBytes();
    }

    public static <T extends FcEntity> T fromBytes(byte[] bytes, Class<T> clazz) {
        String json = new String(bytes);

        Gson gson = new Gson();
        try {
            return gson.fromJson(json, clazz);
        } catch (Exception e) {
            System.err.println("Failed to parse JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static <T extends FcEntity> List<T> listFromFile(Class<T> clazz) {
        return listFromFile(null,clazz);
    }
    public static <T extends FcEntity> List<T> listFromFile(String filePath, Class<T> clazz) {
        if(filePath==null)filePath = clazz.getSimpleName()+".json";
        try {
            return JsonUtils.readJsonObjectListFromFile(filePath, clazz);
        } catch (Exception e) {
            System.out.println("Error reading "+clazz.getSimpleName()+" list from file: " + e.getMessage());
            return null;
        }
    }

    public static <T extends FcEntity> void listToFile(List<T> list, Class<T> clazz) {
        listToFile(list, clazz.getSimpleName(), false);
    }
    public static <T extends FcEntity> void listToFile(List<T> list, String className, boolean isAppend) {
        String filePath = className + DOT_JSON;
        if(list==null || list.isEmpty()){
            if(isAppend)return;
            else {
                File file = new File(filePath);
                if(file.exists())file.delete();
                return;
            }
        }
        try {
            JsonUtils.writeListToJsonFile(list, filePath, isAppend);
        } catch (Exception e) {
            System.out.println("Error writing "+list.get(0).getClass().getSimpleName()+" list to file: " + e.getMessage());
        }
        
    }
    /**
     * Get the field width map for displaying items
     * @return Map of field names to their display widths
     */
    public static LinkedHashMap<String, Integer> getFieldWidthMap() {
        return new LinkedHashMap<>();
    }

    /**
     * Get list of fields that contain timestamp values
     * @return List of timestamp field names
     */
    public static List<String> getTimestampFieldList() {
        return new ArrayList<>();
    }

    /**
     * Get list of fields that contain satoshi values
     * @return List of satoshi field names
     */
    public static List<String> getSatoshiFieldList() {
        return new ArrayList<>();
    }

    /**
     * Get map of height fields to their corresponding time fields
     * @return Map of height field names to time field names
     */
    public static Map<String, String> getHeightToTimeFieldMap() {
        return new HashMap<>();
    }

    /**
     * Get map of field names to their display names
     * @return Map of field names to display names
     */
    public static Map<String, String> getShowFieldNameAsMap() {
        return new HashMap<>();
    }

    /**
     * Get map of input field names to their default values
     * @return Map of input field names to default values
     */
    public static Map<String, Object> getInputFieldDefaultValueMap() {
        return new HashMap<>();
    }

    /**
     * Get map of input field names to their default values
     * @return Map of input field names to default values
     */
    public static List<String> getReplaceWithMeFieldList() {
        return new ArrayList<>();
    }

    public record ShowingRules(LinkedHashMap<String, Integer> fieldWidthMap, List<String> timestampFieldList, List<String> satoshiField, Map<String, String> heightToTimeFieldMap, Map<String, String> showFieldNameAsMap,List<String> replaceWithMeFieldList) {
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }
}
