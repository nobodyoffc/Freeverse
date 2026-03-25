
# FUDP加密模式改进建议

## 问题
1. 直接问题：在FUDP当前的设计中，节点首次发起会话会同时启动密钥协商和用AsyTwoWay方式发送数据，当一方节点重启后会清空symkey，对方节点用原symkey发送的消息失败，触发持续的AsyTwoWay加密通讯，无法回到symkey通讯。
2. 深层问题：FUDP设计方案中对加密方式的选择逻辑设计不够清晰、简洁、完备。

## 方案
1. 对每个Peer增加可持久化的配置项encryptMode，值为SYMKEY(default)或ASYTWOWAY
2. SYMKEY模式
   1. AsyTwoWay仅用于密钥协商，不用于数据传输
   2. 没有有效symkey时，先进行密钥协商，成功后再传输数据
   3. 传输数据返回解密失败或无密钥的错误时，启动密钥协商，成功后重传原数据。
   4. 收到密钥建议时，己方如果存在ACTIVE状态的symkey则转换为DEPRECATED，然后继续密钥协商进程。
3. ASYTWOWAY模式：不需要密钥协商和密钥轮换，所有信息采用AsyTwoWay加密传输。
4. 模式切换：
   1. 主动切换：
      1. 用户或应用层主动修改encryptMode值
      2. 从SYMKEY切换到ASYTWOWAY时，原有symkey改为DEPRECATED。
      3. 从ASYTWOWAY切换到SYMKEY时，先启动密钥协商，协商成功后开始数据传输
   2. 被动切换：
      1. SYMKEY模式下，收到AsyTwoWay加密数据，立即切换到ASYTWOWAY模式，symkey转为DEPRECATED状态。
      2. ASYTWOWAY模式下：1. 收到symkey加密数据返回无密钥错误；2. 收到密钥建议，切换到SYMKEY模式，并继续密钥协商流程。

## 任务
1. 认真分析上述问题是否真实存在和有必要改进
2. 认真分析上述方案是否清晰、完备、可行，有没有更优的方案或局部的改进建议。
3. 如果有必要改进，请新建改进文档

## 参考
@FC-JDK/Docs/P2P_PROTOCOL_DESIGN.md , @FC-JDK/Docs/FUDP_NODE_IMPLEMENTATION_PLAN.md , @FC-JDK/Docs/P2P_PROTOCOL_OVERVIEW.md , 以及/fudp包内的相关代码

