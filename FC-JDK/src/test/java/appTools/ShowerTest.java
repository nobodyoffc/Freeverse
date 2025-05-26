package appTools;

import ui.Shower;
import data.fchData.Cash;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

public class ShowerTest {
    public static void main(String[] args) {
        // Create sample data
        List<Cash> cashList = createSampleCashList(25); // Create 25 sample items
        
        // Setup parameters for showOrChooseListInPages
        int pageSize = 10;
        String title = "Test Cash List";
        
        // Create field width map using Cash.DefaultShowFields and DefaultShowWidths
        LinkedHashMap<String, Integer> fieldWidthMap = Cash.getFieldWidthMap();
        
        // Setup timestamp fields
        List<String> timestampFieldList = Collections.singletonList("birthTime");
        
        // Setup satoshi fields (for value conversion)
        List<String> satoshiField = Collections.singletonList("value");
        
        // Height to time field map (empty for this test)
        Map<String, String> heightToTimeFieldMap = new HashMap<>();
        
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        
        try {
            // Test without choosing (just display)
            System.out.println("\nTesting display mode (no choosing):");
            Shower.showOrChooseListInPages(
                    title, cashList,
                pageSize,
                    // beginFrom
                    null, false,  // choose
                    Cash.class, br
            );
            
            // Test with choosing enabled
            System.out.println("\nTesting choose mode:");
            List<Cash> chosen = Shower.showOrChooseListInPages(
                    title, cashList,
                pageSize,
                    // beginFrom
                    null, true,  // choose
                    Cash.class, br
            );
            
            // Show chosen items
            if (chosen != null && !chosen.isEmpty()) {
                System.out.println("\nChosen items:");
                Cash.showOrChooseCashList(chosen, "Selected Cash Items", "", false, br);
            }
            
        } catch (Exception e) {
            System.out.println("Error during test: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<Cash> createSampleCashList(int count) {
        List<Cash> cashList = new ArrayList<>();
        long currentTime = System.currentTimeMillis() / 1000;
        
        for (int i = 0; i < count; i++) {
            Cash cash = new Cash();
            
            // Set basic fields using proper setter methods
            cash.setId("cash_" + i);
            cash.setBirthTime(currentTime - (i * 3600)); // Each cash created 1 hour apart
            cash.setValid(i % 2 == 0); // Alternating valid status
            cash.setIssuer("issuer_" + (i % 3)); // Cycle through 3 issuers
            cash.setOwner("owner_" + (i % 4)); // Cycle through 4 owners
            cash.setValue(100000000L + (i * 10000000L)); // Different values
            cash.setCd(1000L + (i * 100L)); // Different CD values
            cash.setCdd(500L + (i * 50L)); // Different CDD values
            
            // No need for reflection here since we're using proper setters
            cashList.add(cash);
        }
        
        return cashList;
    }
} 