package data.fcData;

public class Module {
    private String name;
    private ModuleType type;

    public enum ModuleType {
        SERVICE,
        MANAGER,
        HANDLER
    }

    public Module(String type, String name) {
        this.name = name;
        this.type = ModuleType.valueOf(type.toUpperCase());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ModuleType getType() {
        return type;
    }

    public void setType(ModuleType type) {
        this.type = type;
    }
}


