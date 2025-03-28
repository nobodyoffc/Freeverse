package apip.apipData;

import appTools.Inputer;
import appTools.Menu;
import utils.JsonUtils;
import utils.StringUtils;
import utils.http.HttpUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.*;

import static apip.apipData.FcQuery.*;
import static apip.apipData.Range.*;
import static constants.FieldNames.INDEX;
import static constants.Values.ASC;
import static constants.Values.DESC;

public class Fcdsl {
    private static final Logger log = LoggerFactory.getLogger(Fcdsl.class);
    private String index;
    private List<String> ids;
    private FcQuery query;
    private Filter filter;
    private Except except;
    private String size;
    private List<Sort> sort;
    private List<String> after;
    private Map<String,String> other;

    public static final String MATCH_ALL = "matchAll";
    public static final String IDS = "ids";
    public static final String QUERY = "query";
    public static final String FILTER = "filter";
    public static final String EXCEPT = "except";
    public static final String SIZE = "size";
    public static final String SORT = "sort";
    public static final String AFTER = "after";
    public static final String OTHER = "other";
    public static final String[] FCDSL_FIELDS = new String[]{MATCH_ALL, IDS, QUERY, FILTER, EXCEPT, SIZE, SORT, AFTER, OTHER};
    /*
   Fcdsl to GET url parameters:

   index = <String indexName>
   ids = <String[]>
   terms = field,value1,value2... & match = field1,field2,...,value
   range = field,lte,value1,gt,value2...
   exists = field1,field2,...
   unexists = field1,field2,...
   equals = field,value1,value2,...
   part = field1,field2,...,value
   sort = field1,order1,field2,order2
   size = <int in String>
   after = <List<String>>
   other = String

   Filter and Except is forbidden.
    */
    public static Fcdsl urlParamsToFcdsl(String urlParams){
        if("".equals(urlParams))return null;
        Fcdsl fcdsl = new Fcdsl();
        int i = urlParams.indexOf("?");
        if(i!=-1) urlParams = urlParams.substring(i +1);

        urlParams = urlParams.replaceAll(" ", "");
        String[] params = urlParams.split("&");
        Map<String,String> otherMap = new HashMap<>();
        for(String param : params){
            int splitIndex = param.indexOf("=");
            String method = param.substring(0, splitIndex);
            String valueStr = param.substring(splitIndex+1);
            if("".equals(method)||"".equals(valueStr)) {
                System.out.println("Bad url.");
                return null;
            }
            switch (method){
                case INDEX -> fcdsl.addIndex(valueStr);
                case IDS-> fcdsl.addIds(valueStr.split(","));
                case TERMS-> {
                    if(fcdsl.getQuery()==null)fcdsl.addNewQuery();
                    Terms terms = new Terms();

                    urlParamsToTerms(valueStr, terms);

                    fcdsl.getQuery().setTerms(terms);
                }

                case FILTER-> {
                    if(fcdsl.getFilter()==null)fcdsl.addNewFilter();
                    Terms terms = new Terms();

                    urlParamsToTerms(valueStr, terms);

                    fcdsl.getFilter().setTerms(terms);
                }

                case EXCEPT-> {
                    if(fcdsl.getExcept()==null)fcdsl.addNewExcept();
                    Terms terms = new Terms();

                    urlParamsToTerms(valueStr, terms);

                    fcdsl.getExcept().setTerms(terms);
                }
                
                case MATCH-> {
                    if(fcdsl.getQuery()==null)fcdsl.addNewQuery();
                    String[] values = valueStr.split(",");
                    String[] fields = new String[values.length-1];
                    System.arraycopy(values, 0, fields, 0, values.length - 1);
                    fcdsl.getQuery().addNewMatch().addNewFields(fields);
                    fcdsl.getQuery().getMatch().addNewValue(values[values.length-1]);
                }
                case RANGE-> {
                    if(fcdsl.getQuery()==null)fcdsl.addNewQuery();
                    String[] values = valueStr.split(",");
                    fcdsl.getQuery().addNewRange().addNewFields(values[0]);
                    String[] newValues = new String[values.length-1];
                    System.arraycopy(values, 1, newValues, 0, values.length - 1);

                    Iterator<String> iter = Arrays.stream(newValues).iterator();
                    while(iter.hasNext()){
                        String str = iter.next();
                        switch (str){
                            case GT-> fcdsl.getQuery().getRange().addGt(iter.next());
                            case GTE-> fcdsl.getQuery().getRange().addGte(iter.next());
                            case LT-> fcdsl.getQuery().getRange().addLt(iter.next());
                            case LTE-> fcdsl.getQuery().getRange().addLte(iter.next());
                        }
                    }
                }
                case PART->  {
                    if(fcdsl.getQuery()==null)fcdsl.addNewQuery();
                    String[] values = valueStr.split(",");
                    String[] fields = new String[values.length-1];
                    System.arraycopy(values, 0, fields, 0, values.length - 1);
                    fcdsl.getQuery().addNewPart().addNewFields(fields);
                    fcdsl.getQuery().getPart().addNewValue(values[values.length-1]);
                }
                case EXISTS-> {
                    if(fcdsl.getQuery()==null)fcdsl.addNewQuery();
                    String[] values = valueStr.split(",");
                    fcdsl.getQuery().addNewExists(values);
                }
                case UNEXISTS->  {
                    if(fcdsl.getQuery()==null)fcdsl.addNewQuery();
                    String[] values = valueStr.split(",");
                    fcdsl.getQuery().addNewUnexists(values);
                }
                case EQUALS-> {
                    if(fcdsl.getQuery()==null)fcdsl.addNewQuery();
                    String[] values = valueStr.split(",");
                    fcdsl.getQuery().addNewEquals().addNewFields(values[0]);
                    String[] newValues = new String[values.length-1];
                    System.arraycopy(values, 1, newValues, 0, values.length - 1);
                    fcdsl.getQuery().getEquals().addNewValues(newValues);
                }
                case SORT-> {
                    String[] values = valueStr.split(",");
                    Iterator<String> iter = Arrays.stream(values).iterator();
                    while(iter.hasNext()){
                        String field = iter.next();
                        String order = iter.next();
                        if(!order.equals(DESC) && !order.equals(ASC)){
                            System.out.println("Wrong order. It should be 'desc' or 'asc'.");
                            return null;
                        }
                        fcdsl.addSort(field,order);
                    }
                }
                case SIZE-> fcdsl.addSize(Integer.parseInt(valueStr));
                case AFTER-> fcdsl.addAfter(List.of(valueStr.split(",")));
//                case OTHER-> fcdsl.addOther(valueStr);
                default -> otherMap.put(method,valueStr);
            }
        }
        if(fcdsl.getOther()==null && !otherMap.isEmpty())fcdsl.setOther(otherMap);
        return fcdsl;
    }

    private static void urlParamsToTerms(String valueStr, Terms terms) {
        String[] values = valueStr.split(",");
        int fieldNum = Integer.parseInt(values[0]);
        String[] fields = new String[fieldNum];
        System.arraycopy(values, 1, fields, 0, fieldNum);
        terms.addNewFields(fields);
        String[] newValues = new String[values.length-1-fieldNum];
        System.arraycopy(values, 1+fieldNum, newValues, 0, values.length - 1 - fieldNum);
        terms.addNewValues(newValues);
    }

    public static Map<String,String> urlParamsStrToMap(String paramsStr){
        if("".equals(paramsStr))return null;
        Map<String,String> paramMap=new HashMap<>();
        if(paramsStr.startsWith("?"))paramsStr = paramsStr.substring(1);
        paramsStr = paramsStr.replaceAll(" ", "");
        String[] params = paramsStr.split("&");
        for(String param : params){
            int splitIndex = param.indexOf("=");
            String key = param.substring(0, splitIndex);
            String valueStr = param.substring(splitIndex+1);
            paramMap.put(key,valueStr);
        }
        return paramMap;
    }
    public static String fcdslToUrlParams(Fcdsl fcdsl){
        if(fcdsl.isBadFcdsl()){
            System.out.println("Bad fcdsl.");
            return null;
        }

        boolean started = false;
        StringBuilder stringBuilder = new StringBuilder();

        if(fcdsl.getIndex()!=null) {
            stringBuilder.append(INDEX + "=").append(fcdsl.getIndex());
            started = true;
        }

        if(fcdsl.getIds()!=null) {
            if(started)stringBuilder.append("&");
            String ids = StringUtils.listToString(fcdsl.getIds());
            stringBuilder.append(IDS + "=").append(ids);
            started = true;
        }

        if(fcdsl.getQuery()!=null){
            FcQuery query = fcdsl.getQuery();

            if(query.getTerms()!=null){
                Terms terms = query.getTerms();
                started = termsToUrlParams(Terms.termsToUrlParam(terms), started, stringBuilder, TERMS);
            }

            if(query.getMatch()!=null){

                Match match = query.getMatch();
                started = matchToUrlParams(started, stringBuilder, match);
            }

            if(query.getRange()!=null){

                Range range = query.getRange();
                started = rangeToUrlParams(started, stringBuilder, range);
            }

            if(query.getPart()!=null){

                Part part = query.getPart();
                started = partToUrlParams(started, stringBuilder, part);
            }

            if(query.getExists()!=null){
                if(started)stringBuilder.append("&");
                String[] exists = query.getExists();
                stringBuilder.append(EXISTS + "=").append(StringUtils.arrayToString(exists));
                started = true;
            }

            if(query.getUnexists()!=null){
                if(started)stringBuilder.append("&");
                String[] unexists = query.getUnexists();
                stringBuilder.append(UNEXISTS + "=").append(StringUtils.arrayToString(unexists));
                started = true;
            }

            if(query.getEquals()!=null){
                Equals equals = query.getEquals();
                started = equalsToUrlParams(started, stringBuilder, equals);
            }
        }

        if(fcdsl.getFilter()!=null){
            Filter filter = fcdsl.getFilter();
            if(filter.getTerms()!=null){
                Terms terms = filter.getTerms();
                started = termsToUrlParams(Terms.termsToUrlParam(terms), started, stringBuilder, FILTER);
            }else System.out.println("For Filter, only terms can be convert into URL.");
        }

        if(fcdsl.getExcept()!=null){
            Except except = fcdsl.getExcept();

            if(except.getTerms()!=null){
                Terms terms = except.getTerms();
                started = termsToUrlParams(Terms.termsToUrlParam(terms), started, stringBuilder, EXCEPT);
            }else System.out.println("For Except, only terms can be convert into URL.");
        }

        if(fcdsl.getSort()!=null && fcdsl.getSort().size()>0){
            List<Sort> sortList = fcdsl.getSort();
            List<String> sortStrList = new ArrayList<>();
            for(Sort sort1:sortList){
                sortStrList.add(sort1.getField());
                sortStrList.add(sort1.getOrder());
            }
            if(started)stringBuilder.append("&");
            stringBuilder.append(SORT + "=").append(StringUtils.listToString(sortStrList));
            started=true;
        }

        if(fcdsl.getSize()!=null){
            if(started)stringBuilder.append("&");
            stringBuilder.append(SIZE + "=").append(fcdsl.getSize());
            started=true;
        }
        if(fcdsl.getAfter()!=null){
            if(started)stringBuilder.append("&");
            stringBuilder.append(AFTER + "=").append(StringUtils.listToString(fcdsl.getAfter()));
            started=true;
        }

        if(fcdsl.getOther()!=null){
            if(started)stringBuilder.append("&");
            String otherStr;
            try {
                otherStr = HttpUtils.makeUrlParamsString(fcdsl.getOther());
                otherStr = otherStr.substring(1);
                stringBuilder.append(otherStr);
            }catch (Exception e){
                otherStr = String.valueOf(fcdsl.getOther());
                stringBuilder.append(OTHER + "=").append(otherStr);
            }
        }
        return stringBuilder.toString();
    }

    private static boolean equalsToUrlParams(boolean started, StringBuilder stringBuilder, Equals equals) {
        String equalsStr = Equals.equalsToUrlParam(equals);
        if (equalsStr != null){
            if(started) stringBuilder.append("&");
            stringBuilder.append(EQUALS + "=").append(equalsStr);
            started = true;
        }
        return started;
    }

    private static boolean partToUrlParams(boolean started, StringBuilder stringBuilder, Part part) {
        String partStr = Part.partToUrlParam(part);
        if (partStr != null) {
            if(started) stringBuilder.append("&");
            stringBuilder.append(PART + "=").append(partStr);
            started = true;
        }
        return started;
    }

    private static boolean rangeToUrlParams(boolean started, StringBuilder stringBuilder, Range range) {
        String rangeStr = rangeToUrlParam(range);
        if (rangeStr != null) {
            if(started) stringBuilder.append("&");
            stringBuilder.append(RANGE + "=").append(rangeStr);
            started = true;
        }
        return started;
    }

    private static boolean matchToUrlParams(boolean started, StringBuilder stringBuilder, Match match) {
        String matchStr = Match.matchToUrlParam(match);
        if (matchStr != null) {
            if(started) stringBuilder.append("&");
            stringBuilder.append(MATCH + "=").append(matchStr);
            started = true;
        }
        return started;
    }

    private static boolean termsToUrlParams(String terms, boolean started, StringBuilder stringBuilder, String terms1) {
        if (terms != null) {
            if (started) stringBuilder.append("&");
            stringBuilder.append(terms1 + "=").append(terms);
            started = true;
        }
        return started;
    }

    public static boolean askIfAdd(String fieldName, BufferedReader br) {
            System.out.println("Add " + fieldName + " ? y /others:");
            String input = Inputer.inputString(br);
        return "y".equals(input);
    }

    public static Fcdsl addFilterTermsToFcdsl(RequestBody requestBody, String field, String value) {
        Fcdsl fcdsl;
        if (requestBody.getFcdsl() != null) {
            fcdsl = requestBody.getFcdsl();
        } else fcdsl = new Fcdsl();

        Filter filter;
        if (fcdsl.getFilter() != null) {
            filter = fcdsl.getFilter();
        } else filter = new Filter();

        Terms terms;
        if (filter.getTerms() != null) {
            terms = filter.getTerms();
        } else terms = new Terms();

        terms.setFields(new String[]{field});
        terms.setValues(value);
        filter.setTerms(terms);
        fcdsl.setFilter(filter);
        return fcdsl;
    }

    public static Fcdsl addExceptTermsToFcdsl(RequestBody requestBody, String field, String value) {
        Fcdsl fcdsl;
        if (requestBody.getFcdsl() != null) {
            fcdsl = requestBody.getFcdsl();
        } else fcdsl = new Fcdsl();

        Except except;
        if (fcdsl.getExcept() != null) {
            except = fcdsl.getExcept();
        } else except = new Except();

        Terms terms;
        if (except.getTerms() != null) {
            terms = except.getTerms();
        } else terms = new Terms();

        terms.setFields(new String[]{field});
        terms.setValues(value);
        except.setTerms(terms);
        fcdsl.setExcept(except);
        return fcdsl;
    }

    @Nullable
    public static Fcdsl makeTermsFilter(Fcdsl fcdsl, String filterFiled, String filterValue) {
        if(fcdsl == null) fcdsl = new Fcdsl();

        if(fcdsl.getFilter()!=null){
            if(fcdsl.getFilter().getTerms()!=null){
                log.info("The fcdsl.filter.terms should be reserved. Clear it.");
                return null;
            }
            else fcdsl.getFilter().addNewTerms().addNewFields(filterFiled).addNewValues(filterValue);
        }else fcdsl.addNewFilter().addNewTerms().addNewFields(filterFiled).addNewValues(filterValue);
        return fcdsl;
    }

    @Nullable
    public static Fcdsl makeTermsExcept(Fcdsl fcdsl, String exceptFiled, String exceptValue) {
        if(fcdsl == null) fcdsl = new Fcdsl();

        if(fcdsl.getExcept()!=null){
            if(fcdsl.getExcept().getTerms()!=null){
                log.info("The fcdsl.except.terms should be reserved. Clear it.");
                return null;
            }
            else fcdsl.getExcept().addNewTerms().addNewFields(exceptFiled).addNewValues(exceptValue);
        }else fcdsl.addNewExcept().addNewTerms().addNewFields(exceptFiled).addNewValues(exceptValue);
        return fcdsl;
    }

    public static void setSingleOtherMap(Fcdsl fcdsl, String key, String value) {
        Map<String,String> otherMap = new HashMap<>();
        otherMap.put(key, value);
        fcdsl.setOther(otherMap);
    }

    public void promoteSearch(int defaultSize, String defaultSort, BufferedReader br) {
        if (askIfAdd(QUERY, br)) inputQuery(br);
        if (askIfAdd(FILTER, br)) inputFilter(br);
        if (askIfAdd(EXCEPT, br)) inputExcept(br);
        System.out.println("The default size is " + defaultSize + ".");
        if (askIfAdd(SIZE, br)) inputSize(br);
        System.out.println("The default sort is " + defaultSort + ".");
        if (askIfAdd(SORT, br)) inputSort(br);
        if (askIfAdd(AFTER, br)) inputAfter(br);
    }

    public boolean isBadFcdsl() {
        //1. ids 不可有query，filter，except，matchAll
        if (ids != null) {
            if (query != null) {
                System.out.println("With Ids search, there can't be a query.");
                return true;
            }
            if (filter != null) {
                System.out.println("With Ids search, there can't be a filter.");
                return true;
            }
            if (except != null) {
                System.out.println("With Ids search, there can't be an except.");
                return true;
            }
            if (after != null) {
                System.out.println("With Ids search, there can't be an after.");
                return true;
            }
            if (size != null) {
                System.out.println("With Ids search, there can't be a size.");
                return true;
            }
            if (sort != null) {
                System.out.println("With Ids search, there can't be a sort.");
                return true;
            }
        }

        //2. 没有query就不能有filter，except
        if (filter != null || except != null) {
            if (query == null) {
                System.out.println("Filter and except have to be used with a query.");
                return true;
            }
        }

        return false;
    }

    public void addIndex(String index) {
        this.index = index;
    }

    public void addIds(String... ids) {
        if(this.ids==null)this.ids = new ArrayList<>();
        this.ids.addAll(Arrays.asList(ids));
        return;
    }
    public void addIds(List<String> ids) {
        if(this.ids==null)this.ids = new ArrayList<>();
        this.ids.addAll(ids);
        return;
    }

    public FcQuery addNewQuery() {
        FcQuery fcQuery = new FcQuery();
        this.setQuery(fcQuery);
        return fcQuery;
    }

    public Filter addNewFilter() {
        Filter filter = new Filter();
        this.setFilter(filter);
        return filter;
    }

    public Except addNewExcept() {
        Except except = new Except();
        this.setExcept(except);
        return except;
    }

    public Fcdsl addSort(String field, String order) {
        if(this.sort==null)
            sort = new ArrayList<>();
        Sort s = new Sort(field, order);
        sort.add(s);
        this.setSort(sort);
        return this;
    }


    public Fcdsl addSize(int size) {
        this.size = String.valueOf(size);
        return this;
    }

    public Fcdsl addAfter(List<String> values) {
        this.after = new ArrayList<>();
        this.after.addAll(values);
        return this;
    }

    public Fcdsl addAfter(String value) {
        if(this.after==null)
            this.after = new ArrayList<>();
        this.after.add(value);
        return this;
    }


    public void setQueryTerms(String field, String value) {
        FcQuery fcQuery = new FcQuery();
        Terms terms;
        if (fcQuery.getTerms() != null) {
            terms = fcQuery.getTerms();
        } else terms = new Terms();

        terms.setFields(new String[]{field});
        terms.setValues(value);
        fcQuery.setTerms(terms);
        this.query = fcQuery;
    }

    public void setFilterTerms(String field, String value) {
        Filter filter = new Filter();
        Terms terms;
        if (filter.getTerms() != null) {
            terms = filter.getTerms();
        } else terms = new Terms();

        terms.setFields(new String[]{field});
        terms.setValues(value);
        filter.setTerms(terms);
        this.filter = filter;
    }

    public void setExceptTerms(String field, String value) {
        Except except1 = new Except();
        Terms terms;
        if (except1.getTerms() != null) {
            terms = except1.getTerms();
        } else terms = new Terms();

        terms.setFields(new String[]{field});
        terms.setValues(value);
        except1.setTerms(terms);
        this.except = except1;
    }

    public Map<String, String> getOther() {
        return other;
    }

    public void setOther(Map<String, String> other) {
        this.other = other;
    }

    public void addOther(Map<String, String> other) {
        this.other = other;
    }
    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public FcQuery getQuery() {
        return query;
    }

    public void setQuery(FcQuery fcQuery) {
        this.query = fcQuery;
    }

    public Filter getFilter() {
        return filter;
    }
    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public List<Sort> getSort() {
        return sort;
    }

    public void setSort(List<Sort> sort) {
        this.sort = sort;
    }

    public List<String> getAfter() {
        return after;
    }

    public void setAfter(List<String> after) {
        this.after = after;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public Except getExcept() {
        return except;
    }
    public void setExcept(Except except) {
        this.except = except;
    }

    @Test
    public void test() {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIndex("cid");
        fcdsl.setSize("2");
        JsonUtils.printJson(fcdsl);
    }


    public void promoteInput(BufferedReader br) {
        while (true) {
            Menu menu = new Menu();
            menu.setTitle("Input FCDSL");
            menu.add(FCDSL_FIELDS);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> inputMatchAll(br);
                case 2 -> inputIds(br);
                case 3 -> inputQuery(br);
                case 4 -> inputFilter(br);
                case 5 -> inputExcept(br);
                case 6 -> inputSize(br);
                case 7 -> inputSort(br);
                case 8 -> inputAfter(br);
                case 9 -> inputOther(br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private void inputOther(BufferedReader br) {
        System.out.println("Input a string or a json. Enter to exit:");
        other = Inputer.inputStringStringMap(br, "Input the key:", "Input the value:");
    }

    public void inputMatchAll(BufferedReader br) {
        while (true) {
            Menu menu = new Menu();
            menu.setTitle("Input Match All");
            menu.add(SIZE, SORT, AFTER);
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> inputSize(br);
                case 2 -> inputSort(br);
                case 3 -> inputAfter(br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public void inputAfter(BufferedReader br) {
        String[] inputs = Inputer.inputStringArray(br, "Input strings of after. Enter to end:", 0);
        if (inputs.length > 0) after = List.of(inputs);
    }

    public void inputSort(BufferedReader br) {
        ArrayList<Sort> sortList = Sort.inputSortList(br);
        if (sortList != null && sortList.size() > 0) sort = sortList;

    }

    public void inputSize(BufferedReader br) {
        String numStr = Inputer.inputIntegerStr(br, "Input size. Enter to skip:");
        if ("".equals(numStr)) return;
        size = numStr;
    }


    public void inputIds(BufferedReader br) {
        List<String> inputs = Inputer.inputStringList(br, "Input the ID. Enter to end:", 0);
        if (inputs.size() > 0) ids = inputs;
    }

    public void inputQuery(BufferedReader br) {
        query = new FcQuery();
        query.promoteInput(QUERY, br);
    }

    public void inputFilter(BufferedReader br) {
        filter = new Filter();
        filter.promoteInput(FILTER, br);
    }

    public void inputExcept(BufferedReader br) {
        except = new Except();
        except.promoteInput(EXCEPT, br);
    }
}
