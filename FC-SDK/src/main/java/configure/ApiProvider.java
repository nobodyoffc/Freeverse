package configure;


import clients.Client;
import constants.ApiNames;
import constants.Strings;
import fcData.FcReplierHttp;
import feip.feipData.serviceParams.ApipParams;
import feip.feipData.serviceParams.DiskParams;
import feip.feipData.serviceParams.Params;
import clients.apipClient.ApipClient;
import feip.feipData.Service;
import appTools.Inputer;
import javaTools.JsonTools;
import javaTools.http.HttpTools;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.FreeApi;
import settings.Settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

import static appTools.Inputer.askIfYes;
import static appTools.Inputer.promptAndUpdate;
import static configure.ServiceType.ES;
import static configure.ServiceType.REDIS;
import static constants.Strings.URL_HEAD;
import static constants.Ticks.FCH;


public class ApiProvider {
    private static final Logger log = LoggerFactory.getLogger(ApiProvider.class);
    private String id;
    private String name;
    private ServiceType type;
    private String orgUrl;
    private String docUrl;
    private String apiUrl;
    private String owner;
    private String[] protocols;
    private String[] ticks;
    private transient Service service;
    private transient Params apiParams;
    private String accountPubKey;
//    private transient List<FreeApi> freeApiList;

    public ApiProvider() {}
    public boolean fromFcService(Service service, Class<?extends Params> tClass) {
        if(service==null)return false;
        this.id =service.getSid();
        this.name = service.getStdName();
        Params params = Params.getParamsFromService(service, tClass);
        if(params==null) return false;
        this.apiUrl=params.getUrlHead();
        this.owner=service.getOwner();
        for(String type : service.getTypes()){
            try{
                this.type= ServiceType.valueOf(type);
                break;
            }catch (Exception ignore){};
        }
        if(service.getUrls().length>0)this.orgUrl=service.getUrls()[0];
        if(service.getProtocols()!=null && service.getProtocols().length>0)this.protocols=service.getProtocols();
        this.apiParams=Params.getParamsFromService(service,tClass);
        this.service=service;
        return true;
    }

    @Nullable
    public static ApiProvider apiProviderFromFcService(Service service, ServiceType type) {
        if(service==null)return null;
        ApiProvider apiProvider = new ApiProvider();
        apiProvider.setId(service.getSid());
        Params params = null;
        switch (type){
            case APIP -> params = Params.getParamsFromService(service, ApipParams.class);
            case DISK -> params = Params.getParamsFromService(service, DiskParams.class);
        }

        if(params==null) return null;
        apiProvider.setApiUrl(params.getUrlHead());
        apiProvider.setOwner(service.getOwner());
        for(String typeStr : service.getTypes()){
            try{
                apiProvider.setType(ServiceType.valueOf(typeStr));
                break;
            }catch (Exception ignore){};
        }
        if(service.getUrls()!=null&&service.getUrls().length>0)apiProvider.setOrgUrl(service.getUrls()[0]);
        if(service.getProtocols()!=null&&service.getProtocols().length>0)apiProvider.setProtocols(service.getProtocols());
        apiProvider.setApiParams((Params) service.getParams());
        apiProvider.setService(service);
        apiProvider.setType(type);

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
        return apiProviderFromFcService(service,type);
    }

    public boolean makeApipProvider(BufferedReader br) {
        apiUrl = Inputer.inputString(br,"Input the urlHead of the APIP service. Enter to choose a default one");
        if("".equals(apiUrl)) {
            List<FreeApi> freeApiList = Settings.freeApiListMap.get(ServiceType.APIP);
            FreeApi freeApi = Inputer.chooseOne(freeApiList, URL_HEAD, "Choose an default APIP service:", br);
            if(freeApi!=null) apiUrl = freeApi.getUrlHead();
            else apiUrl = Inputer.inputString(br,"Input the urlHead of the APIP service.");
        }
        FcReplierHttp replier;
        try {
            replier = Client.getService(apiUrl, ApiNames.Version1, ApipParams.class);//OpenAPIs.getService(apiUrl);
        }catch (Exception ignore) {
            return false;
        }
        if (replier == null || replier.getData() == null) return false;
        service = (Service) replier.getData();
        if(service==null || service.getParams()==null)return false;
        apiParams = (Params) service.getParams();
        System.out.println("Got the service:");
        JsonTools.printJson(service);
        id = service.getSid();
        name = service.getStdName();
        owner = service.getOwner();
        protocols = service.getProtocols();
        ticks = new String[]{"fch"};
        return true;
    }


    public boolean makeApiProvider(BufferedReader br, ServiceType serviceType, @Nullable ApipClient apipClient) {
        try  {
            if(serviceType ==null)serviceType = inputType(br);
            else type = serviceType;

            if(type==null)return false;

            switch (type){
                case APIP -> {return makeApipProvider(br);}
                case NASA_RPC ->{
                    inputApiURL(br, "http://127.0.0.1:8332");
                    do {
                        inputTicks(br);
                    }while(this.ticks==null|| ticks.length==0);
                    id = makeNasaId();
                    name=id;
                }
                case ES -> {
                    inputApiURL(br,"http://127.0.0.1:9200");
                    id = makeSimpleId(ES);
                    name=id;
                }
                case REDIS -> {
                    inputApiURL(br, "http://127.0.0.1:6379");
                    id = makeSimpleId(REDIS);
                    name=id;
                }
                case DISK, TALK -> {
                    if(apipClient==null){
                        System.out.println("Can't add such provider because the APIP client is null.");
                        return false;
                    }
                    List<Service> serviceList = apipClient.getServiceListByType(serviceType.name());
                    Service service = Configure.selectService(serviceList);
                    boolean done = fromFcService(service, DiskParams.class);
                    if(!done) System.out.println("Failed to make provider from on-chain service information.");
                }
                default -> {
                    inputSid(br);
                    inputApiURL(br, null);
                    inputOrgUrl(br);
                    inputDocUrl(br);
                    inputOwner(br);
                    inputProtocol(br);
                    id = makeSimpleId(serviceType);
                    name=id;
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
        return type.name() + "@" + apiUrl;
    }

    @NotNull
    private String makeNasaId() {
        return ticks[0] + "@" + apiUrl;
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

    private ServiceType inputType(BufferedReader br) throws IOException {
        ServiceType[] choices = ServiceType.values();
        type = Inputer.chooseOne(choices, null, "Choose the type of API provider:",br);
//
//        for(int i=0;i<choices.length;i++){
//            System.out.println((i+1)+" "+choices[i].name());
//        }
//        int choice = Inputer.inputInteger(br,"Input the number:",choices.length+1);
//        type = choices[choice-1];
        return type;
    }

    private void inputApiURL(BufferedReader br, String defaultUrl) throws IOException {
        while(true) {
            this.apiUrl = Inputer.promptAndSet(br, "the url of API request. The default is " + defaultUrl, this.apiUrl);
            if(apiUrl==null)
                apiUrl=defaultUrl;
            if(!HttpTools.illegalUrl(apiUrl))break;
            System.out.println("Illegal URL. Try again.");
        }
    }

    private void inputDocUrl(BufferedReader br) throws IOException {
        this.docUrl = Inputer.promptAndSet(br, "the url of API document", this.docUrl);
    }

    private void inputOrgUrl(BufferedReader br) throws IOException {
        this.orgUrl = Inputer.promptAndSet(br, "the url of organization", this.orgUrl);
    }

    private void inputProtocol(BufferedReader br) throws IOException {
        this.protocols = Inputer.promptAndSet(br, "protocol", this.protocols);
    }

    private void inputTicks(BufferedReader br) throws IOException {
        this.ticks = Inputer.promptAndSet(br, "ticks", this.ticks);
    }


    public void updateAll(BufferedReader br) {
        try {
            if(this.type==null)
                    this.type = Inputer.chooseOne(ServiceType.values(), null, "Choose the type:",br);//ApiType.valueOf(promptAndUpdate(br, "type ("+ Arrays.toString(ApiType.values())+")", String.valueOf(this.type)));
            else if(askIfYes(br,"The type is "+this.type+". Update it? "))
                this.type = Inputer.chooseOne(ServiceType.values(), null, "Choose the type:",br);

            switch (this.type){
                case APIP, DISK ->{
                    if(Inputer.askIfYes(br,"The apiUrl is "+apiUrl+". Update it?")) {
                        apiUrl = Inputer.inputString(br, "Input the urlHead of the APIP service:");
                    }
                    if(apiUrl==null)return;
                    FcReplierHttp replier = ApipClient.getService(apiUrl, ApiNames.Version1, ApipParams.class);//OpenAPIs.getService(apiUrl);

                    if(replier==null||replier.getData()==null)return;
                    service = (Service) replier.getData();
                    this.id=service.getSid();
                    if(service.getParams()!=null)
                        apiParams = (ApipParams) service.getParams();
                    if(service.getUrls().length>0)
                        orgUrl = service.getUrls()[0];//promptAndUpdate(br, "url of the organization", this.orgUrl);
                    if(orgUrl!=null)docUrl = orgUrl+"/"+ Strings.DOCS;//promptAndUpdate(br, "url of the API documents", this.docUrl);
                    owner = service.getOwner();
                    protocols = service.getProtocols();
                    if(ticks ==null ||ticks.length==0)ticks = new String[] {FCH};
            }
                case ES,REDIS->{
                    this.apiUrl = promptAndUpdate(br, "url of the API requests", this.apiUrl);
                    this.id = makeSimpleId(type);
                }
                default -> {
                    this.apiUrl = promptAndUpdate(br, "url of the API requests", this.apiUrl);
                    this.id = apiUrl;
                    this.docUrl = promptAndUpdate(br, "url of the API documents", this.docUrl);
                    this.orgUrl = promptAndUpdate(br, "url of the organization", this.orgUrl);
                    this.owner = promptAndUpdate(br, "API owner", this.owner);
                    this.protocols = promptAndUpdate(br, "protocol", this.protocols);
                    this.ticks = promptAndUpdate(br, "ticks", this.ticks);
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


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ServiceType getType() {
        return type;
    }

    public void setType(ServiceType type) {
        this.type = type;
    }

    public String getOrgUrl() {
        return orgUrl;
    }

    public void setOrgUrl(String orgUrl) {
        this.orgUrl = orgUrl;
    }

    public String getDocUrl() {
        return docUrl;
    }

    public void setDocUrl(String docUrl) {
        this.docUrl = docUrl;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String[] getTicks() {
        return ticks;
    }

    public void setTicks(String[] ticks) {
        this.ticks = ticks;
    }

    public void setProtocols(String[] protocols) {
        this.protocols = protocols;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public Params getApiParams() {
        return apiParams;
    }

    public void setApiParams(Params apiParams) {
        this.apiParams = apiParams;
    }

    public String[] getProtocols() {
        return protocols;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccountPubKey() {
        return accountPubKey;
    }

    public void setAccountPubKey(String accountPubKey) {
        this.accountPubKey = accountPubKey;
    }
    //    public List<FreeApi> getFreeApiList() {
//        return freeApiList;
//    }
//
//    public void setFreeApiList(List<FreeApi> freeApiList) {
//        this.freeApiList = freeApiList;
//    }
}
