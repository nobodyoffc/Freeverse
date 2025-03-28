package fcData;

import fch.fchData.Cid;
import feip.feipData.Group;
import feip.feipData.Team;
import org.jetbrains.annotations.NotNull;
import utils.StringUtils;

public class TalkIdInfo extends FcObject {
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
        String titleStr = contactDetail.getTitles()==null ? "":"("+contactDetail.getTitles()+")";
        if(contactDetail.getCid()==null)showName= StringUtils.omitMiddle(contactDetail.getFid(),13)+titleStr;
        else showName = contactDetail.getCid()+titleStr;
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


    @Override
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
        this.showName = this.stdName + "(" + StringUtils.omitMiddle(this.id, 11) + ")";
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
        talkIdInfo.setId(team.getId());
        talkIdInfo.setIdType(TalkUnit.IdType.GROUP);
        talkIdInfo.setStdName(team.getStdName());
        talkIdInfo.setOwner(team.getOwner());
        return talkIdInfo;
    }

    @NotNull
    public static TalkIdInfo fromGroup(Group group) {
        TalkIdInfo talkIdInfo = new TalkIdInfo();
        talkIdInfo.setId(group.getId());
        talkIdInfo.setIdType(TalkUnit.IdType.GROUP);
        talkIdInfo.setStdName(group.getName());
        talkIdInfo.setShowName(group.getName()+ StringUtils.omitMiddle(group.getId(),13));
        return talkIdInfo;
    }

    public static TalkIdInfo fromCidInfo(Cid cid) {
        TalkIdInfo talkIdInfo = new TalkIdInfo();
        talkIdInfo.setId(cid.getId());
        talkIdInfo.setIdType(TalkUnit.IdType.FID);
        talkIdInfo.setStdName(cid.getCid());
        talkIdInfo.setShowName(cid.getCid()==null? StringUtils.omitMiddle(cid.getId(),13): StringUtils.omitMiddle(cid.getCid(),13));
        talkIdInfo.setPubKey(cid.getPubKey());
        return talkIdInfo;
    }
}
