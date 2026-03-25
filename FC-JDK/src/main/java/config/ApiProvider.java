package config;


import clients.FcClient;
import constants.Values;
import data.feipData.ServiceType;
import server.ApipApi;
import constants.Strings;
import constants.FieldNames;
import constants.IndicesNames;
import data.apipData.Fcdsl;
import data.fcData.ReplyBody;
import clients.ApipClient;
import fapi.client.FapiClient;
import data.feipData.Service;
import ui.Inputer;
import utils.JsonUtils;
import utils.http.HttpUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.FreeApi;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static ui.Inputer.askIfYes;
import static ui.Inputer.promptAndUpdate;
import static data.feipData.ServiceType.*;
import static constants.Strings.URL_HEAD;


public class ApiProvider extends Service {
    private static final Logger log = LoggerFactory.getLogger(ApiProvider.class);
    public static final String COMPONENTS = "components";

    public ApiProvider() {}
    
    public boolean fromFcService(Service service) {
        if(service==null)return false;
        this.id = service.getId();
        this.stdName = service.getStdName();
        this.localNames = service.getLocalNames();
        this.desc = service.getDesc();
        this.type = service.getType();
        this.ver = service.getVer();
        this.dealer = service.getDealer();
        this.dealerPubkey = service.getDealerPubkey();
        this.home = service.getHome();
        this.waiters = service.getWaiters();
        this.protocols = service.getProtocols();
        this.codes = service.getCodes();
        this.services = service.getServices();
        this.setParams(service.getParams());
        this.owner = service.getOwner();
        this.pricePerKB = service.getPricePerKB();
        this.pricePerKBIn = service.getPricePerKBIn();
        this.pricePerKBOut = service.getPricePerKBOut();
        this.pricePerKBDay = service.getPricePerKBDay();
        this.minPayment = service.getMinPayment();
        this.pricePerRequest = service.getPricePerRequest();
        this.sessionDays = service.getSessionDays();
        this.consumeViaShare = service.getConsumeViaShare();
        this.orderViaShare = service.getOrderViaShare();
        this.currency = service.getCurrency();
        this.birthTime = service.getBirthTime();
        this.birthHeight = service.getBirthHeight();
        this.lastTxId = service.getLastTxId();
        this.lastTime = service.getLastTime();
        this.lastHeight = service.getLastHeight();
        this.tCdd = service.gettCdd();
        this.tRate = service.gettRate();
        this.active = service.getActive();
        this.closed = service.getClosed();
        this.closeStatement = service.getCloseStatement();

        // Set apiUrl if not already in home
        String urlHead = service.getApiUrl();
        if(urlHead != null && !urlHead.isBlank()){
            setApiUrl(urlHead);
        } else if(getApiUrl() == null){
            setApiUrl("http://127.0.0.1:8081/APIP");
        }

        return true;
    }
    
    public void freshApiProvider(ApipClient apipClient) {
        Service service = apipClient.serviceById(id);
        fromFcService(service);
    }
    
    @Nullable
    public static ApiProvider apiProviderFromFcService(Service service, ServiceType type) {
        if(service==null)return null;
        ApiProvider apiProvider = new ApiProvider();
        apiProvider.fromFcService(service);
        apiProvider.makeServiceType(type);
        return apiProvider;
    }

    public static ApiProvider searchFcApiProvider(ApipClient initApipClient, ServiceType serviceType) {
        List<Service> serviceList = initApipClient.getServiceListByType(serviceType.toString().toLowerCase());
        Service service = Configure.selectService(serviceList);
        if(service==null)return null;
        return apiProviderFromFcService(service, serviceType);
    }

    private void inputOwner(BufferedReader br) throws IOException {
        this.owner = Inputer.promptAndSet(br, "API owner", this.owner);
    }
    
    public ApiProvider makeFcProvider(ServiceType serviceType, ApipClient apipClient){
        List<Service> serviceList = apipClient.getServiceListByType(serviceType.toString());
        Service service = Configure.selectService(serviceList);
        if(service==null)return null;
        return apiProviderFromFcService(service, fetchServiceType());
    }

    public boolean makeApipProvider(BufferedReader br) {
        String apiUrl = Inputer.inputString(br,"Input the urlHead of the APIP service. Enter to choose a default one");
        if("".equals(apiUrl)) {
            List<FreeApi> freeApiList = Settings.freeApiListMap.get(ServiceType.APIP);
            FreeApi freeApi = Inputer.chooseOneFromList(freeApiList, URL_HEAD, "Choose an default APIP service:", br);
            if(freeApi!=null) apiUrl = freeApi.getUrlHead();
            else apiUrl = Inputer.inputString(br,"Input the urlHead of the APIP service.");
        }
        setApiUrl(apiUrl);
        ReplyBody replier;
        try {
            replier = FcClient.getService(apiUrl, ApipApi.VER_1);
        }catch (Exception ignore) {
            return false;
        }
        if (replier == null || replier.getData() == null) return false;
        Service service = (Service) replier.getData();
        if(service==null)return false;
        fromFcService(service);
        System.out.println("Got the service:");
        JsonUtils.printJson(service);
        return true;
    }


    public boolean makeApiProvider(BufferedReader br, ServiceType serviceType, @Nullable ApipClient apipClient, @Nullable FapiClient fapiClient) {
        try  {
            if(serviceType == null) serviceType = inputType(br);
            else this.makeServiceType(serviceType);

            if(fetchServiceType() == null) return false;

            switch (fetchServiceType()){
                case APIP -> {return makeApipProvider(br);}
                case NASA_RPC ->{
                    inputApiURL(br, "http://127.0.0.1:8332");
                    id = makeNasaId();
                    stdName = id;
                }
                case ES -> {
                    inputApiURL(br,"http://127.0.0.1:9200");
                    id = makeSimpleId(ES);
                    stdName = id;
                }
                case REDIS -> {
                    inputApiURL(br, "http://127.0.0.1:6379");
                    id = makeSimpleId(REDIS);
                    stdName = id;
                }
                case DISK, TALK -> {
                    List<Service> serviceList = fetchServicesByType(serviceType, fapiClient, apipClient);
                    if(serviceList==null || serviceList.isEmpty()){
                        System.out.println("No "+serviceType+" service available.");
                        return false;
                    }
                    Service service = Configure.selectService(serviceList);
                    boolean done = fromFcService(service);
                    if(!done) System.out.println("Failed to make provider from on-chain service information.");
                }
                case FAPI, FAPI_No1_NrC7 -> {
                    List<Service> serviceList = fetchServicesByType(serviceType, fapiClient, apipClient);
                    Service service = Configure.selectService(serviceList);
                    if(service==null){
                        System.out.println("No FAPI service available.");
                        return false;
                    }
                    boolean done = fromFcService(service);
                    if(!done) System.out.println("Failed to make provider from on-chain service information.");
                }
                default -> {

                    Service service = fetchServiceById(this.id, fapiClient, apipClient);
                    if(service!=null){
                        fromFcService(service);
                    }else {
                        inputSid(br);
                        inputApiURL(br, null);
                        inputOrgUrl(br);
                        inputDocUrl(br);
                        inputOwner(br);
                        inputProtocol(br);
                        inputComponents(br);
                    }
                    id = makeSimpleId(serviceType);
                    stdName = id;
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading input");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format");
            e.printStackTrace();
        }
        return true;
    }

    @NotNull
    private String makeSimpleId(ServiceType type) {
        return type.name() + "@" + getApiUrl();
    }

    @NotNull
    private String makeNasaId() {
        if(components!=null && !components.isEmpty())
            return components.get(0) + "@" + getApiUrl();
        return "NasaRPC" + "@" + getApiUrl();
    }

    private void inputSid(BufferedReader br) throws IOException {
        while(true) {
            String input = Inputer.promptAndSet(br, "sid", this.id);
            if(input!=null){
                this.id = input;
                break;
            }
            System.out.println("Sid is necessary. Input again.");
        }
    }

    private List<Service> fetchServicesByType(ServiceType serviceType, @Nullable FapiClient fapiClient, @Nullable ApipClient apipClient){
        if(fapiClient!=null){
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.setEntity(IndicesNames.SERVICE);
            fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.TYPE).addNewValues(serviceType.name());
            fcdsl.addNewExcept().addNewTerms().addNewFields(FieldNames.ACTIVE).addNewValues(Values.FALSE);
            List<Service> services = fapiClient.entitySearch(IndicesNames.SERVICE, fcdsl, Service.class);
            if(services!=null && !services.isEmpty())return services;
        }
        if(apipClient!=null){
            return apipClient.getServiceListByType(serviceType.name());
        }
        return null;
    }

    private Service fetchServiceById(@Nullable String sid, @Nullable FapiClient fapiClient, @Nullable ApipClient apipClient){
        if(sid==null)return null;
        if(fapiClient!=null){
            Map<String, Service> result = fapiClient.entityByIds(IndicesNames.SERVICE, Service.class, sid);
            if(result!=null && result.containsKey(sid))return result.get(sid);
        }
        if(apipClient!=null){
            return apipClient.serviceById(sid);
        }
        return null;
    }

    private ServiceType inputType(BufferedReader br) throws IOException {
        ServiceType[] choices = ServiceType.values();
        ServiceType type = Inputer.chooseOne(choices, null, "Choose the type of API provider:",br);
        this.makeServiceType(type);
        return type;
    }

    private void inputApiURL(BufferedReader br, String defaultUrl) throws IOException {
        while(true) {
            String apiUrl = Inputer.promptAndSet(br, "the url of API request. The default is " + defaultUrl, getApiUrl());
            if(apiUrl==null)
                apiUrl=defaultUrl;
            if(!HttpUtils.illegalUrl(apiUrl)){
                setApiUrl(apiUrl);
                break;
            }
            System.out.println("Illegal URL. Try again.");
        }
    }

    private void inputDocUrl(BufferedReader br) throws IOException {
        String docUrl = Inputer.promptAndSet(br, "the url of API document", getDocUrl());
        setDocUrl(docUrl);
    }

    private void inputOrgUrl(BufferedReader br) throws IOException {
        String orgUrl = Inputer.promptAndSet(br, "the url of organization", getOrgUrl());
        setOrgUrl(orgUrl);
    }

    private void inputProtocol(BufferedReader br) throws IOException {
        this.protocols = Inputer.promptAndSet(br, "protocol", this.protocols);
    }

    private void inputComponents(BufferedReader br) throws IOException {
        this.components = Inputer.promptAndSet(br, "components", this.components);
    }


    public void updateAll(BufferedReader br) {
        try {
            if(fetchServiceType()==null)
                this.makeServiceType(Inputer.chooseOne(ServiceType.values(), null, "Choose the type:",br));
            else if(askIfYes(br,"The type is "+ fetchServiceType()+". Update it? "))
                this.makeServiceType(Inputer.chooseOne(ServiceType.values(), null, "Choose the type:",br));

            switch (fetchServiceType()){
                case APIP, DISK ->{
                    if(Inputer.askIfYes(br,"The apiUrl is "+getApiUrl()+". Update it?")) {
                        String apiUrl = Inputer.inputString(br, "Input the urlHead of the API service:");
                        setApiUrl(apiUrl);
                    }
                    if(getApiUrl()==null)return;
                    ReplyBody replier = ApipClient.getService(getApiUrl(), ApipApi.VER_1);

                    if(replier==null||replier.getData()==null)return;
                    Service service = (Service) replier.getData();
                    fromFcService(service);
                    String orgUrlFromService = service.getOrgUrl();
                    if(orgUrlFromService != null && !orgUrlFromService.isBlank())
                        setOrgUrl(orgUrlFromService);
                    if(getOrgUrl()!=null) setDocUrl(getOrgUrl()+"/"+ Strings.DOCS);
                }
                case ES,REDIS->{
                    String apiUrl = promptAndUpdate(br, "url of the API requests", getApiUrl());
                    setApiUrl(apiUrl);
                    this.id = makeSimpleId(fetchServiceType());
                }
                default -> {
                    String apiUrl = promptAndUpdate(br, "url of the API requests", getApiUrl());
                    setApiUrl(apiUrl);
                    this.id = getApiUrl();
                    String docUrl = promptAndUpdate(br, "url of the API documents", getDocUrl());
                    setDocUrl(docUrl);
                    String orgUrl = promptAndUpdate(br, "url of the organization", getOrgUrl());
                    setOrgUrl(orgUrl);
                    this.owner = promptAndUpdate(br, "API owner", this.owner);
                    this.protocols = promptAndUpdate(br, "protocol", this.protocols);
                    this.components = promptAndUpdate(br, COMPONENTS, this.components);
                }
            }

        } catch (IOException e) {
            System.out.println("Error reading input");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format");
            e.printStackTrace();
        }
    }

}
