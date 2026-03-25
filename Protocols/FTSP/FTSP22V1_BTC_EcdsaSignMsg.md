# FTSP22V1_BTC_EcdsaSignMsg

## Summary

|Field|Content|
|---|---|
|Title|BTC-EcdsaSignMsg|
|Type|FTSP|
|SN|22|
|Ver|1|
|Category|Signing|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

Parent: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**`BTC_EcdsaSignMsg_No1_NrC7`** signs a **string message** using **bitcoinj `ECKey.signMessage`**: ECDSA over **secp256k1** in Bitcoin’s **“sign message”** encoding (compact recoverable-style string, **Base64** in JSON **`sign`**). Verification uses **`ECKey.signedMessageToKey(msg, sign)`** and compares signer **FID** to **`fid`**. **`AlgorithmId` display name:** **`BTC-EcdsaSignMsg@No1_NrC7`**. Bundle prefix: **`00 00 00 00 00 04`** (see [FVEP7](../FVEP/FVEP7V1_Signature.md)).

## Specification

### Inputs

- **`msg`**: Java **`String`**; signed as in **bitcoinj** (same bytes as **`msg.getBytes()`** usage in reference **`sign`** path for **`ECKey`**).
- **`key`**: **32-byte** secp256k1 private key; **`ECKey.fromPrivate(key)`**.

### Sign

```
sign = Base64( ECKey.signMessage(msg) )
fid = KeyTools.prikeyToFid(key)
```

### Verify

Recover public key from **`(msg, sign)`**, derive **FID** from pubkey, **`fid.equals(signFid)`**.

### Binary bundle (`Signature.toBundle`)

1. **`algBytes[6]`** = **`[0,0,0,0,0,4]`**.
2. **`fidBytes[20]`** — **HASH160** of FCH address (**`KeyTools.addrToHash160(fid)`**).
3. **`signLen[2]`** — big-endian **16-bit** length of **`signBytes`**.
4. **`signBytes`** — Base64-decoded signature.
5. **`msgBytes`** — remainder (message bytes; JSON round-trip uses string encoding per reference).

Parse: **`fromBundle`** inverts; length via **`BytesUtils.intTo2ByteArray` / `bytes2ToIntBE`**.

## Test Vectors

### Regression

FC-JDK **`FtspProtocolVectorTest.ftsp22_btc_ecdsa_sign_message_verify`** — fixed **32-byte** private key (hex `404142…5f`), message **`FTSP22 BTC message`**, **`Signature.verify()`** must succeed.

## Developer JSON example

Private key = **fidA** 32-byte hex ([FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples)); message UTF-8 **`Hello world!`**. JSON matches **`Signature.toNiceJson()`** (bitcoinj **`signMessage`**).

```json
{
  "fid": "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK",
  "msg": "Hello world!",
  "sign": "H72WqwfL1H4QGvR3NnPacQjH+s578JkBNQ6Q/2DhAt5CJKNIgd1AzmUXeET5ofzCm7W0EfuiJhRoJzHxDDa9my4=",
  "alg": "BTC-EcdsaSignMsg@No1_NrC7"
}
```

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-03-24|C_armX, No1_NrC7|Initial FTSP22 from `Signature.java` + bitcoinj.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FVEP7|Signature envelope, bundle prefix table.|
|FTSP23 / FTSP24|Other `AlgorithmId` signing profiles.|

## Reference Implementation

[FC-JDK/src/main/java/data/fcData/Signature.java](../../FC-JDK/src/main/java/data/fcData/Signature.java)
