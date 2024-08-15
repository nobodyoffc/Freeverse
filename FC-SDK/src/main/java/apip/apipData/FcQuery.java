package apip.apipData;

import appTools.Inputer;
import appTools.Menu;

import java.io.BufferedReader;
import java.util.Arrays;

public class FcQuery {

    public static final String TERMS = "terms";
    public static final String PART = "part";
    public static final String MATCH = "match";
    public static final String RANGE = "range";
    public static final String EQUALS = "equals";
    public static final String EXISTS = "exists";
    public static final String UNEXISTS = "unexists";
    public static final String[] QUERY_FIELDS = new String[]{TERMS, PART, MATCH, RANGE, EQUALS, EXISTS, UNEXISTS};
    protected String[] exists;
    protected String[] unexists;
    protected Terms terms;
    protected Part part;
    protected Match match;
    protected Range range;
    protected Equals equals;

    public Terms addNewTerms() {
        Terms newOne = new Terms();
        this.setTerms(newOne);
        return newOne;
    }

    public Part addNewPart() {
        Part newOne = new Part();
        this.setPart(newOne);
        return newOne;
    }

    public Match addNewMatch() {
        Match newOne = new Match();
        this.setMatch(newOne);
        return newOne;
    }

    public Range addNewRange() {
        Range newOne = new Range();
        this.setRange(newOne);
        return newOne;
    }

    public Equals addNewEquals() {
        Equals newOne = new Equals();
        this.setEquals(newOne);
        return newOne;
    }

    public FcQuery addNewExists(String... fields) {
        this.exists = fields;
        return this;
    }

    public FcQuery appendExists(String field) {
        String[] newExists = Arrays.copyOf(exists, exists.length + 1);
        newExists[exists.length] = field;
        exists = newExists;
        return this;
    }

    public FcQuery addNewUnexists(String... fields) {
        this.unexists = fields;
        return this;
    }

    public FcQuery appendUnexists(String field) {
        String[] newUnexists = Arrays.copyOf(unexists, unexists.length + 1);
        newUnexists[unexists.length] = field;
        unexists = newUnexists;
        return this;
    }


    public String[] getExists() {
        return exists;
    }

    public void setExists(String[] exists) {
        this.exists = exists;
    }

    public String[] getUnexists() {
        return unexists;
    }

    public void setUnexists(String[] unexists) {
        this.unexists = unexists;
    }

    public Terms getTerms() {
        return terms;
    }

    public void setTerms(Terms terms) {
        this.terms = terms;
    }

    public Part getPart() {
        return part;
    }

    public void setPart(Part part) {
        this.part = part;
    }

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    public Range getRange() {
        return range;
    }

    public void setRange(Range range) {
        this.range = range;
    }

    public Equals getEquals() {
        return equals;
    }

    public void setEquals(Equals equals) {
        this.equals = equals;
    }

    public void promoteInput(String name, BufferedReader br) {
        while (true) {
            Menu menu = new Menu();
            menu.setName("Adding Query");
            menu.add(QUERY_FIELDS);
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> inputTerms(br);
                case 2 -> inputPart(br);
                case 3 -> inputMatch(br);
                case 4 -> inputRange(br);
                case 5 -> inputEquals(br);
                case 6 -> inputExists(br);
                case 7 -> inputUnexists(br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private void inputTerms(BufferedReader br) {
        String[] fields = Inputer.inputStringArray(br, "Input the fields. Enter to end:", 0);
        String[] values = Inputer.inputStringArray(br, "Input the values. Enter to end:", 0);
        if (fields.length > 0 && values.length > 0) {
            terms = new Terms();
            terms.setFields(fields);
            terms.setValues(values);
        }
    }

    private void inputPart(BufferedReader br) {
        String[] fields = Inputer.inputStringArray(br, "Input the fields. Enter to end:", 0);
        System.out.println("Input the value. Enter to exit:");
        String value = Inputer.inputString(br);
        if (fields.length == 0 || "".equals(value)) return;
        part = new Part();
        part.setFields(fields);
        part.setValue(value);
    }

    private void inputMatch(BufferedReader br) {
        String[] fields = Inputer.inputStringArray(br, "Input the fields. Enter to end:", 0);
        System.out.println("Input the value. Enter to exit:");
        String value = Inputer.inputString(br);
        if (fields.length == 0 || "".equals(value)) return;
        match = new Match();
        match.setFields(fields);
        match.setValue(value);
    }

    private void inputRange(BufferedReader br) {
        String[] fields = Inputer.inputStringArray(br, "Input the fields. Enter to end:", 0);
        System.out.println("Input the value. Enter to exit:");
        range = new Range();
        range.setFields(fields);

        while (true) {
            Menu menu = new Menu();
            menu.add("gt", "lt", "gte", "lte");
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> {
                    String numStr = Inputer.inputIntegerStr(br, "Input a integer. Enter to skip:");
                    if ("".equals(numStr)) break;
                    range.setGt(numStr);
                }
                case 2 -> {
                    String numStr = Inputer.inputIntegerStr(br, "Input a integer. Enter to skip:");
                    if ("".equals(numStr)) break;
                    range.setLt(numStr);
                }
                case 3 -> {
                    String numStr = Inputer.inputIntegerStr(br, "Input a integer. Enter to skip:");
                    if ("".equals(numStr)) break;
                    range.setGte(numStr);
                }
                case 4 -> {
                    String numStr = Inputer.inputIntegerStr(br, "Input a integer. Enter to skip:");
                    if ("".equals(numStr)) break;
                    range.setLte(numStr);
                }
                case 0 -> {
                    return;
                }
            }
        }
    }

    private void inputEquals(BufferedReader br) {
        String[] fields = Inputer.inputStringArray(br, "Input the fields. Enter to end:", 0);
        String[] values = Inputer.inputStringArray(br, "Input the values. Enter to end:", 0);
        if (fields.length > 0 && values.length > 0) {
            equals = new Equals();
            equals.setFields(fields);
            equals.setValues(values);
        }
    }

    private void inputExists(BufferedReader br) {
        exists = Inputer.inputStringArray(br, "Input exists fields. Enter to end:", 0);
    }

    private void inputUnexists(BufferedReader br) {
        unexists = Inputer.inputStringArray(br, "Input unexists fields. Enter to end:", 0);
    }
}
