# FTSP21V1_EccK1ChaCha20

## Summary

|Field|Content|
|---|---|
|Title|EccK1ChaCha20|
|Type|FTSP|
|SN|21|
|Ver|1|
|Category|Encryption / KeyExchange|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

Parent: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**EccK1ChaCha20** chains **secp256k1 ECDH** with **32-byte** shared secret (**`Ecc256K1Hkdf.getSharedSecret`** — same encoding as [FTSP11](FTSP11V1_Ecc256K1AesGcm256.md)), **[FTSP13](FTSP13V1_HKDF.md)** with **`info = ASCII "hkdf-chacha20"`** (not the string **`hkdf`**), **12-byte** salt = nonce, and **[FTSP20](FTSP20V1_ChaCha20.md)** ChaCha20 encryption (including **`did`** + **`sum`**). **`AlgorithmId`**: **`EccK1ChaCha20@No1_NrC7`**; bundle last byte **`0x07`**.

## Composition (normative)

```
Z = Ecc256K1Hkdf.getSharedSecret(priKey, pubKey)   // 32 bytes, FTSP11-style
symkey = HKDF.hkdf(Z, salt = nonce12, info = "hkdf-chacha20", L = 32)
... ChaCha20.encrypt per FTSP20 ...
```

**Domain separation:** **`info`** MUST be **`hkdf-chacha20`** bytes — using **`hkdf`** would **not** match FC-JDK.

## Test Vectors

### Regression

FC-JDK **`FtspProtocolVectorTest.ftsp21_ecc_k1_chacha_roundTrip`** — **AsyTwoWay** with **`PRI_SEND_ECC`** ( **`ECKey.fromPrivate(5)`** ), recipient **`ECKey.fromPrivate(999983)`**, IV **`7172737475767778797a7b7c`**, plaintext **`FTSP21`**.

## Developer JSON example

**AsyTwoWay:** sender **fidA**, recipient **fidB**; IV = 12-byte `000102030405060708090a0b`; plaintext UTF-8 **`Hello world!`**. Keys: [FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples).

```json
{
  "type": "AsyTwoWay",
  "alg": "EccK1ChaCha20@No1_NrC7",
  "cipher": "wKL8FC/s3JnNQAua",
  "pubkeyA": "030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a",
  "pubkeyB": "02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67",
  "iv": "000102030405060708090a0b",
  "sum": "45103c43"
}
```

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-03-24|C_armX, No1_NrC7|Initial FTSP21 from `Ecc256K1ChaCha20.java`.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FTSP11|Same ECDH encoding; different **`info`** and symmetric cipher.|
|FTSP13|HKDF.|
|FTSP20|ChaCha20 + `sum`.|
|FVEP8|Envelope.|

## Reference Implementation

[FC-JDK/src/main/java/core/crypto/Algorithm/Ecc256K1ChaCha20.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/Ecc256K1ChaCha20.java)
