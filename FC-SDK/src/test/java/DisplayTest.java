import appTools.Shower;
import fch.fchData.Cash;

import java.util.ArrayList;
import java.util.List;

public class DisplayTest {
    public static void main(String[] args) {
        // Create a test Cash object
        Cash testCash = new Cash();
        testCash.setId("test-id-12345"); // Set the id from FcEntity
        testCash.setValue(1000000L); // Set some Cash-specific fields
        testCash.setOwner("owner-address");
        testCash.setValid(true);
        
        // Create a list with the test object
        List<Cash> cashList = new ArrayList<>();
        cashList.add(testCash);
        
        // Display the Cash object
        System.out.println("Testing display of Cash objects with id field:");
        Shower.showOrChooseList("Cash Test",cashList, null, false, Cash.class, null);
    }
} 