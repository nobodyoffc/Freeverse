# FTSP15V1_Ecc256K1AesCbc256

## Contents

[Summary](#summary)

[Abstract](#abstract)

[Motivation](#motivation)

[Interoperability](#interoperability)

[Specification](#specification)

[Normative ECDH output (legacy)](#normative-ecdh-output-legacy-secp256k1)

[Normative SHA512 key derivation](#normative-sha512-key-derivation)

[Composition with FTSP14](#composition-with-ftsp14)

[Test Vectors](#test-vectors)

[Developer JSON example](#developer-json-example)

[Security Considerations](#security-considerations)

[Versioning](#versioning)

[Related Protocols](#related-protocols)

[Reference Implementation](#reference-implementation)

---

## Summary

|Field|Content|
|---|---|
|Title|Ecc256K1AesCbc256|
|Type|FTSP|
|SN|15|
|Ver|1|
|Category|Encryption / KeyExchange|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

Parent rules: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**Ecc256K1AesCbc256** is the Freeverse **legacy** profile **secp256k1 ECDH → SHA-512 key derivation → AES-256-CBC** (PKCS#7). The **16-byte** nonce is both the **CBC IV** and the **leading** part of the KDF input. ECDH uses **`Ecc256K1.getSharedSecret`** (raw **`BigInteger.toByteArray()`**, **not** the 32-byte padded encoding in [FTSP11](FTSP11V1_Ecc256K1AesGcm256.md)). Symmetric encryption, **`did`**, and **`sum`** follow [FTSP14](FTSP14V1_AesCbc256.md). **`AlgorithmId`**: **`EccK1AesCbc256@No1_NrC7`**; bundle prefix last byte **`0x02`**. Envelope: [FVEP8V1_Encryption](../FVEP/FVEP8V1_Encryption.md).

## Motivation

- **Backward compatibility** with older FC encrypted payloads.
- **Clear separation** from **FTSP11** (HKDF + GCM + different ECDH encoding).

## Interoperability

A compliant implementation MUST:

1. Derive **`sharedSecret`** with **[Normative ECDH output (legacy)](#normative-ecdh-output-legacy-secp256k1)** — **not** **`Ecc256K1Hkdf.getSharedSecret`** / FTSP11.
2. Derive **`symkey`** with **[Normative SHA512 key derivation](#normative-sha512-key-derivation)** using the same **16-byte** nonce as **`iv`**.
3. Encrypt/decrypt with **[FTSP14](FTSP14V1_AesCbc256.md)** (CBC, PKCS7, **`did`**, **`sum`**).

Mixing FTSP11 ECDH/HKDF with this **`AlgorithmId`** will **not** interoperate.

## Specification

### Algorithm ID

|Form|Value|
|---|---|
|Display name (JSON `alg`)|`EccK1AesCbc256@No1_NrC7`|
|Enum (reference)|`AlgorithmId.FC_EccK1AesCbc256_No1_NrC7`|
|Bundle `algBytes` (6 bytes)|`00 00 00 00 00 02`|

### Parameters

|Parameter|Value|
|---|---|
|Curve|**secp256k1**|
|Private key|**32** bytes|
|Public key|**33** bytes, **compressed** SEC1|
|Nonce / IV|**16** bytes (same value for KDF and CBC)|
|Symmetric cipher|[FTSP14](FTSP14V1_AesCbc256.md) **`AesCbc256`**|

### Encryption / decryption (high level)

**Encrypt**

1. `symkey = asyKeyToSymkey(priKey, pubKey, nonce16)` (ECDH + SHA512 KDF below).
2. `cipher = FTSP14.encrypt(symkey, iv = nonce16, plaintext)`; populate **`did`** and **`sum`** per FTSP14 / `Encryptor.encryptBySymkeyBase`.

**Decrypt**

1. `symkey = asyKeyToSymkey(priKey, pubKey, nonce16)`.
2. Decrypt **`cipher`** with FTSP14; verify **`sum`** using stored **`did`** (or recompute **`did`** from plaintext and compare).

Reference **`Ecc256K1AesCbc256.decrypt`** temporarily sets **`FC_AesCbc256_No1_NrC7`** for the CBC step, then restores **`FC_EccK1AesCbc256_No1_NrC7`**.

### Streaming

**`encryptStream` / `decryptStream`** on **`Ecc256K1AesCbc256`** use the same KDF and **`AesCbc256`** stream helpers.

## Normative ECDH output (legacy, secp256k1)

This matches **`Ecc256K1.getSharedSecret(priKeyBytes, pubKeyBytes)`**:

1. Decode keys with **`KeyTools.prikeyFromBytes` / `pubkeyFromBytes`** (same curve as FTSP11).
2. **`ECDHBasicAgreement.calculateAgreement(peerPublic)`** → **`java.math.BigInteger`** shared secret.
3. **`sharedSecretBytes = Z.toByteArray()`** — **signed** big-endian, **variable length** (typically **32** or **33** bytes with possible leading **`0x00`**); **no** fixed 32-byte right-alignment step (contrast [FTSP11 § ECDH encoding](FTSP11V1_Ecc256K1AesGcm256.md#normative-ecdh-shared-secret-encoding-secp256k1)).

Interoperating code MUST use this **exact** byte sequence in the KDF input.

## Normative SHA512 key derivation

Let **`N`** = **16-byte** nonce (same as IV). Let **`S`** = **`sharedSecretBytes`** from ECDH above.

1. Allocate **`buf`** of length **`16 + length(S)`**.
2. **`buf = N || S`** (`System.arraycopy` **nonce first**, then **shared secret**).
3. **`digest = SHA512(buf)`** — reference **`Encryptor.sha512`** = Guava **`Hashing.sha512().hashBytes(buf).asBytes()`** (**64** bytes).
4. **`symkey = digest[0..31]`** (first **32** bytes).

5. Zeroize the **`S`** buffer in memory after use (reference **`Arrays.fill(sharedSecret, 0)`**).

## Composition with FTSP14

```
S = FTSP15.ecdh_legacy(priKey, pubKey)           // variable-length bytes
symkey = first_32_bytes( SHA512( nonce16 || S ) )
cipher, did, sum = FTSP14.encrypt(symkey, iv = nonce16, plaintext)
```

Decryption inverts the chain; **`sum`** MUST verify per FTSP14.

## Test Vectors

Normative one-shot hex ciphertexts SHOULD be added in a future revision.

### Regression

FC-JDK **`FtspProtocolVectorTest.ftsp15_ecc_k1_aes_cbc_roundTrip`** ([FtspProtocolVectorTest.java](../../FC-JDK/src/test/java/core/crypto/FtspProtocolVectorTest.java)) — **AsyTwoWay** encrypt/decrypt with fixed **16-byte IV**, sender **`PRI_SEND_ECC`** (bitcoinj **`ECKey.fromPrivate(5)`**), recipient **`ECKey.fromPrivate(999983)`**.

## Developer JSON example

IV = 16-byte `000102030405060708090a0b0c0d0e0f`; plaintext UTF-8 **`Hello world!`**. **AsyTwoWay:** sender **fidA** private key, recipient **fidB** pubkey. **AsyOneWay:** ephemeral secret = **fidB** private key, static recipient **fidA** pubkey (same ECDH product as the **AsyTwoWay** case here, so **`cipher`** / **`sum`** match). Keys: [FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples).

```json
{
  "type": "AsyTwoWay",
  "alg": "EccK1AesCbc256@No1_NrC7",
  "cipher": "Ges4WC0qqeYUPF2b8RLdzg==",
  "pubkeyA": "030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a",
  "pubkeyB": "02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67",
  "iv": "000102030405060708090a0b0c0d0e0f",
  "sum": "1a35a6a3"
}
```

```json
{
  "type": "AsyOneWay",
  "alg": "EccK1AesCbc256@No1_NrC7",
  "cipher": "Ges4WC0qqeYUPF2b8RLdzg==",
  "pubkeyA": "02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67",
  "iv": "000102030405060708090a0b0c0d0e0f",
  "sum": "1a35a6a3"
}
```

## Security Considerations

- **Legacy stack**: Prefer **[FTSP11](FTSP11V1_Ecc256K1AesGcm256.md)+[FTSP12](FTSP12V1_AesGcm256.md)** for new designs (AEAD, normative 32-byte ECDH encoding, HKDF).
- **Variable-length ECDH output** and **SHA512(IV‖secret)** are older choices; document precisely to avoid silent mismatch.
- **`sum`** is only **4** bytes; see FTSP14.

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-03-24|C_armX, No1_NrC7|Initial FTSP15: `Ecc256K1AesCbc256`, legacy ECDH + SHA512 + FTSP14.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FTSP0|FTSP governance.|
|FTSP11|Different ECDH encoding + HKDF + GCM — **not** interchangeable.|
|FTSP14|CBC, `did`, `sum` layer.|
|FVEP8|Asymmetric types, 16-byte IV for CBC, `sum` in bundle.|
|FVEP2|[FVEP2V1_ID](../FVEP/FVEP2V1_ID.md) — `did`.|

## Reference Implementation

|Component|Location|
|---|---|
|`Ecc256K1AesCbc256`| [FC-JDK/src/main/java/core/crypto/Algorithm/Ecc256K1AesCbc256.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/Ecc256K1AesCbc256.java) |
|`Ecc256K1` (ECDH + `sharedSecretToSymkey`)| [FC-JDK/src/main/java/core/crypto/Algorithm/Ecc256K1.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/Ecc256K1.java) |
|`AesCbc256`| [FC-JDK/src/main/java/core/crypto/Algorithm/AesCbc256.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/AesCbc256.java) |
|`Encryptor.sha512`| [FC-JDK/src/main/java/core/crypto/Encryptor.java](../../FC-JDK/src/main/java/core/crypto/Encryptor.java) |
