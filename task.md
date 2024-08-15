
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