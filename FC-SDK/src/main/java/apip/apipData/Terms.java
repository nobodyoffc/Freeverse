package apip.apipData;

import javaTools.StringTools;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Terms {
    private String[] fields;
    private String[] values;

    @Nullable
    public static String termsToUrlParam(Terms terms) {
        if(terms==null)return null;
        if(terms.getFields().length>1){
            System.out.println("To make terms into URL, the field can not more than one.");
            return null;
        }
        List<String> stringList = new ArrayList<>();
        stringList.add(terms.getFields()[0]);
        stringList.addAll(Arrays.asList(terms.getValues()));
        return StringTools.listToString(stringList);
    }

    public Terms addNewFields(String... fields) {
        this.fields = fields;
        return this;
    }

    public Terms appendFields(String field) {
        String[] newFields = Arrays.copyOf(fields, fields.length + 1);
        newFields[fields.length] = field;
        fields = newFields;
        return this;
    }

    public Terms addNewValues(String... values) {
        this.values = values;
        return this;
    }

    public Terms appendValues(String value) {
        String[] newValues = Arrays.copyOf(this.values, this.values.length + 1);
        newValues[this.values.length] = value;
        this.values = newValues;
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

    public void setValues(String... values) {
        this.values = values;
    }
}
