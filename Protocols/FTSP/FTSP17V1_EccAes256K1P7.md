# FTSP17V1_EccAes256K1P7

## Summary

|Field|Content|
|---|---|
|Title|EccAes256K1P7|
|Type|FTSP|
|SN|17|
|Ver|1|
|Category|Encryption|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

Parent: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**`EccAes256K1P7`** is the legacy **`CryptoDataStr` / `CryptoDataByte`** stack in **`EccAes256K1P7`**: **secp256k1 ECDH** with **raw** `BigInteger.toByteArray()` shared secret, a **double-SHA-256** key derivation mixing **`SHA256(sharedSecret)`** and **16-byte IV**, **AES-256-CBC** via **`Aes256CbcP7`**, and a **4-byte `sum`** over **`symkey ‖ iv ‖ cipher`** (not over **`did`**). Supports **Symkey**, **Password**, **AsyOneWay**, **AsyTwoWay**. Display id: **`EccAes256K1P7@No1_NrC7`**.

## Specification

### ECDH

**`getSharedSecret`**: **`ECDHBasicAgreement.calculateAgreement(...).toByteArray()`** — variable-length **signed** big-endian (same family as [FTSP15](FTSP15V1_Ecc256K1AesCbc256.md) **`Ecc256K1`**, not FTSP11 padding).

### Symmetric key derivation (`asyKeyToSymkey`)

1. **`h1 = SHA256(sharedSecret)`** (single hash).
2. **`tmp = h1 ‖ iv`** (16-byte IV).
3. **`symkey = SHA256( SHA256(tmp) )`** (Java **`MessageDigest`**, two successive digests).

### AES layer

- **`Aes256CbcP7.encrypt` / `decrypt`**: **AES-256-CBC**, PKCS7, **16-byte IV**.

### `sum` (P7)

```
sum4 = first_4_bytes( SHA256( symkey ‖ iv ‖ cipher ) )
```

**Note:** [FTSP14](FTSP14V1_AesCbc256.md) **`CryptoDataByte.makeSum4`** uses **`symkey ‖ iv ‖ did`** — **different** from P7 **`getSum4`** above.

### Algorithm id

- Logical / JSON: **`EccAes256K1P7@No1_NrC7`** (`AlgorithmId.EccAes256K1P7_No1_NrC7`).
- Some file paths in code also reference **`FC_EccK1AesCbc256_No1_NrC7`** for interchange; treat **`EccAes256K1P7`** class behavior as normative for this FTSP.

## Interoperability

Implementing **P7** decrypt for legacy payloads **must** use **P7 `sum`** and **P7 KDF**, not **FTSP14/15** **`did`-based `sum`** alone.

## Test Vectors

### TV-FTSP17-1 — `aesEncrypt` / `decrypt` (AsyTwoWay)

|Field|Value|
|---|---|
|Sender **`prikeyA`**|secp256k1 **d = 1** (32-byte big-endian `00…01`)|
|Recipient **`pubkeyB`**|Compressed pubkey for **`ECKey.fromPrivate(999983)`**|
|`iv` (16 bytes, hex)|`1112131415161718191a1b1c1d1e1f20`|
|Plaintext (UTF-8)|**`FTSP17-P7`**|
|**`cipher` (16 bytes, hex)**|`5b38582d1ecf84ecdb39b49f6d3eb77d`|
|**`sum4` (4 bytes, hex)**|`2fe2df88`|

**Procedure:** `EccAes256K1P7.asyKeyToSymkey` + `aesEncrypt` / `decrypt` on **`CryptoDataByte`** (see test).

### Regression

FC-JDK **`FtspProtocolVectorTest.ftsp17_p7_aes_encrypt_decrypt`**.

## Developer JSON example

**AsyTwoWay:** sender secret = **fidB** private key, recipient pubkey = **fidA** compressed pubkey; IV = 16-byte `000102030405060708090a0b0c0d0e0f`; plaintext UTF-8 **`Hello world!`**. Keys: [FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples).

```json
{
  "type": "AsyTwoWay",
  "alg": "EccAes256K1P7@No1_NrC7",
  "cipher": "7a77SBiKKdil6IDogc4qRA==",
  "pubkeyA": "02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67",
  "pubkeyB": "030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a",
  "iv": "000102030405060708090a0b0c0d0e0f",
  "sum": "158017ac"
}
```

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-03-24|C_armX, No1_NrC7|Initial FTSP17 from `EccAes256K1P7.java`.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FTSP14|Modern CBC + `did`-bound `sum` (different formula).|
|FTSP15|EccK1 CBC via **`Ecc256K1`** + SHA512 — different KDF.|
|FVEP8|Encrypt types and fields.|

## Reference Implementation

[FC-JDK/src/main/java/core/crypto/old/EccAes256K1P7.java](../../FC-JDK/src/main/java/core/crypto/old/EccAes256K1P7.java)
