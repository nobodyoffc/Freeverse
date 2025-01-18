package fcData;

import apip.apipData.CidInfo;
import feip.feipData.Group;
import feip.feipData.Team;
import org.jetbrains.annotations.NotNull;
import tools.StringTools;

public class TalkIdInfo extends FcData{
    private String id;
    private TalkUnit.IdType type;
    private String stdName;
    private String showName;
    private String owner;
    private String pubKey;

    @NotNull
    public static TalkIdInfo fidTalkIdInfo(String fid) {
        TalkIdInfo talkIdInfo = new TalkIdInfo();
        talkIdInfo.setId(fid);
        talkIdInfo.setIdType(TalkUnit.IdType.FID);
        return talkIdInfo;
    }

    public static TalkIdInfo fromContact(ContactDetail contactDetail) {
        TalkIdInfo talkIdInfo = new TalkIdInfo();
        talkIdInfo.setId(contactDetail.getFid());
        talkIdInfo.setStdName(contactDetail.getCid());
        String showName;
        String memoStr = contactDetail.getMemo()==null ? "":"("+contactDetail.getMemo()+")";
        if(contactDetail.getCid()==null)showName=StringTools.omitMiddle(contactDetail.getFid(),13)+memoStr;
        else showName = contactDetail.getCid()+memoStr;
        talkIdInfo.setShowName(showName);
        talkIdInfo.setIdType(TalkUnit.IdType.FID);
        talkIdInfo.setPubKey(contactDetail.getPubKey());
        return talkIdInfo;
    }

    public static boolean matchesTalkIdInfo(TalkIdInfo info, String searchTerm) {
        return (info.getId() != null && info.getId().toLowerCase().contains(searchTerm)) ||
               (info.getStdName() != null && info.getStdName().toLowerCase().contains(searchTerm)) ||
               (info.getShowName() != null && info.getShowName().toLowerCase().contains(searchTerm));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
        if (this.stdName != null && this.showName == null) makeName();
    }

    public void setShowName(String showName) {
        this.showName = showName;
    }


    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public TalkUnit.IdType getIdType() {
        return type;
    }

    public void setIdType(TalkUnit.IdType toType) {
        this.type = toType;
    }

    public String getStdName() {
        return stdName;
    }

    public void setStdName(String stdName) {
        this.stdName = stdName;
        if (this.id != null && this.showName == null) makeName();
    }

    public String getShowName() {
        return showName;
    }

    public void makeName() {
        this.showName = this.stdName + "(" + StringTools.omitMiddle(this.id, 11) + ")";
    }

    public TalkUnit.IdType getType() {
        return type;
    }

    public void setType(TalkUnit.IdType type) {
        this.type = type;
    }

    public String getPubKey() {
        return pubKey;
    }

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
    }

    @NotNull
    public static TalkIdInfo fromTeam(Team team) {
        TalkIdInfo talkIdInfo = new TalkIdInfo();
        talkIdInfo.setId(team.getTid());
        talkIdInfo.setIdType(TalkUnit.IdType.GROUP);
        talkIdInfo.setStdName(team.getStdName());
        talkIdInfo.setOwner(team.getOwner());
        return talkIdInfo;
    }

    @NotNull
    public static TalkIdInfo fromGroup(Group group) {
        TalkIdInfo talkIdInfo = new TalkIdInfo();
        talkIdInfo.setId(group.getGid());
        talkIdInfo.setIdType(TalkUnit.IdType.GROUP);
        talkIdInfo.setStdName(group.getName());
        talkIdInfo.setShowName(group.getName()+StringTools.omitMiddle(group.getGid(),13));
        return talkIdInfo;
    }

    public static TalkIdInfo fromCidInfo(CidInfo cidInfo) {
        TalkIdInfo talkIdInfo = new TalkIdInfo();
        talkIdInfo.setId(cidInfo.getFid());
        talkIdInfo.setIdType(TalkUnit.IdType.FID);
        talkIdInfo.setStdName(cidInfo.getCid());
        talkIdInfo.setShowName(cidInfo.getCid()==null?StringTools.omitMiddle(cidInfo.getFid(),13):StringTools.omitMiddle(cidInfo.getCid(),13));
        talkIdInfo.setPubKey(cidInfo.getPubKey());
        return talkIdInfo;
    }
}
