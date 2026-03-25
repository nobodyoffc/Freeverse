# FTSP23V1_Sha256SymSignMsg

## Summary

|Field|Content|
|---|---|
|Title|Sha256SymSignMsg|
|Type|FTSP|
|SN|23|
|Ver|1|
|Category|Signing|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

Parent: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**`FC_Sha256SymSignMsg_No1_NrC7`** is **symmetric** “signing”: the **signature** is the **hex** encoding of **double SHA-256** over **`msgBytes ‖ symkey`**. **`keyName`** is the **hex** of the first **6** bytes of **`SHA256(symkey)`**. **`AlgorithmId` display name:** **`Sha256SymSignMsg@No1_NrC7`**. Bundle prefix: **`00 00 00 00 00 03`** (see [FVEP7](../FVEP/FVEP7V1_Signature.md)).

## Specification

### Sign

```
payload = msg.getBytes(UTF-8) ‖ symkey
digest = SHA256( SHA256( payload ) )
sign = Hex.toHex(digest)          // lowercase hex in reference paths
keyName = Hex( first_6_bytes( SHA256(symkey) ) )
```

Reference: **`Signature.symSign`**, **`sign(..., FC_Sha256SymSignMsg_No1_NrC7)`**.

### Verify

Recompute **`Hex( sha256x2( msgBytes ‖ key ) )`** and compare to **`sign`** (**`verifySha256SymSign`**).

### Binary bundle

1. **`algBytes`** = **`[0,0,0,0,0,3]`**.
2. **`keyNameBytes[6]`**.
3. **`signLen[2]`** + **`signBytes`** — **32 bytes** = **`SHA256(SHA256(msgBytes ‖ symkey))`**.

**Implementation note:** After **`fromBundle`**, reference **`bytesToStr`** sets **`sign`** to **Base64** of **`signBytes`**. After **`sign()`** for this algorithm, **`sign`** is **lowercase hex** (64 chars); callers building **`toBundle`** must populate **`sign`** in the form **`strToBytes`** expects (**Base64** of the 32-byte digest) or align with local **`Signature`** helpers. Verification **`verifySha256SymSign`** compares against **hex** of the digest.

## Test Vectors

### TV-FTSP23-1

|Field|Value|
|---|---|
|`symkey` (32 bytes, hex)|`505152535455565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f`|
|Message (UTF-8)|**`FTSP23 sym`**|
|**`sign` (64 hex, lowercase)**|`c0d6ca9c6fbef7da6fee37cc69eae562b56f4e86a63b5b194b3100fce2120dca`|

### Regression

FC-JDK **`FtspProtocolVectorTest.ftsp23_sha256_sym_sign_fixed`**.

## Developer JSON example

**Symkey** from [FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples); message UTF-8 **`Hello world!`**. **`sign`** is **64-char lowercase hex** of **`SHA256(SHA256(msg ‖ symkey))`** (see specification).

```json
{
  "keyName": "6ede688dea3b",
  "msg": "Hello world!",
  "sign": "9ec8def3c3cca2eac618522e54128f2f8d4c74a874a57bef9ee76e7e22c18b48",
  "alg": "Sha256SymSignMsg@No1_NrC7"
}
```

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-03-24|C_armX, No1_NrC7|Initial FTSP23 from `Signature.java`.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FVEP7|SymSign, `keyName`.|
|FTSP22 / FTSP24|Asymmetric signing profiles.|

## Reference Implementation

[FC-JDK/src/main/java/data/fcData/Signature.java](../../FC-JDK/src/main/java/data/fcData/Signature.java)
