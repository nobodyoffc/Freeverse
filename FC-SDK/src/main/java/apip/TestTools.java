package apip;

import apip.apipData.*;
import crypto.Hash;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import constants.ApiNames;
import appTools.Inputer;
import appTools.Menu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestTools {
    private static final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    private static String urlHead = null;
    private static String via = null;
    private static String result;

    public static void main(String[] args) {

        inputUrlHead();
        inputVia();
        String symKey = Inputer.inputString(br);

        while (true) {
            Menu menu = new Menu();

            ArrayList<String> menuItemList = new ArrayList<>();

            menuItemList.add("MatchAll");
            menuItemList.add("ByIds");
            menuItemList.add("Query Terms");
            menuItemList.add("Query Match");
            menuItemList.add("Query part");
            menuItemList.add("Query range");
            menuItemList.add("Query Exists");
            menuItemList.add("Query Unexists");
            menuItemList.add("Query Equals");
            menuItemList.add("All manual");


            menu.add(menuItemList);
            System.out.println(" << Apip request maker>> \n");
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> makeMatchAll();
                case 2 -> makeByIds();
                case 3 -> makeTerms();
                case 4 -> makeMatch();
                case 5 -> makePart();
                case 6 -> makeRange();
                case 7 -> makeExists();
                case 8 -> makeUnexists();
                case 9 -> makeEquals();
                case 10 -> allManual();
                case 0 -> {
                    try {
                        br.close();
                        return;
                    } catch (IOException e) {
                        System.out.println("Close BufferReader wrong.");
                        return;
                    }
                }
                default -> {
                }
            }
            String sign = Hash.getSign(symKey, result);
            System.out.println("-----\n Sign: \n" + sign + "\n----");
        }
    }

    private static String inputSymKey() {
        String ask = "Input the sessionKey:";
        return Arrays.toString(Inputer.input32BytesKey(br, ask));
    }

    private static void makeEquals() {
        RequestBody dataRequestBody = new RequestBody();
        FcQuery fcQuery = makeEmptyQury(dataRequestBody);
        String urlTail = setUrlAndVia(dataRequestBody);
        if (urlTail == null) return;
        fcQuery.setEquals(inputEquals());
        askSizeSortAfterThenPrint(dataRequestBody, fcQuery);
    }

    private static void makeUnexists() {
        RequestBody dataRequestBody = new RequestBody();
        FcQuery fcQuery = makeEmptyQury(dataRequestBody);
        String urlTail = setUrlAndVia(dataRequestBody);
        if (urlTail == null) return;
        fcQuery.setUnexists(inputUnexists());
        askSizeSortAfterThenPrint(dataRequestBody, fcQuery);
    }

    private static FcQuery makeEmptyQury(RequestBody dataRequestBody) {
        Fcdsl fcdsl = new Fcdsl();
        FcQuery fcQuery = new FcQuery();
        fcdsl.setQuery(fcQuery);
        dataRequestBody.setFcdsl(fcdsl);
        return fcQuery;
    }

    private static void askSizeSortAfterThenPrint(RequestBody dataRequestBody, FcQuery fcQuery) {
        dataRequestBody.getFcdsl().setQuery(fcQuery);

        askSize(dataRequestBody);
        askSort(dataRequestBody);
        askAfter(dataRequestBody);

        GsonBuilder gb = new GsonBuilder();
        Gson gson = gb.setPrettyPrinting().create();
        result = gson.toJson(dataRequestBody);
        System.out.println(result);
    }

    private static void makeExists() {
        RequestBody dataRequestBody = new RequestBody();
        FcQuery fcQuery = makeEmptyQury(dataRequestBody);

        String urlTail = setUrlAndVia(dataRequestBody);
        if (urlTail == null) return;

        fcQuery.setExists(inputExists());
        askSizeSortAfterThenPrint(dataRequestBody, fcQuery);
    }

    private static void makePart() {
        RequestBody dataRequestBody = new RequestBody();
        FcQuery fcQuery = makeEmptyQury(dataRequestBody);

        String urlTail = setUrlAndVia(dataRequestBody);
        if (urlTail == null) return;

        fcQuery.setPart(inputPart());
        askSizeSortAfterThenPrint(dataRequestBody, fcQuery);
    }

    private static void makeRange() {
        RequestBody dataRequestBody = new RequestBody();
        FcQuery fcQuery = makeEmptyQury(dataRequestBody);

        String urlTail = setUrlAndVia(dataRequestBody);
        if (urlTail == null) return;

        fcQuery.setRange(inputRange());
        askSizeSortAfterThenPrint(dataRequestBody, fcQuery);
    }

    private static void makeMatch() {
        RequestBody dataRequestBody = new RequestBody();
        FcQuery fcQuery = makeEmptyQury(dataRequestBody);

        String urlTail = setUrlAndVia(dataRequestBody);
        if (urlTail == null) return;

        fcQuery.setMatch(inputMatch());
        askSizeSortAfterThenPrint(dataRequestBody, fcQuery);
    }

    private static void makeTerms() {

        RequestBody dataRequestBody = new RequestBody();
        FcQuery fcQuery = makeEmptyQury(dataRequestBody);

        String urlTail = setUrlAndVia(dataRequestBody);
        if (urlTail == null) return;

        fcQuery.setTerms(inputTerms());
        askSizeSortAfterThenPrint(dataRequestBody, fcQuery);
    }

    private static void makeByIds() {
        RequestBody dataRequestBody = new RequestBody();

        String urlTail = setUrlAndVia(dataRequestBody);
        if (urlTail == null) return;
        Fcdsl fcdsl = new Fcdsl();
        dataRequestBody.setFcdsl(fcdsl);

        String[] ids = Inputer.inputStringArray(br, "Input the IDs.", 0);
        dataRequestBody.getFcdsl().setIds(List.of(ids));

        GsonBuilder gb = new GsonBuilder();
        Gson gson = gb.setPrettyPrinting().create();
        System.out.println(gson.toJson(dataRequestBody));
    }

    private static void makeMatchAll() {
        RequestBody dataRequestBody = new RequestBody();
        Fcdsl fcdsl = new Fcdsl();
        dataRequestBody.setFcdsl(fcdsl);

        String urlTail = setUrlAndVia(dataRequestBody);
        if (urlTail == null) return;

        askSize(dataRequestBody);
        askSort(dataRequestBody);
        askAfter(dataRequestBody);

        GsonBuilder gb = new GsonBuilder();
        Gson gson = gb.setPrettyPrinting().create();
        result = gson.toJson(dataRequestBody);
        System.out.println(result);
    }

    private static String setUrlAndVia(RequestBody dataRequestBody) {
        String urlTail = inputUrlTail();
        if (urlTail == null) return null;
        dataRequestBody.makeRequestBody(urlHead + urlTail, via);
        return urlTail;
    }

    private static void inputVia() {
        String input;

        System.out.println("Input via:");
        input = Inputer.inputString(br);
        if (!"".equals(input)) via = input;
    }

    private static void inputUrlHead() {
        String input = null;

        System.out.println("Input urlHead:");
        input = Inputer.inputString(br);
        if (!"".equals(input)) urlHead = input;
    }

    private static void allManual() {
        RequestBody dataRequestBody = new RequestBody();

        String urlTail = setUrlAndVia(dataRequestBody);
        if (urlTail == null) return;
        Fcdsl fcdsl = new Fcdsl();
        dataRequestBody.setFcdsl(fcdsl);
        if (urlTail.equals(ApiNames.APIP1V1Path + ApiNames.General)) askIndex(dataRequestBody);
        if (askByIds(dataRequestBody)) return;
        if (!askMatchAll()) {
            askQuery(dataRequestBody);
            askFilter(dataRequestBody);
            askExcept(dataRequestBody);
        }
        askSize(dataRequestBody);
        askSort(dataRequestBody);
        askAfter(dataRequestBody);

        GsonBuilder gb = new GsonBuilder();
        Gson gson = gb.setPrettyPrinting().create();
        System.out.println(gson.toJson(dataRequestBody));
    }

    private static boolean askAfter(RequestBody dataRequestBody) {
        while (true) {
            System.out.println("Add After? 'y' or 'n':");
            String input = Inputer.inputString(br);
            if ("y".equals(input)) {
                inputAfter(dataRequestBody);
                return true;
            } else if ("n".equals(input)) {
                return false;
            }
        }
    }

    private static void inputAfter(RequestBody dataRequestBody) {
        String[] after;
        Gson gson = new Gson();
        try {
            while (true) {
                System.out.println("Input the json string of the last:");
                String input = br.readLine();
                try {
                    after = gson.fromJson(input, String[].class);
                    dataRequestBody.getFcdsl().setAfter(Arrays.stream(after).toList());
                    return;
                } catch (Exception e) {
                    System.out.println("Wrong input. A string array json is needed. Try again.");
                }
            }
        } catch (IOException e) {
            System.out.println("BufferReader wrong.");
        }
    }

    private static boolean askSort(RequestBody dataRequestBody) {
        while (true) {
            System.out.println("Add Sort? 'y' or 'n':");
            String input = Inputer.inputString(br);
            if ("y".equals(input)) {
                inputSort(dataRequestBody);
                return true;
            } else if ("n".equals(input)) {
                return false;
            }
        }
    }

    private static void inputSort(RequestBody dataRequestBody) {
        ArrayList<Sort> sortList = Sort.inputSortList(br);
        dataRequestBody.getFcdsl().setSort(sortList);
    }

    private static boolean askSize(RequestBody dataRequestBody) {
        while (true) {
            System.out.println("Add Size? 'y' or 'n':");
            String input = Inputer.inputString(br);
            if ("y".equals(input)) {
                inputSize(dataRequestBody);
                return true;
            } else if ("n".equals(input)) {
                return false;
            }
        }
    }

    private static void inputSize(RequestBody dataRequestBody) {
        String ask = "Input the size you want in one request:";
        String size = Inputer.inputIntegerStr(br, ask);
        dataRequestBody.getFcdsl().setSize(size);
    }

    private static boolean askMatchAll() {
        while (true) {
            System.out.println("Is MatchAll? 'y' or 'n':");
            String input = Inputer.inputString(br);
            if ("y".equals(input)) {
                return true;
            } else if ("n".equals(input)) {
                return false;
            }
        }
    }

    private static boolean askByIds(RequestBody dataRequestBody) {
        while (true) {
            System.out.println("Is ByIds? 'y' or 'n':");
            String input = Inputer.inputString(br);
            if ("y".equals(input)) {
                String[] ids = Inputer.inputStringArray(br, "Input the IDs.", 0);
                dataRequestBody.getFcdsl().setIds(List.of(ids));
                return true;
            } else if ("n".equals(input)) {
                return false;
            }
        }
    }

    private static void askIndex(RequestBody dataRequestBody) {
        System.out.println("General request need the name of index. Input it:");
        String index = Inputer.inputString(br);
        dataRequestBody.getFcdsl().setIndex(index);
    }

    private static void askQuery(RequestBody dataRequestBody) {
        while (true) {
            System.out.println("Is ByIds? 'y' or 'n':");
            String input = Inputer.inputString(br);
            if ("y".equals(input)) {
                setQuery(dataRequestBody);
                return;
            } else if ("n".equals(input)) {
                return;
            }
        }
    }

    private static void askFilter(RequestBody dataRequestBody) {
        while (true) {
            System.out.println("Is Filter? 'y' or 'n':");
            String input = Inputer.inputString(br);
            if ("y".equals(input)) {
                setFilter(dataRequestBody);
                return;
            } else if ("n".equals(input)) {
                return;
            }
        }
    }

    private static void askExcept(RequestBody dataRequestBody) {
        while (true) {
            System.out.println("Is Except? 'y' or 'n':");
            String input = Inputer.inputString(br);
            if ("y".equals(input)) {
                setExcept(dataRequestBody);
                return;
            } else if ("n".equals(input)) {
                return;
            }
        }
    }


    private static void setExcept(RequestBody dataRequestBody) {
        Except filter = new Except();
        dataRequestBody.getFcdsl().setQuery(filter);

        Menu menu = new Menu();

        ArrayList<String> menuItemList = new ArrayList<>();
        menuItemList.add("Terms");
        menuItemList.add("Match");
        menuItemList.add("Part");
        menuItemList.add("Range");
        menuItemList.add("Exists");
        menuItemList.add("Unexists");
        menuItemList.add("Equals");

        menu.add(menuItemList);
        System.out.println("Choice method in your Except:");
        menu.show();
        int choice = menu.choose(br);
        switch (choice) {
            case 1 -> dataRequestBody.getFcdsl().getExcept().setTerms(inputTerms());
            case 2 -> dataRequestBody.getFcdsl().getExcept().setMatch(inputMatch());
            case 3 -> dataRequestBody.getFcdsl().getExcept().setPart(inputPart());
            case 4 -> dataRequestBody.getFcdsl().getExcept().setRange(inputRange());
            case 5 -> dataRequestBody.getFcdsl().getExcept().setExists(inputExists());
            case 6 -> dataRequestBody.getFcdsl().getExcept().setUnexists(inputUnexists());
            case 7 -> dataRequestBody.getFcdsl().getExcept().setEquals(inputEquals());
            default -> {
            }
        }
    }

    private static void setFilter(RequestBody dataRequestBody) {
        Filter filter = new Filter();
        dataRequestBody.getFcdsl().setFilter(filter);

        Menu menu = new Menu();

        ArrayList<String> menuItemList = new ArrayList<>();

        menuItemList.add("Terms");
        menuItemList.add("Match");
        menuItemList.add("Part");
        menuItemList.add("Range");
        menuItemList.add("Exists");
        menuItemList.add("Unexists");
        menuItemList.add("Equals");

        menu.add(menuItemList);
        System.out.println("Choice method in your Filter:");
        menu.show();
        int choice = menu.choose(br);
        switch (choice) {
            case 1 -> dataRequestBody.getFcdsl().getFilter().setTerms(inputTerms());
            case 2 -> dataRequestBody.getFcdsl().getFilter().setMatch(inputMatch());
            case 3 -> dataRequestBody.getFcdsl().getFilter().setPart(inputPart());
            case 4 -> dataRequestBody.getFcdsl().getFilter().setRange(inputRange());
            case 5 -> dataRequestBody.getFcdsl().getFilter().setExists(inputExists());
            case 6 -> dataRequestBody.getFcdsl().getFilter().setUnexists(inputUnexists());
            case 7 -> dataRequestBody.getFcdsl().getFilter().setEquals(inputEquals());
            default -> {
            }
        }
    }

    private static void setQuery(RequestBody dataRequestBody) {
        FcQuery fcQuery = new FcQuery();
        dataRequestBody.getFcdsl().setQuery(fcQuery);

        Menu menu = new Menu();

        ArrayList<String> menuItemList = new ArrayList<>();

        menuItemList.add("Terms");
        menuItemList.add("Match");
        menuItemList.add("Part");
        menuItemList.add("Range");
        menuItemList.add("Exists");
        menuItemList.add("Unexists");
        menuItemList.add("Equals");

        menu.add(menuItemList);
        System.out.println("Choice method in your Query:");
        menu.show();
        int choice = menu.choose(br);
        switch (choice) {
            case 1 -> dataRequestBody.getFcdsl().getQuery().setTerms(inputTerms());
            case 2 -> dataRequestBody.getFcdsl().getQuery().setMatch(inputMatch());
            case 3 -> dataRequestBody.getFcdsl().getQuery().setPart(inputPart());
            case 4 -> dataRequestBody.getFcdsl().getQuery().setRange(inputRange());
            case 5 -> dataRequestBody.getFcdsl().getQuery().setExists(inputExists());
            case 6 -> dataRequestBody.getFcdsl().getQuery().setUnexists(inputUnexists());
            case 7 -> dataRequestBody.getFcdsl().getQuery().setEquals(inputEquals());
            default -> {
            }
        }
    }

    private static Equals inputEquals() {
        Equals equals = new Equals();
        String name = "equals";
        equals.setFields(inputFeilds(name));
        equals.setValues(inputValues(name));
        return equals;
    }

    private static String[] inputUnexists() {
        String ask = "Input the field:";
        return Inputer.inputStringArray(br, ask, 0);
    }

    private static String[] inputExists() {
        String ask = "Input the field:";
        return Inputer.inputStringArray(br, ask, 0);
    }

    private static Match inputMatch() {
        Match match = new Match();
        String name = "match";
        match.setFields(inputFeilds(name));
        match.setValue(inputValue(name));
        return match;
    }

    private static Part inputPart() {
        Part part = new Part();
        String name = "part";
        part.setFields(inputFeilds(name));
        part.setValue(inputValue(name));
        return part;
    }

    private static Range inputRange() {
        Range range = new Range();
        List<String> con = new ArrayList<>();
        con.add("lt");
        con.add("lte");
        con.add("gt");
        con.add("gte");
        String name = "range";
        range.setFields(inputFeilds(name));
        while (true) {
            System.out.println("Input the condition (lt, lte, gt, or gte), enter to finish:");
            String input = Inputer.inputString(br);
            if ("".equals(input)) {
                return range;
            }
            String ask = "Input the value:";
            String num = Inputer.inputDoubleAsString(br, ask);
            switch (input) {
                case "lt" -> range.setLt(num);
                case "lte" -> range.setLte(num);
                case "gt" -> range.setGt(num);
                case "Gte" -> range.setGte(num);
                default -> {
                    return range;
                }
            }
        }
    }

    private static Terms inputTerms() {
        Terms terms = new Terms();
        String name = "terms";
        terms.setFields(inputFeilds(name));
        terms.setValues(inputValues(name));
        return terms;
    }

    private static String[] inputValues(String name) {
        String ask = "Input the values of " + name + ": ";
        return Inputer.inputStringArray(br, ask, 0);
    }

    private static String inputValue(String name) {
        System.out.println("Input the values of " + name + ": ");
        return Inputer.inputString(br);
    }

    private static String[] inputFeilds(String name) {
        String ask = "Input the fields of " + name + ": ";
        return Inputer.inputStringArray(br, ask, 0);
    }

    private static String inputUrlTail() {
        String urlTail = null;
        while (urlTail == null || "".equals(urlTail)) {
            System.out.println("Input urlTail:");
            try {
                urlTail = br.readLine();
            } catch (IOException e) {
                System.out.println("BufferReader wrong.");
                return null;
            }
        }
        return urlTail;
    }
}
