package apip.apipData;

import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import constants.FieldNames;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static constants.Strings.*;

public class Sort {
    private String field;
    private String order = DESC;

    public Sort() {
    }

    public Sort(String field, String order) {
        if (order.equals(DESC) || order.equals(ASC)) {
            this.field = field;
            this.order = order;
        }
    }

    public static List<SortOptions> makeHeightTxIndexSort() {
        return makeTwoFieldsSort(HEIGHT, DESC, FieldNames.TX_INDEX, DESC);
    }

    public static List<SortOptions> makeTwoFieldsSort(String field1, String order1, String field2, String order2) {
        ArrayList<Sort> sortList = new ArrayList<>();

        Sort sort1 = new Sort();
        sort1.setField(field1);
        sort1.setOrder(order1);
        sortList.add(sort1);

        Sort sort2 = new Sort();
        sort2.setField(field2);
        sort2.setOrder(order2);
        sortList.add(sort2);

        return getSortList(sortList);
    }

    public static ArrayList<Sort> inputSortList(BufferedReader br) {
        ArrayList<Sort> sortList = new ArrayList<>();
        String input;
        try {
            while (true) {
                Sort sort = new Sort();
                System.out.println("Input the field name. 'q' to finish: ");
                input = br.readLine();
                if ("q".equals(input)) break;
                sort.setField(input);

                while (true) {
                    System.out.println("Input the order. " + DESC + " or " + ASC + ": ");
                    input = br.readLine();
                    if ("q".equals(input)) break;
                    if ("desc".equals(input)) {
                        sort.setOrder(input);
                        break;
                    }
                    if ("asc".equals(input)) {
                        sort.setOrder(input);
                        break;
                    }
                    System.out.println("Wrong input. Try again.");
                }
                sortList.add(sort);
            }
        } catch (IOException e) {
            System.out.println("BufferReader wrong.");
            return null;
        }
        return sortList;
    }

    public static List<SortOptions> getSortList(List<Sort> sortList) {
        List<SortOptions> soList = new ArrayList<>();
        for (Sort sort1 : sortList) {
            SortOrder order;
            if (sort1.getOrder().equals(ASC)) {
                order = SortOrder.Asc;
            } else if (sort1.getOrder().equals(DESC)) {
                order = SortOrder.Desc;
            } else {
                order = null;
                return null;
            }
            FieldSort fs = FieldSort.of(f -> f.field(sort1.getField()).order(order));
            SortOptions so = SortOptions.of(s -> s.field(fs));
            soList.add(so);
        }
        return soList;
    }

    public static ArrayList<Sort> makeSortList(String field1, Boolean isAsc1, String field2, Boolean isAsc2, String field3, Boolean isAsc3) {
        ArrayList<Sort> sortList = new ArrayList<>();
        Sort sort = new Sort();
        sort.setField(field1);
        if (isAsc1)
            sort.setOrder(ASC);
        sortList.add(sort);

        if (field2 != null) {
            Sort sort1 = new Sort();
            sort1.setField(field2);
            if (isAsc2) sort1.setOrder(ASC);
            sortList.add(sort1);
        }

        if (field3 != null) {
            Sort sort1 = new Sort();
            sort1.setField(field3);
            if (isAsc3) sort1.setOrder(ASC);
            sortList.add(sort1);
        }
        return sortList;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

}
