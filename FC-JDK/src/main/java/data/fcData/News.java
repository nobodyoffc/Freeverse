package data.fcData;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import constants.IndicesNames;

public class News extends FcObject{
    /*
        1. Publish, Stop, Recover, Close of Protocol, Service, Code and App;
        2. any act of Statement
        3. any act of Nobody
        4. Publish of Essay, Report, Paper, Book, Artwork, Sound, Image and Video.
        5. Create of Group
        6. Create, Transfer, TakeOver and Disband of Team
        7. Deploy and Close of Token

     */
    private String doer;    //signer
    private String act;     //op
    private String objectType;  //protocol sn of Feip
    private String objectId;    //id
    private String objectName;  //name, stdName or title
    private String objectBrief; //a brief of the content or desc
    private Long height;    //height
    private Long time;      //time

    /**
     * Helper method to create and index a News record
     * @param esClient Elasticsearch client
     * @param txId Transaction ID (used as news ID)
     * @param doer The signer/doer of the action
     * @param act The operation/action (e.g., "publish", "stop", "recover")
     * @param objectType The protocol SN or type (e.g., "21" for Essay)
     * @param objectId The ID of the object being acted upon
     * @param objectName The name/title of the object
     * @param objectBrief Brief description or content
     * @param height Block height
     * @param time Block timestamp
     */
    public static void createNews(ElasticsearchClient esClient, String txId, String doer, String act,
                                  String objectType, String objectId, String objectName, String objectBrief,
                                  Long height, Long time) {
        try {
            News news = new News();
            news.setId(txId);
            news.setDoer(doer);
            news.setAct(act);
            news.setObjectType(objectType);
            news.setObjectId(objectId);
            news.setObjectName(objectName);
            if(objectBrief!=null) {
               if(objectBrief.length()>200)
                   objectBrief = objectBrief.substring(0, 200);
               news.setObjectBrief(objectBrief);
            }
            news.setHeight(height);
            news.setTime(time);

             esClient.index(i -> i.index(IndicesNames.NEWS).id(news.getId()).document(news));
        } catch (Exception e) {
            // Log error but don't fail the main parsing operation
            System.err.println("Failed to create news for txId: " + txId + ", error: " + e.getMessage());
        }
    }

    public String getDoer() {
        return doer;
    }

    public void setDoer(String doer) {
        this.doer = doer;
    }

    public String getAct() {
        return act;
    }

    public void setAct(String act) {
        this.act = act;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getObjectBrief() {
        return objectBrief;
    }

    public void setObjectBrief(String objectBrief) {
        this.objectBrief = objectBrief;
    }

    public Long getHeight() {
        return height;
    }

    public void setHeight(Long height) {
        this.height = height;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }
}
