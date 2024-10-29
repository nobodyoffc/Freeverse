package apip.apipData;

import javaTools.StringTools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Range {
    private String[] fields;
    private String gt;
    private String gte;
    private String lt;
    private String lte;
    public static final String GT = "gt";
    public static final String GTE = "gte";
    public static final String LT = "lt";
    public static final String LTE = "lte";

    public static String rangeToUrlParam(Range range) {
        if(range.getFields().length>1){
            System.out.println("To make terms into URL, the field can not more than one.");
            return null;
        }
        List<String> stringList = new ArrayList<>();
        stringList.add(range.getFields()[0]);
        if(range.getGt()!=null){
            stringList.add(GT);
            stringList.add(range.getGt());
        }
        if(range.getGte()!=null){
            stringList.add(GTE);
            stringList.add(range.getGte());
        }
        if(range.getLt()!=null){
            stringList.add(LT);
            stringList.add(range.getLt());
        }
        if(range.getLte()!=null){
            stringList.add(LTE);
            stringList.add(range.getLte());
        }
        return StringTools.listToString(stringList);
    }

    public Range addNewFields(String... fields) {
        this.fields = fields;
        return this;
    }

    public Range appendFields(String field) {
        String[] newFields = Arrays.copyOf(fields, fields.length + 1);
        newFields[fields.length] = field;
        fields = newFields;
        return this;
    }

    public Range addGte(String gte) {
        this.gte = gte;
        return this;
    }

    public Range addLt(String lt) {
        this.lt = lt;
        return this;
    }

    public Range addLte(String lte) {
        this.lte = lte;
        return this;
    }

    public Range addGt(String gt) {
        this.gt = gt;
        return this;
    }

    public String[] getFields() {
        return fields;
    }

    public void setFields(String[] fields) {
        this.fields = fields;
    }

    public String getGt() {
        return gt;
    }

    public void setGt(String gt) {
        this.gt = gt;
    }

    public String getGte() {
        return gte;
    }

    public void setGte(String gte) {
        this.gte = gte;
    }

    public String getLt() {
        return lt;
    }

    public void setLt(String lt) {
        this.lt = lt;
    }

    public String getLte() {
        return lte;
    }

    public void setLte(String lte) {
        this.lte = lte;
    }

    static enum op {
        GT("gt"),
        Lt("lt"),
        GTE("gte"),
        LTE("lte");

        op(String name) {
        }
    }
}
