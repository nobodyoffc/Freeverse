package apip.apipData;

import javaTools.StringTools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Part {
    private String[] fields;
    private String value;
    private String isCaseInsensitive;

    public static String partToUrlParam(Part part) {
        if(part==null)return null;
        List<String> stringList = new ArrayList<>(Arrays.asList(part.getFields()));
        stringList.add(part.getValue());
        return StringTools.listToString(stringList);
    }


    public Part addNewValue(String value) {
        this.value = value;
        return this;
    }

    public Part addNewFields(String... fields) {
        this.fields = fields;
        return this;
    }

    public Part appendFields(String field) {
        String[] newFields = Arrays.copyOf(fields, fields.length + 1);
        newFields[fields.length] = field;
        fields = newFields;
        return this;
    }

    public String getIsCaseInsensitive() {
        return isCaseInsensitive;
    }

    public void setIsCaseInsensitive(String isCaseInsensitive) {
        this.isCaseInsensitive = isCaseInsensitive;
    }

    public String[] getFields() {
        return fields;
    }

    public void setFields(String[] fields) {
        this.fields = fields;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
