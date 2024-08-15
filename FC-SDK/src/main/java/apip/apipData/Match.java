package apip.apipData;

import javaTools.StringTools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Match {
    private String[] fields;
    private String value;

    public static String matchToUrlParam(Match match) {
        if(match==null)return null;
        List<String> stringList = new ArrayList<>(Arrays.asList(match.getFields()));
        stringList.add(match.getValue());
        return StringTools.listToString(stringList);
    }

    public Match addNewFields(String... fields) {
        this.fields = fields;
        return this;
    }

    public Match appendFields(String field) {
        String[] newFields = Arrays.copyOf(fields, fields.length + 1);
        newFields[fields.length] = field;
        fields = newFields;
        return this;
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
    public Match addNewValue(String value) {
        this.value = value;
        return this;
    }
}
