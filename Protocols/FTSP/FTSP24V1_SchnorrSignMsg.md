# FTSP24V1_SchnorrSignMsg

## Summary

|Field|Content|
|---|---|
|Title|SchnorrSignMsg|
|Type|FTSP|
|SN|24|
|Ver|1|
|Category|Signing|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

Parent: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**`FC_SchnorrSignMsg_No1_NrC7`** signs **`msg`** with **BIP340-style Schnorr** helpers in **`SchnorrSignature`**: message is hashed as **`msgHash = SHA256( SHA256( msg.getBytes(UTF-8) ) )`**, signature bytes from **`schnorr_sign(msgHash, privKeyBigInteger)`**, and the **`sign`** field is **Base64** of **`pubkey (33 bytes) ‖ schnorr_signature`**. **`fid`** is **`KeyTools.prikeyToFid(key)`**. Bundle prefix: **`00 00 00 00 00 05`**. Display name: **`SchnorrSignMsg@No1_NrC7`**.

## Specification

### Sign (`schnorrMsgSign`)

1. **`ECKey.fromPrivate(priKey)`** → **`privKeyBigInteger`**, **`pubKey` (33-byte compressed)**.
2. **`msgHash = Hash.sha256x2( msg.getBytes(StandardCharsets.UTF_8) )`**.
3. **`sig = SchnorrSignature.schnorr_sign(msgHash, privKeyBigInteger)`**.
4. **`sign = Base64( pubkey ‖ sig )`**.

### Verify (`schnorrMsgVerify`)

1. **`msgHash = Hash.sha256x2( msg.getBytes(UTF-8) )`**.
2. Decode Base64 → **`pubSignBytes`**; **`pubKey = first 33 bytes`**, **`sign = remainder`**.
3. Check **`fid`** matches **`KeyTools.pubkeyToFchAddr(hex(pubKey))`**.
4. **`SchnorrSignature.schnorr_verify(msgHash, pubKey, sign)`**.

### Bundle

Same layout as **FTSP22** after prefix: **`alg[6]`** + **`fid hash160[20]`** + **`len[2]`** + **`signBytes`** + **`msgBytes`**.

## Test Vectors

### Regression

FC-JDK **`FtspProtocolVectorTest.ftsp24_schnorr_sign_msg_verify`** — fixed **32-byte** private key (hex `606162…7f`), message **`FTSP24 Schnorr`**, **`Signature.schnorrMsgVerify`** must succeed.

## Developer JSON example

Private key = **fidA** 32-byte hex ([FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples)); message UTF-8 **`Hello world!`**. **`sign`** is Base64 per **`Signature.toNiceJson()`**.

```json
{
  "fid": "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK",
  "msg": "Hello world!",
  "sign": "Awvh1+Yz/rIzinSoYOdtiTusUl81pYE8t7IeJ7obyDEqDghw/vaARASpZ3BgAamFeZzyqAVXoTFS6RveXGoA2/LTnldNJwh/Sbzew3442l0J627Mv92L+afy5aNDrTHAeg==",
  "alg": "SchnorrSignMsg@No1_NrC7"
}
```

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-03-24|C_armX, No1_NrC7|Initial FTSP24 from `Signature.java` + `SchnorrSignature`.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FVEP7|Signature bundle layout.|
|FTSP22|ECDSA message signing alternative.|
|`FC_SchnorrSignTx_No1_NrC7`|Different **`AlgorithmId`** for tx signing — not this FTSP.|

## Reference Implementation

|Component|Location|
|---|---|
|`Signature`| [FC-JDK/src/main/java/data/fcData/Signature.java](../../FC-JDK/src/main/java/data/fcData/Signature.java) |
|`SchnorrSignature`| [FC-JDK/src/main/java/core/fch/SchnorrSignature.java](../../FC-JDK/src/main/java/core/fch/SchnorrSignature.java) |
