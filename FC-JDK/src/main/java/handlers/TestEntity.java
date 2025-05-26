package handlers;

import data.fcData.FcEntity;

public class TestEntity extends FcEntity {
    private String name;
    private String description;

    public TestEntity() {
        // Default constructor
    }

    public TestEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "TestEntity{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
} 