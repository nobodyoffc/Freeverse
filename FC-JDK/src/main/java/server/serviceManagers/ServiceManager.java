package server.serviceManagers;

import clients.ApipClient;
import data.feipData.Feip;
import data.feipData.Service;
import data.feipData.ServiceOpData;
import ui.Menu;
import ui.Shower;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.ApiAccount;
import constants.OpNames;
import utils.JsonUtils;

import java.io.BufferedReader;
import java.util.ArrayList;

import static clients.ApipClient.checkBalance;

public abstract class ServiceManager {
    protected Service service;
    protected ApiAccount apipAccount;
    protected BufferedReader br;
    protected byte[] symKey;

    public ServiceManager(Service service, ApiAccount apipAccount, BufferedReader br, byte[] symKey) {
        this.service=service;
        this.br = br;
        this.symKey = symKey;
        this.apipAccount =apipAccount;
    }

    protected abstract Object inputParams(byte[] symKey, BufferedReader br);

    protected abstract Object updateParams(Object serviceParams, BufferedReader br, byte[] symKey);


    public void menu() {
        Menu menu = new Menu();
        menu.setTitle("Service Manager");
        ArrayList<String> menuItemList = new ArrayList<>();

        menuItemList.add("Show service");
        menuItemList.add("Publish service");
        menuItemList.add("Update service");
        menuItemList.add("Stop service");
        menuItemList.add("Recover service");
        menuItemList.add("Close service");

        menu.add(menuItemList);
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> showService();
                case 2 -> publishService();
                case 3 -> updateService(symKey,br);
                case 4 -> stopService(br);
                case 5 -> recoverServices(br);
                case 6 -> closeServices(br);
                case 7 -> reloadServices(br,symKey);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private void reloadServices(BufferedReader br, byte[] symKey) {
        String sid = service.getId();
        ApipClient apipClient = (ApipClient) apipAccount.getClient();
        Service service1 = apipClient.serviceById(sid);
        if(service1==null)return;
        service = service1;
        checkBalance(apipAccount, apipClient.getFcClientEvent(), symKey, apipClient);
    }

    private void showService() {
        System.out.println(JsonUtils.toNiceJson(service));
    }

    public void publishService() {
        System.out.println("Publish service services...");

        if (Menu.askIfToDo("Get the OpReturn text to publish a new service service?", br)) return;

        Feip dataOnChain = setFcInfoForService();

        ServiceOpData data = new ServiceOpData();

        data.setOp(OpNames.PUBLISH);

        data.inputType(br);

        data.inputDealerPubkey(br);

        if(symKey!=null) {
            ApipClient apipClient=null;
            if (apipAccount != null)apipClient = apipAccount.getApipClient();
            data.inputService(br);
        }
        else data.inputService(br);

        System.out.println("Set the pricing fields...");
        data.inputPricingFields(br);

        System.out.println("Set the service parameters...");

        Object serviceParams = inputParams(symKey, br);

        data.setParams(serviceParams);

        dataOnChain.setData(data);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        Shower.printUnderline(10);
        String opReturnJson = gson.toJson(dataOnChain);
        System.out.println(opReturnJson);
        Shower.printUnderline(10);
        System.out.println("Check, and edit if you want, the JSON text above. Send it in a TX by the owner of the service to freecash blockchain:");
        Menu.anyKeyToContinue(br);
    }

    private static Feip setFcInfoForService() {
        Feip dataOnChain = new Feip();
        dataOnChain.setType("FEIP");
        dataOnChain.setSn(Feip.FeipProtocol.SERVICE.getSn());
        dataOnChain.setVer(Feip.FeipProtocol.SERVICE.getVer());
        dataOnChain.setName(Feip.FeipProtocol.SERVICE.getName());
        return dataOnChain;
    }


    public void updateService(byte[] symKey,BufferedReader br) {
        System.out.println("Update service services...");
        if(service==null)return;
        showService();

        if (Menu.askIfToDo("Get the OpReturn text to update a service service?", br)) return;

        Feip dataOnChain = setFcInfoForService();

        ServiceOpData data = new ServiceOpData();

        ServiceOpData.serviceToServiceData(service,data);

        data.setOp(OpNames.UPDATE);

        data.updateType(br);

        if(symKey!=null) {
            ApipClient apipClient = null;
            if(apipAccount!=null)apipClient = (ApipClient) apipAccount.getClient();
            data.updateService(br, symKey, apipClient);
        }
        else data.updateService(br);

        System.out.println("Update the pricing fields...");
        data.updatePricingFields(br);

        Object serviceParams = data.getParams();

        serviceParams = updateParams(serviceParams,br,symKey);

        data.setParams(serviceParams);

        dataOnChain.setData(data);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        System.out.println("Check the JSON text below. Send it in a TX by the owner of the service to freecash blockchain:");
        System.out.println(gson.toJson(dataOnChain));

        Menu.anyKeyToContinue(br);
    }

    private void stopService(BufferedReader br) {
        System.out.println("Stop service services...");
        operateService(br,OpNames.STOP);
    }

    private void recoverServices(BufferedReader br) {
        System.out.println("Recover service services...");
        operateService(br,OpNames.RECOVER);
    }

    private void closeServices(BufferedReader br) {
        System.out.println("Close service services...");
        operateService(br,OpNames.CLOSE);
    }

    private void operateService(BufferedReader br,String op) {

        showService();

        if (Menu.askIfToDo("Get the OpReturn text to "+op+" a service service?", br)) return;

        Feip dataOnChain = setFcInfoForService();

        ServiceOpData data = new ServiceOpData();

        data.setOp(op);
        data.setSid(service.getId());
        dataOnChain.setData(data);

        System.out.println("The owner can send a TX with below json in OpReturn to "+op+" the service: "+service.getId());
        System.out.println(JsonUtils.toNiceJson(dataOnChain));

        System.out.println("you can replace the value of 'data.sid' to "+op+" other your own service services.");
        Menu.anyKeyToContinue(br);
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public ApiAccount getApipAccount() {
        return apipAccount;
    }

    public void setApipAccount(ApiAccount apipAccount) {
        this.apipAccount = apipAccount;
    }

}
