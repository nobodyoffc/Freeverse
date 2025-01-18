package fcData;

import tools.JsonTools;

public class RoomInfo {
    private String roomId;
    private String name;
    private String owner;
    private String[] members;

    public byte[] toBytes() {
        return tools.JsonTools.toJson(this).getBytes();
    }

    public static RoomInfo fromBytes(byte[] bytes) {
        return JsonTools.fromJson(new String(bytes), RoomInfo.class);
    }

    public static RoomInfo fromRoom(Room room) {
        RoomInfo roomInfo = new RoomInfo();
        roomInfo.setRoomId(room.getRoomId());
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

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
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
