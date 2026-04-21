import com.google.gson.Gson;
import data.feipData.Feip;
import data.feipData.HomeOpData;
import data.fchData.OpReturn;
import org.junit.Test;
import org.junit.Assert;
import startFEIP.FileParser;

import java.util.HashMap;
import java.util.Map;

/**
 * Test to verify that Map<String, String> home in HomeOpData can be parsed correctly
 * through the full parsing pipeline:
 * 1. Create Feip with HomeOpData containing sample data
 * 2. Convert to JSON string
 * 3. Parse JSON string using FileParser.parseFeip (line 63)
 * 4. Parse feip.data using IdentityParser approach (line 172)
 * 5. Verify the Map<String, String> home is correctly parsed
 */
public class HomeOpDataParsingTest {

    @Test
    public void testHomeOpDataMapParsing() {
        // Step 1: Create a sample HomeOpData with Map<String, String> home
        HomeOpData homeOpData = new HomeOpData();
        homeOpData.setOp("register");
        
        Map<String, String> homeMap = new HashMap<>();
        homeMap.put("home", "https://example.com");
        homeMap.put("github", "https://github.com/user");
        homeMap.put("twitter", "https://twitter.com/user");
        homeOpData.setHome(homeMap);

        // Step 2: Create Feip object with HomeOpData
        Feip feip = new Feip();
        feip.setType("FEIP");
        feip.setSn("9");  // HOMEPAGE protocol sn
        feip.setVer("1");
        feip.setName("Homepage");
        feip.setData(homeOpData);

        // Step 3: Convert Feip to JSON string
        Gson gson = new Gson();
        String jsonString = gson.toJson(feip);
        System.out.println("Generated JSON: " + jsonString);

        // Step 4: Parse JSON string using FileParser.parseFeip (line 63 approach)
        OpReturn opReturn = new OpReturn();
        opReturn.setOpReturn(jsonString);
        opReturn.setSigner("FCHtest123456789012345678901234567890");
        opReturn.setHeight(12345L);
        opReturn.setTime(1234567890L);
        opReturn.setTxIndex(0);

        Feip parsedFeip = FileParser.parseFeip(opReturn);
        Assert.assertNotNull("Parsed Feip should not be null", parsedFeip);
        Assert.assertEquals("Type should be FEIP", "FEIP", parsedFeip.getType());
        Assert.assertEquals("Sn should be 9", "9", parsedFeip.getSn());
        Assert.assertNotNull("Data should not be null", parsedFeip.getData());

        // Step 5: Parse feip.data using IdentityParser approach (line 172)
        HomeOpData parsedHomeOpData;
        try {
            parsedHomeOpData = gson.fromJson(gson.toJson(parsedFeip.getData()), HomeOpData.class);
        } catch (Exception e) {
            Assert.fail("Failed to parse HomeOpData: " + e.getMessage());
            return;
        }

        Assert.assertNotNull("Parsed HomeOpData should not be null", parsedHomeOpData);
        Assert.assertEquals("Operation should be register", "register", parsedHomeOpData.getOp());

        // Step 6: Verify Map<String, String> home is correctly parsed
        Map<String, String> parsedHome = parsedHomeOpData.getHome();
        Assert.assertNotNull("Parsed home Map should not be null", parsedHome);
        Assert.assertEquals("Home map should have 3 entries", 3, parsedHome.size());
        Assert.assertEquals("Home entry should match", "https://example.com", parsedHome.get("home"));
        Assert.assertEquals("Github entry should match", "https://github.com/user", parsedHome.get("github"));
        Assert.assertEquals("Twitter entry should match", "https://twitter.com/user", parsedHome.get("twitter"));

        System.out.println("Successfully parsed HomeOpData with Map<String, String> home:");
        System.out.println("  op: " + parsedHomeOpData.getOp());
        System.out.println("  home map: " + parsedHome);
    }

    @Test
    public void testHomeOpDataWithEmptyMap() {
        // Test with empty map
        HomeOpData homeOpData = new HomeOpData();
        homeOpData.setOp("register");
        homeOpData.setHome(new HashMap<>());

        Feip feip = new Feip();
        feip.setType("FEIP");
        feip.setSn("9");
        feip.setVer("1");
        feip.setName("Homepage");
        feip.setData(homeOpData);

        Gson gson = new Gson();
        String jsonString = gson.toJson(feip);

        OpReturn opReturn = new OpReturn();
        opReturn.setOpReturn(jsonString);
        opReturn.setSigner("FCHtest123456789012345678901234567890");

        Feip parsedFeip = FileParser.parseFeip(opReturn);
        Assert.assertNotNull("Parsed Feip should not be null", parsedFeip);

        HomeOpData parsedHomeOpData = gson.fromJson(gson.toJson(parsedFeip.getData()), HomeOpData.class);
        Assert.assertNotNull("Parsed HomeOpData should not be null", parsedHomeOpData);
        Assert.assertNotNull("Parsed home Map should not be null", parsedHomeOpData.getHome());
        Assert.assertTrue("Home map should be empty", parsedHomeOpData.getHome().isEmpty());
    }

    @Test
    public void testHomeOpDataWithSingleEntry() {
        // Test with single entry map
        HomeOpData homeOpData = new HomeOpData();
        homeOpData.setOp("unregister");
        
        Map<String, String> homeMap = new HashMap<>();
        homeMap.put("home", "https://mysite.com");
        homeOpData.setHome(homeMap);

        Feip feip = new Feip();
        feip.setType("FEIP");
        feip.setSn("9");
        feip.setVer("1");
        feip.setName("Homepage");
        feip.setData(homeOpData);

        Gson gson = new Gson();
        String jsonString = gson.toJson(feip);

        OpReturn opReturn = new OpReturn();
        opReturn.setOpReturn(jsonString);
        opReturn.setSigner("FCHtest123456789012345678901234567890");

        Feip parsedFeip = FileParser.parseFeip(opReturn);
        Assert.assertNotNull("Parsed Feip should not be null", parsedFeip);

        HomeOpData parsedHomeOpData = gson.fromJson(gson.toJson(parsedFeip.getData()), HomeOpData.class);
        Assert.assertNotNull("Parsed HomeOpData should not be null", parsedHomeOpData);
        Assert.assertEquals("Operation should be unregister", "unregister", parsedHomeOpData.getOp());
        
        Map<String, String> parsedHome = parsedHomeOpData.getHome();
        Assert.assertNotNull("Parsed home Map should not be null", parsedHome);
        Assert.assertEquals("Home map should have 1 entry", 1, parsedHome.size());
        Assert.assertEquals("Home entry should match", "https://mysite.com", parsedHome.get("home"));
    }
}