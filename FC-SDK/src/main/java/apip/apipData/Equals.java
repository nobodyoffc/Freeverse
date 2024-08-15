package apip.apipData;

import javaTools.StringTools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Equals {
    private String[] fields;
    private String[] values;

    public static String equalsToUrlParam(Equals equals) {
        if(equals==null)return null;
        if(equals.getFields().length>1){
            System.out.println("To make terms into URL, the field can not more than one.");
            return null;
        }
        List<String> stringList = new ArrayList<>();
        stringList.add(equals.getFields()[0]);
        stringList.addAll(Arrays.asList(equals.getValues()));
        return StringTools.listToString(stringList);
    }

    public Equals addNewFields(String... fields) {
        this.fields = fields;
        return this;
    }

    public Equals appendFields(String field) {
        String[] newFields = Arrays.copyOf(fields, fields.length + 1);
        newFields[fields.length] = field;
        fields = newFields;
        return this;
    }

    public Equals addNewValues(String... values) {
        this.values = values;
        return this;
    }

    public Equals appendValues(String field) {
        String[] newValues = Arrays.copyOf(values, values.length + 1);
        newValues[values.length] = field;
        values = newValues;
        return this;
    }

    public String[] getFields() {
        return fields;
    }

    public void setFields(String[] fields) {
        this.fields = fields;
    }

    public String[] getValues() {
        return values;
    }

    public void setValues(String[] values) {
        this.values = values;
    }
}
