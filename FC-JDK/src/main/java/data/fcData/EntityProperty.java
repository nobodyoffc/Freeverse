package data.fcData;

import data.apipData.WebhookInfo;
import data.fchData.*;
import data.feipData.*;
import feature.swap.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static constants.FieldNames.*;
import static constants.Values.DESC;

/**
 * Enum defining entity properties including class type and default sorting.
 */
public enum EntityProperty {
    BLOCK(constants.IndicesNames.BLOCK, Block.class, createDefaultSorts(HEIGHT, ID)),
    TX(constants.IndicesNames.TX, Tx.class, createDefaultSorts(HEIGHT, ID)),
    CASH(constants.IndicesNames.CASH, Cash.class, createDefaultSorts(LAST_TIME, ID)),
    OPRETURN(constants.IndicesNames.OPRETURN, OpReturn.class, createDefaultSorts(HEIGHT, ID)),
    MULTISIG(constants.IndicesNames.MULTISIG, Multisig.class, createDefaultSorts(BIRTH_HEIGHT, ID)),
    P2SH(constants.IndicesNames.P2SH, P2SH.class, createDefaultSorts(BIRTH_HEIGHT, ID)),
    BLOCK_MARK(constants.IndicesNames.BLOCK_MARK, BlockMark.class, createDefaultSorts(HEIGHT, ID)),
    FREER(constants.IndicesNames.FREER, Freer.class, createDefaultSorts(LAST_HEIGHT, ID)),
    FREER_HISTORY(constants.IndicesNames.FREER_HISTORY, FreerHist.class, createDefaultSorts(HEIGHT, ID)),
    REPUTATION_HISTORY(constants.IndicesNames.REPUTATION_HISTORY, RepuHist.class, createDefaultSorts(HEIGHT, ID)),
    PROTOCOL(constants.IndicesNames.PROTOCOL, Protocol.class, createDefaultSorts(LAST_HEIGHT, ID)),
    CODE(constants.IndicesNames.CODE, Code.class, createDefaultSorts(LAST_HEIGHT, ID)),
    SERVICE(constants.IndicesNames.SERVICE, Service.class, createDefaultSorts(LAST_HEIGHT, ID)),
    APP(constants.IndicesNames.APP, App.class, createDefaultSorts(LAST_HEIGHT, ID)),
    PROTOCOL_HISTORY(constants.IndicesNames.PROTOCOL_HISTORY, ProtocolHistory.class, createDefaultSorts(HEIGHT, ID)),
    CODE_HISTORY(constants.IndicesNames.CODE_HISTORY, CodeHistory.class, createDefaultSorts(HEIGHT, ID)),
    SERVICE_HISTORY(constants.IndicesNames.SERVICE_HISTORY, ServiceHistory.class, createDefaultSorts(HEIGHT, ID)),
    APP_HISTORY(constants.IndicesNames.APP_HISTORY, AppHistory.class, createDefaultSorts(HEIGHT, ID)),
    CONTACT(constants.IndicesNames.CONTACT, Contact.class, createDefaultSorts(LAST_HEIGHT, ID)),
    MAIL(constants.IndicesNames.MAIL, Mail.class, createDefaultSorts(LAST_HEIGHT, ID)),
    SECRET(constants.IndicesNames.SECRET, Secret.class, createDefaultSorts(LAST_HEIGHT, ID)),
    BOX(constants.IndicesNames.BOX, Box.class, createDefaultSorts(LAST_HEIGHT, ID)),
    BOX_HISTORY(constants.IndicesNames.BOX_HISTORY, BoxHistory.class, createDefaultSorts(HEIGHT, ID)),
    SQUARE(constants.IndicesNames.SQUARE, Square.class, createDefaultSorts(LAST_HEIGHT, ID)),
    TEAM(constants.IndicesNames.TEAM, Team.class, createDefaultSorts(LAST_HEIGHT, ID)),
    SQUARE_HISTORY(constants.IndicesNames.SQUARE_HISTORY, SquareHistory.class, createDefaultSorts(HEIGHT, ID)),
    TEAM_HISTORY(constants.IndicesNames.TEAM_HISTORY, TeamHistory.class, createDefaultSorts(HEIGHT, ID)),
    STATEMENT(constants.IndicesNames.STATEMENT, Statement.class, createDefaultSorts(HEIGHT, ID)),
    TEXT(constants.IndicesNames.TEXT, Text.class, createDefaultSorts(LAST_HEIGHT, ID)),
    REMARK(constants.IndicesNames.REMARK, Remark.class, createDefaultSorts(LAST_HEIGHT, ID)),
    TEXT_HISTORY(constants.IndicesNames.TEXT_HISTORY, TextHistory.class, createDefaultSorts(HEIGHT, ID)),
    REMARK_HISTORY(constants.IndicesNames.REMARK_HISTORY, RemarkHistory.class, createDefaultSorts(HEIGHT, ID)),
    PROOF(constants.IndicesNames.PROOF, Proof.class, createDefaultSorts(LAST_HEIGHT, ID)),
    PROOF_HISTORY(constants.IndicesNames.PROOF_HISTORY, ProofHistory.class, createDefaultSorts(HEIGHT, ID)),
    NID(constants.IndicesNames.NID, Nid.class, createDefaultSorts(LAST_HEIGHT, ID)),
    WEBHOOK(constants.IndicesNames.WEBHOOK, WebhookInfo.class, createDefaultSorts(LAST_HEIGHT, ID)),
    NOBODY(constants.IndicesNames.NOBODY, Nobody.class, createDefaultSorts(LAST_HEIGHT, ID)),
    NEWS(constants.IndicesNames.NEWS, News.class, createDefaultSorts(LAST_HEIGHT, ID)),
    SWAP_STATE(constants.IndicesNames.SWAP_STATE, SwapStateData.class, createDefaultSorts(LAST_HEIGHT, ID)),
    SWAP_LP(constants.IndicesNames.SWAP_LP, SwapLpData.class, createDefaultSorts(LAST_HEIGHT, ID)),
    SWAP_FINISHED(constants.IndicesNames.SWAP_FINISHED, SwapAffair.class, createDefaultSorts(LAST_HEIGHT, ID)),
    SWAP_PENDING(constants.IndicesNames.SWAP_PENDING, SwapPendingData.class, createDefaultSorts(LAST_HEIGHT, ID)),
    SWAP_PRICE(constants.IndicesNames.SWAP_PRICE, SwapPriceData.class, createDefaultSorts(LAST_HEIGHT, ID)),
    TOKEN_HISTORY(constants.IndicesNames.TOKEN_HISTORY, TokenHistory.class, createDefaultSorts(HEIGHT, ID)),
    TOKEN(constants.IndicesNames.TOKEN, Token.class, createDefaultSorts(LAST_HEIGHT, ID)),
    TOKEN_HOLDER(constants.IndicesNames.TOKEN_HOLDER, TokenHolder.class, createDefaultSorts(LAST_HEIGHT, ID)),
    SOUND(constants.IndicesNames.SOUND, Sound.class, createDefaultSorts(LAST_HEIGHT, ID)),
    SOUND_HISTORY(constants.IndicesNames.SOUND_HISTORY, SoundHistory.class, createDefaultSorts(HEIGHT, ID)),
    IMAGE(constants.IndicesNames.IMAGE, Image.class, createDefaultSorts(LAST_HEIGHT, ID)),
    IMAGE_HISTORY(constants.IndicesNames.IMAGE_HISTORY, ImageHistory.class, createDefaultSorts(HEIGHT, ID)),
    VIDEO(constants.IndicesNames.VIDEO, Video.class, createDefaultSorts(LAST_HEIGHT, ID)),
    VIDEO_HISTORY(constants.IndicesNames.VIDEO_HISTORY, VideoHistory.class, createDefaultSorts(HEIGHT, ID));

    private final String entityName;
    private final Class<?> entityClass;
    private final Map<String, String> defaultSorts;

    // Static lookup maps for efficient access
    private static final Map<String, EntityProperty> NAME_TO_PROPERTY = new HashMap<>();
    private static final Map<String, Class<?>> NAME_TO_CLASS = new HashMap<>();

    static {
        for (EntityProperty prop : values()) {
            NAME_TO_PROPERTY.put(prop.entityName, prop);
            NAME_TO_CLASS.put(prop.entityName, prop.entityClass);
        }
    }

    EntityProperty(String entityName, Class<?> entityClass, Map<String, String> defaultSorts) {
        this.entityName = entityName;
        this.entityClass = entityClass;
        this.defaultSorts = defaultSorts;
    }

    public String getEntityName() {
        return entityName;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public Map<String, String> getDefaultSorts() {
        return defaultSorts;
    }

    /**
     * Get EntityProperty by entity name
     * @param entityName the entity/index name
     * @return EntityProperty or null if not found
     */
    public static EntityProperty getByName(String entityName) {
        return NAME_TO_PROPERTY.get(entityName);
    }

    /**
     * Get entity class by entity name
     * @param entityName the entity/index name
     * @return the entity class or null if not found
     */
    public static Class<?> getEntityClassByName(String entityName) {
        return NAME_TO_CLASS.get(entityName);
    }

    /**
     * Get default sorts by entity name
     * @param entityName the entity/index name
     * @return the default sorts map or null if not found
     */
    public static Map<String, String> getDefaultSortsByName(String entityName) {
        EntityProperty prop = NAME_TO_PROPERTY.get(entityName);
        return prop != null ? prop.defaultSorts : null;
    }

    /**
     * Get all entity names
     * @return Set of all entity names
     */
    public static Set<String> getEntityNames() {
        return NAME_TO_PROPERTY.keySet();
    }

    /**
     * Get the entityClassMap for backward compatibility
     * @return Map of entity name to entity class
     */
    public static Map<String, Class<?>> getEntityClassMap() {
        return new HashMap<>(NAME_TO_CLASS);
    }

    /**
     * Helper method to create default sorts with two fields, both desc order
     */
    private static Map<String, String> createDefaultSorts(String field1, String field2) {
        Map<String, String> sorts = new LinkedHashMap<>();
        sorts.put(field1, DESC);
        if(field2!=null)
            sorts.put(field2, DESC);
        return sorts;
    }
}

