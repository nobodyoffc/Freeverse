# FTSP20V1_ChaCha20

## Summary

|Field|Content|
|---|---|
|Title|ChaCha20|
|Type|FTSP|
|SN|20|
|Ver|1|
|Category|Encryption|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

Parent: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**ChaCha20** (reference) is **symmetric** stream encryption: **`Cipher.getInstance("ChaCha20", "BC")`**, **32-byte** key, **12-byte** nonce/IV, **`IvParameterSpec`**. There is **no** built-in authentication — the reference sets **`did = SHA256( SHA256(plaintext) )`** during encrypt (via Guava SHA-256 over plaintext, then **`Decryptor.sha256`**) and calls **`makeSum4`** (**`sum` = first 4 bytes of `SHA256(symkey ‖ iv ‖ did)`**, same as other non-GCM profiles in **`CryptoDataByte`**). **`AlgorithmId`**: **`ChaCha20@No1_NrC7`**; bundle last byte **`0x06`**.

## Specification

- **Transform:** **`ChaCha20`** (no `/CTR` suffix in reference).
- **Encrypt:** stream plaintext, hash plaintext for **`did`**, **`makeSum4`**, set **`FC_ChaCha20_No1_NrC7`**.
- **Decrypt:** stream decrypt; **`makeDid()`** on plaintext; **`checkSum()`** against **`sum`**.

## Test Vectors

### TV-FTSP20-1 — Symkey ChaCha20

|Field|Value|
|---|---|
|`symkey` (32 bytes, hex)|`4142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f60`|
|`iv` / nonce (12 bytes, hex)|`6162636465666768696a6b6c`|
|Plaintext (UTF-8)|**`FTSP20-ChaCha`**|
|**`cipher` (hex)**|`9a01dc6f2230c92a21cb513838`|
|**`sum` (4 bytes, hex)**|`d5e5a7d6`|

### Regression

FC-JDK **`FtspProtocolVectorTest.ftsp20_chacha20_sym_roundTrip`**.

## Developer JSON example

**Symkey** from [FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples); IV = 12-byte `000102030405060708090a0b`; plaintext UTF-8 **`Hello world!`**.

```json
{
  "type": "Symkey",
  "alg": "ChaCha20@No1_NrC7",
  "cipher": "f6Z5/0sfqv6zMjL/",
  "keyName": "6ede688dea3b",
  "iv": "000102030405060708090a0b",
  "sum": "bf818243"
}
```

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-03-24|C_armX, No1_NrC7|Initial FTSP20 from `ChaCha20.java`.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FTSP12|AES-GCM AEAD alternative.|
|FTSP14|`sum` semantics overlap (via `CryptoDataByte`).|
|FTSP21|ECDH + HKDF + ChaCha20.|
|FVEP8|12-byte IV; `sum` required for ChaCha profile.|

## Reference Implementation

[FC-JDK/src/main/java/core/crypto/Algorithm/ChaCha20.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/ChaCha20.java)
