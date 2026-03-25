# FTSP12V1_AesGcm256

## Contents

[Summary](#summary)

[Abstract](#abstract)

[Motivation](#motivation)

[Specification](#specification)

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
|Title|AesGcm256|
|Type|FTSP|
|SN|12|
|Ver|1|
|Category|Encryption|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

Parent rules: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**AesGcm256** is the Freeverse reference profile for **symmetric** **AES-256-GCM**: a **32-byte** key and **12-byte** IV encrypt plaintext with **`AES/GCM/NoPadding`** (BouncyCastle **BC**), **128-bit** authentication tag, ciphertext layout **ciphertext ∥ tag**. The **`AlgorithmId`** is **`AesGcm256@No1_NrC7`** (bundle prefix last byte **`0x03`**). It is used with **`EncryptType.Symkey`**; envelope rules (`iv`, `cipher`, **`keyName`** in bundles, no **`sum`**) follow [FVEP8V1_Encryption](../FVEP/FVEP8V1_Encryption.md).

## Motivation

- **AEAD** for data at rest and APIs when the **symkey** is already shared or derived elsewhere.
- **Byte-level parity** with the FC-JDK `AesGcm256` helpers and `Encryptor.encryptBySymkeyBase` for GCM.

## Interoperability

Implementations claiming compatibility with **FTSP12** MUST:

1. Use **AES-256** (`key` length **32**), **GCM** with **128-bit tag**, **no padding**, IV length **12** bytes.
2. Use the same **JCA-style** API semantics as the reference: **`GCMParameterSpec(128, iv)`**, cipher **`AES/GCM/NoPadding`**. Other crypto libraries MUST produce **identical ciphertext** for the same key, IV, plaintext, and **empty AAD** (reference does not supply additional authenticated data).
3. Treat **`cipher`** as **ciphertext ∥ tag** (tag is the **last 16 bytes** of `cipher`).
4. For **`did`** (when used): **`SHA256(SHA256(plaintext))`** over the **exact** decrypted plaintext bytes (see [FVEP2](../FVEP/FVEP2V1_ID.md) / FVEP8).
5. For bundles: **`keyName` = first 6 bytes of `SHA256(symkey)`** (see FVEP8).

**Provider note:** The reference registers **BouncyCastle** (`BC`). Another provider is acceptable only if outputs are **byte-identical** to the test vectors below.

## Specification

### Algorithm ID

|Form|Value|
|---|---|
|Display name (JSON `alg`)|`AesGcm256@No1_NrC7`|
|Enum (reference)|`AlgorithmId.FC_AesGcm256_No1_NrC7`|
|Bundle `algBytes` (6 bytes)|`00 00 00 00 00 03`|

### Parameters

|Parameter|Value|
|---|---|
|Key|**32** bytes (AES-256)|
|IV / nonce|**12** bytes (96-bit GCM nonce)|
|Cipher|AES, **GCM**, no padding|
|Transform (reference)|`AES/GCM/NoPadding`|
|Provider (reference)|**BC** (BouncyCastle)|
|Auth tag|**128** bits (16 bytes), appended to ciphertext by the JCA GCM implementation|

### Procedure

**Encrypt**

1. `key.length` MUST be **32**; otherwise the reference sets an error (`Code4008WrongKeyLength`).
2. Build **`GCMParameterSpec(128, iv)`** and initialize **`Cipher`** in **`ENCRYPT_MODE`** with **`SecretKeySpec(key, "AES")`**.
3. **AAD:** The reference path does **not** call `Cipher.updateAAD`; treat as **no AAD** (empty).
4. Encrypt the plaintext (streaming or one-shot); **`cipher`** output is **encrypted body ∥ tag** (JCA `doFinal` / streaming equivalent).

**Decrypt**

1. Require **`key`** non-null, length **32**; **`iv`** non-null (reference errors: invalid key / missing IV).
2. Read **entire** ciphertext (including tag) before **`doFinal`**-style verification (reference `AesGcm256.decryptStream` buffers the whole stream).
3. Initialize **`Cipher`** in **`DECRYPT_MODE`** with the same **key** and **`GCMParameterSpec(128, iv)`**; **no AAD**.
4. On success, reference **`decrypt(CryptoDataByte)`** sets **`did = SHA256(SHA256(plaintext))`** (double SHA-256 over UTF-8 or raw bytes as stored in `plaintext`) and **`data`**; **`sum`** is not used (AEAD only).

### Reference pseudocode (normative)

```
function aes_gcm256_encrypt(key32, iv12, plaintext):
    assert length(key32) == 32 and length(iv12) == 12
    // AES/GCM/NoPadding, tag length 128 bits, AAD empty
    (ciphertext, tag16) = AES-GCM-Enc(key32, iv12, plaintext, aad = empty)
    return ciphertext || tag16

function aes_gcm256_decrypt(key32, iv12, cipher_with_tag):
    assert length(key32) == 32 and length(iv12) == 12
    assert length(cipher_with_tag) >= 16
    return AES-GCM-Dec(key32, iv12, cipher_with_tag, aad = empty)
```

Use [FTSP13](FTSP13V1_HKDF.md) when the **32-byte key** is derived via HKDF (e.g. [FTSP11](FTSP11V1_Ecc256K1AesGcm256.md)).

### EncryptType and bundle (FVEP8)

|Item|Value|
|---|---|
|`EncryptType`|**Symkey** (byte **0**)|
|Binary bundle after `algBytes` + type| **`keyName` [6]** + **`iv` [12]** + **`cipher` [variable]** |
|`keyName`|First **6** bytes of **`SHA256(symkey)`** (FVEP8)|
|`sum`|Omitted for this profile|

JSON interchange: **`type`**: `Symkey`, **`alg`**, **`iv`** (hex), **`cipher`** (Base64), **`keyName`** (hex) as in FVEP8.

### Streaming API

- **`encrypt` / `encryptStream`**: stream plaintext in, ciphertext (with tag) out via `Encryptor.encryptBySymkeyBase`.
- **`decryptStream`**: buffers full ciphertext from the input stream before **`doFinal`** (suitable for bounded payloads; very large streams may need an implementation-defined chunked strategy outside this document).

## Test Vectors

### TV-AESGCM-1 — one-shot encrypt

|Field|Hex / value|
|---|---|
|`key` (32 bytes)|64 × `ab`|
|`iv` (12 bytes)|`000102030405060708090a0b`|
|Plaintext|UTF-8 string **`FTSP12`** (6 bytes)|
|**`cipher`** (body + tag, 22 bytes)|`660f550e7f255af454294635a43a7606871a4f55eed7`|

**Decrypt check:** `aes_gcm256_decrypt` with the same **`key`**, **`iv`**, and **`cipher`** MUST yield plaintext **`FTSP12`**.

### TV-AESGCM-2 — `did` for plaintext `a`

|Field|Value|
|---|---|
|Plaintext|single byte `0x61` (ASCII **`a`**)|
|**`did`** (32 bytes, hex)|`bf5d3affb73efd2ec6c36ad3112dd933efed63c4e1cbffcfa88e2759c144f2d8` (`SHA256(SHA256(plaintext))`)|

**Verification:** Ciphertext for TV-AESGCM-2 depends on key/IV; **`did`** depends only on plaintext and MUST match when using the reference double-SHA-256 definition.

**Reference run:** Verified with FC-JDK + BouncyCastle **`AES/GCM/NoPadding`** (`BC`), 2026-03-24.

### Regression

FC-JDK **`FtspProtocolVectorTest.ftsp12_aesgcm_tv1_and_roundTrip`** asserts **TV-AESGCM-1** and decrypt round-trip ([FtspProtocolVectorTest.java](../../FC-JDK/src/test/java/core/crypto/FtspProtocolVectorTest.java)).

## Developer JSON example

**Symkey** profile: shared **symkey** and IV = 12-byte `000102030405060708090a0b`; plaintext UTF-8 **`Hello world!`**. Keys: [FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples).

```json
{
  "type": "Symkey",
  "alg": "AesGcm256@No1_NrC7",
  "cipher": "UI45IX1b7PqEBnTZPDJB6Z8ANRkmIoRBLnYT/Q==",
  "keyName": "6ede688dea3b",
  "iv": "000102030405060708090a0b"
}
```

## Security Considerations

- **IV uniqueness:** MUST NOT reuse **`iv`** with the same **32-byte** key; reuse breaks GCM confidentiality and integrity.
- **Key handling:** **`symkey`** MUST NOT appear in public JSON; protect at rest and in memory.
- **Authentication:** Relies on GCM tag verification; failed decrypt indicates tampering or wrong key/IV.
- **Tag length:** Fixed at **128** bits in the reference; do not truncate.

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-03-24|C_armX, No1_NrC7|`AesGcm256` / FVEP8; interop checklist, pseudocode, TV-AESGCM-1–2.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FTSP0|FTSP governance.|
|FTSP11|ECDH + HKDF + this GCM primitive for asymmetric envelope.|
|FTSP14|CBC + `sum` symmetric profile (legacy).|
|FTSP13|HKDF when `symkey` is derived via HKDF.|
|FVEP8|Symkey envelope, `keyName`, IV length, no `sum` for GCM.|
|FVEP2|`did` definition (double SHA-256 of plaintext) after decrypt.|

## Reference Implementation

|Component|Location|
|---|---|
|`AesGcm256`| [FC-JDK/src/main/java/core/crypto/Algorithm/AesGcm256.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/AesGcm256.java) |
|`Encryptor.encryptBySymkeyBase`| [FC-JDK/src/main/java/core/crypto/Encryptor.java](../../FC-JDK/src/main/java/core/crypto/Encryptor.java) |
|`AlgorithmId`| [FC-JDK/src/main/java/data/fcData/AlgorithmId.java](../../FC-JDK/src/main/java/data/fcData/AlgorithmId.java) |
|`CryptoDataByte`| [FC-JDK/src/main/java/core/crypto/CryptoDataByte.java](../../FC-JDK/src/main/java/core/crypto/CryptoDataByte.java) |
