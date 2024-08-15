package java8;

import fchData.Cash;
import jakarta.json.Json;
import javaTools.JsonTools;
import org.checkerframework.checker.units.qual.C;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class mainTest {
    public static void main(String[] args) {
        Cash cash1 =new Cash();
        Cash cash2 =new Cash();
        List<Cash> cashList = new ArrayList<>();
        cash1.setCashId("1");
        cash1.setOwner("me");
        cash2.setCashId("2");
        cash2.setOwner("you");
        cashList.add(cash1);
        cashList.add(cash2);

        List<String> result = cashList.stream().map(Cash::getOwner).collect(Collectors.toList());
        JsonTools.printJson(result);
    }
}
