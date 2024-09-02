Got new order file:newCashByFids0.json
Exception in thread "Thread-0" java.lang.RuntimeException: java.io.FileNotFoundException: /home/armx/freeverse/4c0c8d_newCashByFids/newCashByFids0.json (Permission denied)
at server.Counter.getNewCashListFromFile(Counter.java:327)
at server.Counter.getNewOrders(Counter.java:253)
at server.Counter.run(Counter.java:153)
at java.base/java.lang.Thread.run(Thread.java:1570)
Caused by: java.io.FileNotFoundException: /home/armx/freeverse/4c0c8d_newCashByFids/newCashByFids0.json (Permission denied)
at java.base/java.io.FileInputStream.open0(Native Method)
at java.base/java.io.FileInputStream.open(FileInputStream.java:213)
at java.base/java.io.FileInputStream.<init>(FileInputStream.java:152)
at server.Counter.getNewCashListFromFile(Counter.java:290)
... 3 more

[ ] user is not the account

[ ] one login one account
[ ] new user for disk server

19:55:06.607 [main] DEBUG clients.Client -- 1020:-25:Missing inputs
19:55:06.607 [main] ERROR configure.ApiAccount -- Failed to buy APIP service. Failed to broadcast TX.
V2--V1



11:22:05.338 [Thread-2] DEBUG mempool.MempoolScanner -- Got unconfirmed TX : 7a264b965584d1fa9ab495a2a8a139ead69a5f2de29b9b4d546c967871faa105
11:22:06.717 [Thread-3] ERROR webhook.Pusher -- Operate redis wrong when push newCashByFids.
java.lang.NullPointerException: Cannot invoke "String.trim()" because "in" is null
at java.base/jdk.internal.math.FloatingDecimal.readJavaFormatString(FloatingDecimal.java:1838)
at java.base/jdk.internal.math.FloatingDecimal.parseDouble(FloatingDecimal.java:110)
at java.base/java.lang.Double.parseDouble(Double.java:938)
at webhook.Pusher.pushDataList(Pusher.java:184)
at webhook.Pusher.putNewCashByFids(Pusher.java:160)
at webhook.Pusher.pushWebhooks(Pusher.java:148)
at webhook.Pusher.run(Pusher.java:71)
at java.base/java.lang.Thread.run(Thread.java:1570)

[x] Pusher 183 float price made wrong
[x] jedis 4: sid + newcash
[x] sessionName(sid+userId):hookuserid(sid+method+userId)--method+hookuserid
[x] disk服务的account与APIP相同，并且分配了人家的币
[x] dosearch size参数


[] list
[ ]ApipManager pusher
12:04:00.489 [Thread-0] DEBUG server.balance.BalanceInfo -- User balance is backed up to /Users/liuchangyong/Desktop/Freeverse/6999af_data/balance0.json
Exception in thread "Thread-3" java.lang.NullPointerException: Cannot invoke "webhook.DataOfIds.getFids()" because "data" is null
at webhook.Pusher.getNewCashList(Pusher.java:227)
at webhook.Pusher.putNewCashByFids(Pusher.java:150)
at webhook.Pusher.pushWebhooks(Pusher.java:140)
at webhook.Pusher.run(Pusher.java:78)
at java.base/java.lang.Thread.run(Thread.java:833)


[x] shower
[x] signIn
[ ]
Exception in thread "main" java.lang.NoClassDefFoundError: com/google/gson/internal/ConstructorConstructor$19
at com.google.gson.internal.ConstructorConstructor.newUnsafeAllocator(ConstructorConstructor.java:366)
at com.google.gson.internal.ConstructorConstructor.get(ConstructorConstructor.java:145)
at com.google.gson.internal.bind.ReflectiveTypeAdapterFactory.create(ReflectiveTypeAdapterFactory.java:129)
at com.google.gson.Gson.getAdapter(Gson.java:556)
at com.google.gson.Gson.toJson(Gson.java:834)
at com.google.gson.Gson.toJson(Gson.java:812)
at com.google.gson.Gson.toJson(Gson.java:783)
at javaTools.JsonTools.writeObjectToJsonFile(JsonTools.java:133)
at javaTools.JsonTools.writeObjectToJsonFile(JsonTools.java:140)
at server.Settings.writeToFile(Settings.java:501)
at startManager.DiskManagerSettings.saveSettings(DiskManagerSettings.java:229)
at startManager.DiskManagerSettings.initiateServer(DiskManagerSettings.java:64)
at startManager.StartDiskManager.main(StartDiskManager.java:55)
Caused by: java.lang.ClassNotFoundException: com.google.gson.internal.ConstructorConstructor$19
at java.base/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:641)
at java.base/jdk.internal.loader.ClassLoaders$AppClassLoader.loadClass(ClassLoaders.java:188)
at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:525)
... 13 more

[x] at javaTools.BytesTools.bytesMerger(BytesTools.java:300)
        at apip.apipData.Session.getSessionKeySign(Session.java:86)
        at clients.FcClientEvent.makeHeaderSession(FcClientEvent.java:784)
        at clients.FcClientEvent.initiate(FcClientEvent.java:166)
        at clients.FcClientEvent.<init>(FcClientEvent.java:120)
        at clients.Client.requestBase(Client.java:181)
        at clients.Client.ping(Client.java:342)
        at configure.ApiAccount.waitConfirmation(ApiAccount.java:830)
        at configure.ApiAccount.buyApi(ApiAccount.java:889)
        at clients.Client.checkResult(Client.java:222)
        at clients.Client.signInEcc(Client.java:363)
        at clients.Client.signInEcc(Client.java:461)
        at configure.ApiAccount.freshSessionKey(ApiAccount.java:1155)
        at configure.ApiAccount.checkSessionKey(ApiAccount.java:1052)
        at configure.ApiAccount.connectApip(ApiAccount.java:926)
        at configure.ApiAccount.connectApi(ApiAccount.java:110)
        at configure.Configure.checkAPI(Configure.java:766)
        at configure.Configure.checkAPI(Configure.java:743)
        at startManager.DiskManagerSettings.initiateServer(DiskManagerSettings.java:38)
        at startManager.StartDiskManager.main(StartDiskManager.java:55)

# webhook
## client
    1. register: manager, main, check
    2. recieve: web endpoint, file
    3. parse: manager, counter, run, getNewOrder 
    4. make order
## server
    1. recieve register: web, webhook, method,redis
    2. check data: manager, pusher
    3. balance:
    4. push: mangager, pusher

[] webhook pusher have to ensure that the endpoint got the data.
[] webhook user request since lastHeight when new starting.
[] apip client data size
[] wrong input when set api
[] waiting order when fromWebhook
[x] check share 0-1
[x] cover it?
[x] get method: the balance in header
[x] server.reward.Rewarder -- Get reward parameters failed. Check redis.

[x] updateBalance 简化
[x] balance 放到header中
[x] write 从reply中拿出来。

[x] Replace:DiscClient put get file crypto algorithm
[x] Add: ApiAccount userPubKey;
[x] Replace:DiscClient,DiskManager. password, all cipher crypto

[x] 测试swap client
[x] APIP SignInEcc change to (apiAccount,symKey)
[x] test apip signIn,disk signIN

[x] 所有接口：get 1个，post 多个
[x] 增加SignIn, LIST
[x] 增加fcdsl的查询，
[x] 存储结构：256*4，
* api需求：
  * fch数据：读 apip，nasa（指定地址数据）
  * feip：读 apip
  * 本服务数据：存 redis，file，es，apip（swap）
* 服务类型
  * 全自营：nasa+es+redis，FCH-FEIP-APIP
  * 全接口：apip+redis+file

[] OpenDrive
[] documents
    [] FEIP
    [] APIP
[] Client
    [] Freeverse
    [] FreeBuilder: nid, protocol, code, FEIP builder, APIP Builder
[] APPs
    [] freeSign3
    [] 

* 输入密文，解密，重新加密保存，返回明文字节
* 解密symKey加密密文
* 