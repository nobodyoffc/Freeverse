package fcData;

import utils.JsonUtils;

public class RoomInfo extends FcObject{
    private String name;
    private String owner;
    private String[] members;

    public byte[] toBytes() {
        return JsonUtils.toJson(this).getBytes();
    }

    public static RoomInfo fromBytes(byte[] bytes) {
        return JsonUtils.fromJson(new String(bytes), RoomInfo.class);
    }

    public static RoomInfo fromRoom(Room room) {
        RoomInfo roomInfo = new RoomInfo();
        roomInfo.setId(room.getId());
        roomInfo.setName(room.getName());
        roomInfo.setOwner(room.getOwner());
        return roomInfo;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getMembers() {
        return members;
    }

    public void setMembers(String[] members) {
        this.members = members;
    }
}
