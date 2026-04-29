# Freeverse

[]  1. Decryptor.java — likely a real AES-GCM bug in FC-JDK, fixed in Safe.
Safe uses GCMParameterSpec(128, iv) when the transformation is GCM; FC-JDK uses IvParameterSpec for GCM too.    
IvParameterSpec doesn't carry the authentication-tag length, which with GCM can silently produce wrong or
weakly-authenticated behavior depending on the JCE provider.  

[] news 
[] safe: import prikey to make pubkey
[] talk: fe
tch messages of myself
[] nasa swap, linode

[] default FAPI server

[] No DISK

[] rewrite startFapiServer and startFapiClient

[] FapiServer income and distribute.

[] FAPI: BASE (FEIP_API,FCH_API)，DISK，MAP，TALK，ROAD(relay)，DOCK(save and IO)，BANK(settle)
[] DISK:put,get,check,list
[] DOCK:put,get,consume
[] ROAD:relay
[] MAP:register fudp peer:fid -> ip,port,pubkey. probe heart beats. last seeing. RTT
[] BANK:deposit, pay, withdraw, settle
[] TALK: 

BASE，DISK，MAP，TALK，ROAD，DOCK，BANK

[] relay function in FUDP node.

[x] PROTOCOL("1","7", "Protocol")
[x] CODE("2","1", "Code")
[x] CID("3","4", "CID")
[x] NOBODY("4","1", "Nobody")
[x] SERVICE("5","3", "Service")
[x] MASTER("6","1", "Master")
[x] STATEMENT("8","1", "Statement")
[x] HOME("9","1", "Home")
[x] NOTICE_FEE("10","1", "NoticeFee")
[x] NID("11","1", "NID")
[x] MAIL("7","4", "Mail")
[x] CONTACT("12","3", "Contact")
[x] BOX("13","1", "Box")
[x] PROOF("14","1", "Proof")
[x] APP("15","1", "APP")
[x] REPUTATION("16","1", "Reputation")
[x] SECRET("17","3", "Secret")
[x] TEAM("18","1", "Team")
[x] SQUARE("19","4", "Square")
[x] TOKEN("20","1", "Token")
[x] TEXT("21","1", "Text")
[x] REMARK("22","1", "Remark")
[x] SOUND("23","1", "Sound")
[x] IMAGE("24","1", "Image")
[x] VIDEO("25","1", "Video");

[x] Freer.homepage -> locationMap
[x] apiProvider,service,params 扁平化
[x] when node2 restarted and node1 send msg to node2, they only communicate with AsyTwoWay.
    1. node1 send to node2 with encrypted msg with the symkey
    2. node2 failed to decrypt due to the absent of the symkey
    3. node2 send the error to node1 with AsyTwoWay
    4. node1 send msg to node2 with AsyTwoWay
    5. ...
    * plan
        1. only negotiate symkey with AsyTwoWay?
        2. only resend msg with AsyTwoWay when symkey error.
[x] when node2 was breakdown, node1 resend too many times.
[x] when node2 restarted, it failed to send msg to node2.
[x] 检查balance付费
[x] fudp://

[x] make output: if cltv, replace owner
[x] cash + lockTime, redeemScript
[x] parser + p2sh, cltv
[x] indices + p2sh
[x] news

[x] sound, image, video
[x] fields in fcdsl
1. name
2. categories 
3. regions
4. sites


[] disk box: newBox, toBox, boxList, showBox. 好像不必要，用户自己设置就可以了。

[x] didByNid
[x] cidAvatarByIds
[x] publish
[x] multisig -> multisig
[x] 新用户未输入dealer私钥
[x] 新建disk client，初始设置DISK服务失败
[x] 修改默认API，不能立即使用，重启后才生效。
[x] DISK put 失败。
以上三个的原因可能是diskClient未添加。
Exception in thread "main" java.lang.NullPointerException: Cannot invoke "clients.DiskClient.put(String)" because "startClient.StartDiskClient.diskClient" is null
at startClient.StartDiskClient.put(StartDiskClient.java:139)
at startClient.StartDiskClient.disk(StartDiskClient.java:82)
at startClient.StartDiskClient.main(StartDiskClient.java:68)
18:43:50.442 [Thread-0] INFO config.Settings -- Shutdown hook completed

[x] remove 'Stop' promote
[x] 当前api服务不可用时转向或添加其他。
[x] reset API service
[x] APIP在DISK前加载 tomcat: 名称更换为'1-APIP.war' 和 '2-DISK.war'


[x] the price should be 10s instead of 1s in order to pay to via. 
[x] how to broadcast?
[x] payoffMap test
[x] auto scan type
[x] nPrice saved into localDB

* 自动任务
  - 类型
    - 1 No automatic scanning
      2 Elasticsearch client scanning
      3 APIP client scanning
      4 Webhook notifications
      5 Monitor file changes
  - 需求
    - 订单扫描（可以采用webhook）
    - 定时清理数据 disk
    - 区块触发：mempool
  - 方案
    - 增加AutoTask类：
      - type（byTime,fileChange,dirChange)，
      - method,
      - intervalSec，
      - listenPath(block,opreturn)，
      - webhookDataLocation
    - module 的settings里增加autoTaskMap<String name,List<AutoTask> list>
    - runAutoTask检查每个module的map并运行
  - 问题
    - 用户设定:apip
    - webhook不需要单独设置，收到就执行了。


[] Android app suitable off line sending. UI independent.
[x] cd ==0;
[x] list in pages: continue?
[x] send
[x] imported sessionKey do not be saved.
[x] cid.cash == 0
* Home
    * EasyQR
    * Tool: off line tools
    * Cash
    * Secret
    * Disk
    * Hat
    * Box
* Society
    * Contact (Nobody, Master, Guide, Reputation)
    * Statement
    * Mail
    * Group
    * Team
* Market
    * Swap
    * Service
    * App
    * Token
    * Proof
* Factory
    * Protocol
    * Code
    * Nid
    * Info



[x] FcData id

[x] mempoolHandler
[x] test


[x] pay for share
[x] merge codeMsg: reply,crypto,talkUnit. 3 http, 4 crypto, 5 connect

# 结构
    1. Home：cash,Tool,contact,secret,
    2. Talk: contact,mail,talk
    3. Post: statement,proof,post
    4. Work：protocol,service,code,app
    5. Shop:swap,token
[] makeTx: necessary cash; amount; cd

[x] startFchClient batchMerge
[x] FeipClient: create,send
[x] Data class: makeDelete,makeStop,make

Talk
[] client:
    
    * pending
        - local storage of HATs. Into redis
        - load items from others. send file? read by timestamp? mark the recent start time. ask items from last time to recent start time.
    * display
        - get all new item from server since last time
        - all in one. marked as fid@talkId
        - from: cid or fid; name-idName; 
    * input:
        - to: default recent talkId; 'space' lock default recent talkId; '>fid@talkId '; up,down
        - send hat: as talkUnit
        - send file: <data://path> 
        - find: ?cid 'term'; ?group 'term'; ?team 'term'; ?hat 'did'; ?data 'did'
        - server: '>server ?'
    * storage:
        - memory: this time; last 10; in a queue;
        - recent in file:  all last 10 in one file
        - achieved: one talkId one file; talkId as file name; term in bundle; 
        - rule: 
            every new save to achieved; 
            when closing save last 10 into recent file;

[] server:

    * storage: every item saved to ES; every day find the items older than 7 days to delete.
[] frame
    transfer 

[] UDP:重传，

[] map: fid:{map:[],route:[],...,}

[] balance : charge request only. 0.01 satoshi


[x] data to MapDB:
file -> contact,mail,roomInfo,talkUnits,sessionKeyCipher
[x] pay to FID
[x] mail client; contact client; safe client
[x] remove me, server,anyone,everyone,client
[x] remove from
[x] Ask key to certain fid
[x] user is not the account

[x] one login one account
[x] new user for disk server
[x] Pusher 183 float price made wrong
[x] jedis 4: sid + newcash
[x] sessionName(sid+userId):hookuserid(sid+method+userId)--method+hookuserid
[x] disk服务的account与APIP相同，并且分配了人家的币
[x] dosearch size参数


[x] list
[x]ApipManager pusher
[x] shower
[x] signIn

[x] at javaTools.BytesTools.bytesMerger(BytesTools.java:300)
        at fcData.FcSession.getSessionKeySign(Session.java:86)
        at clients.ApipClientEvent.makeHeaderSession(FcClientEvent.java:784)
        at clients.ApipClientEvent.initiate(FcClientEvent.java:166)
        at clients.ApipClientEvent.<init>(FcClientEvent.java:120)
        at clients.FcClient.requestBase(Client.java:181)
        at clients.FcClient.ping(Client.java:342)
        at configure.ApiAccount.waitConfirmation(ApiAccount.java:830)
        at configure.ApiAccount.buyApi(ApiAccount.java:889)
        at clients.FcClient.checkResult(Client.java:222)
        at clients.FcClient.signInEcc(Client.java:363)
        at clients.FcClient.signInEcc(Client.java:461)
        at configure.ApiAccount.freshSessionKey(ApiAccount.java:1155)
        at configure.ApiAccount.checkSessionKey(ApiAccount.java:1052)
        at configure.ApiAccount.connectApip(ApiAccount.java:926)
        at configure.ApiAccount.connectApi(ApiAccount.java:110)
        at configure.Configure.checkAPI(Configure.java:766)
        at configure.Configure.checkAPI(Configure.java:743)
        at startManager.Settings.initiateServer(DiskManagerSettings.java:38)
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

java.lang.NullPointerException: Cannot invoke "data.fchData.Freer.getCid()" because the return value of "java.util.Map.get(Object)" is null
APIP3V1_Freer.CidAvatarByIds.doRequest(CidAvatarByIds.java:98)
APIP3V1_Freer.CidAvatarByIds.doGet(CidAvatarByIds.java:44)
javax.servlet.http.HttpServlet.service(HttpServlet.java:502)
javax.servlet.http.HttpServlet.service(HttpServlet.java:596)
org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:53)
initial.CorsFilter.doFilter(CorsFilter.java:20)