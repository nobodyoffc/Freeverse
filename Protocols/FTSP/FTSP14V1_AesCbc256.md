# FTSP14V1_AesCbc256

## Contents

[Summary](#summary)

[Abstract](#abstract)

[Motivation](#motivation)

[Interoperability](#interoperability)

[Specification](#specification)

[Integrity: `did` and `sum`](#integrity-did-and-sum)

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
|Title|AesCbc256|
|Type|FTSP|
|SN|14|
|Ver|1|
|Category|Encryption|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

Parent rules: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**AesCbc256** is the Freeverse reference profile for **symmetric AES-256-CBC** with **PKCS#7** padding (`AES/CBC/PKCS7Padding`, BouncyCastle **BC**). The key is **32** bytes; the IV is **16** bytes. Unlike GCM profiles, CBC does not provide authentication—interchange uses a **4-byte `sum`** derived from **`symkey`**, **`iv`**, and **`did`**. The **`AlgorithmId`** display name is **`AesCbc256@No1_NrC7`**; bundle prefix last byte **`0x01`**. Envelope rules follow [FVEP8V1_Encryption](../FVEP/FVEP8V1_Encryption.md).

## Motivation

- **Legacy and API compatibility** with existing FC-JDK CBC payloads.
- **Explicit integrity** via **`sum`** where AEAD is not used.

## Interoperability

Implementations claiming compatibility with **FTSP14** MUST:

1. Use **AES-256** (`key` length **32**), **CBC**, **PKCS7** padding, **`AES/CBC/PKCS7Padding`**, IV length **16** bytes.
2. Compute **`did`** and **`sum`** exactly as in [Integrity: `did` and `sum`](#integrity-did-and-sum) when producing or verifying bundles/JSON.
3. Include **`sum`** (4 bytes) in the binary bundle after **`cipher`** per FVEP8 (and in JSON as 8 hex chars when used).
4. Match the reference **streaming encrypt** behavior for **`did`** during encryption (hash of plaintext as it is fed to the cipher), which reduces to **`SHA256(SHA256(plaintext))`** for a single continuous plaintext buffer—same as **`Hash.sha256x2(plaintext)`** after decryption.

**Provider:** Reference uses **BC**. Other providers MUST yield **byte-identical** ciphertext for the same key, IV, and plaintext.

## Specification

### Algorithm ID

|Form|Value|
|---|---|
|Display name (JSON `alg`)|`AesCbc256@No1_NrC7`|
|Enum (reference)|`AlgorithmId.FC_AesCbc256_No1_NrC7`|
|Bundle `algBytes` (6 bytes)|`00 00 00 00 00 01`|

### Parameters

|Parameter|Value|
|---|---|
|Key|**32** bytes|
|IV|**16** bytes|
|Transform (reference)|`AES/CBC/PKCS7Padding`|
|Provider (reference)|**BC**|
|Padding|PKCS#7 (PKCS5-compatible for AES block size 16)|

### Encryption procedure

1. Validate **`key.length == 32`** (reference otherwise sets error **`Code4008WrongKeyLength`**).
2. Initialize **`Cipher.getInstance("AES/CBC/PKCS7Padding", "BC")`** in **`ENCRYPT_MODE`** with **`SecretKeySpec(key, "AES")`** and **`IvParameterSpec(iv)`**.
3. Stream plaintext through **`CipherInputStreamWithHash`**: plaintext bytes are hashed for **`did`**; ciphertext bytes are hashed for **`cipherId`** (see reference `Encryptor.encryptBySymkeyBase`).
4. Output **`cipher`** is CBC ciphertext including padding (no separate tag).

### Decryption procedure

1. Require **`key`** (32 bytes) and **`iv`** (16 bytes).
2. Decrypt via **`Decryptor.decryptBySymkeyBase`** (same transform/provider).
3. After plaintext is recovered, set **`did = SHA256(SHA256(plaintext))`** (`Hash.sha256x2`) and verify **`sum`** with **`checkSum`** / **`makeSum4`** (see below).

## Integrity: `did` and `sum`

### `did` (content id)

For the decrypted plaintext bytes **`P`**:

```
did = SHA256( SHA256(P) )
```

Use the same definition as FVEP / **`Hash.sha256x2`** in FC-JDK. During **encryption**, the reference computes **`did`** as **`Decryptor.sha256( hasherPlaintext.hash().asBytes() )`** where **`hasherPlaintext`** is Guava **`Hashing.sha256()`** over all plaintext bytes—equivalent to the formula above for one-shot encryption.

### `sum` (4-byte integrity)

When **`symkey`**, **`iv`**, and **`did`** are all present:

```
sum = first_4_bytes( SHA256( symkey || iv || did ) )
```

Concatenation is raw bytes in order: **32-byte symkey**, then **16-byte iv**, then **32-byte did**; **`SHA256`** is a single standard SHA-256; take bytes **`[0..3]`** of the digest.

Reference: **`CryptoDataByte.makeSum4`**, **`Encryptor.encryptBySymkeyBase`** (calls **`makeSum4`** for non-GCM algorithms including **`FC_AesCbc256_No1_NrC7`**).

### EncryptType and bundle (FVEP8)

|Item|Value|
|---|---|
|`EncryptType`|**Symkey** (byte **0**)|
|Layout|`algBytes[6]` + `type[1]` + `keyName[6]` + `iv[16]` + `cipher[…]` + `sum[4]`|
|`keyName`|First **6** bytes of **`SHA256(symkey)`**.|

## Test Vectors

### TV-FTSP14-1 — symmetric CBC + `sum`

|Field|Value|
|---|---|
|`symkey` (32 bytes)|32 × **`0x0c`**|
|`iv` (16 bytes, hex)|`0d0e0f101112131415161718191a1b1c`|
|Plaintext (UTF-8)|**`FTSP14-CBC`**|
|**`cipher` (16 bytes, hex)**|`f1c7d497a2512fdf1c0bca304ffeaf75`|
|**`sum` (4 bytes, hex)**|`a7daa639`|

**Note:** Use `AesCbc256.encrypt(..., cryptoDataByte = null)` (or equivalent) so **`symkey`** and **`iv`** are both applied; the reference `else-if` chain in `AesCbc256.encrypt` skips **`iv`** if only **`symkey`** was supplied on a pre-built object.

### Regression

FC-JDK **`FtspProtocolVectorTest.ftsp14_aes_cbc_symkey_roundTrip_matches_expected_cipher`** ([FtspProtocolVectorTest.java](../../FC-JDK/src/test/java/core/crypto/FtspProtocolVectorTest.java)).

## Developer JSON example

**Symkey:** IV = 16-byte `000102030405060708090a0b0c0d0e0f`; plaintext UTF-8 **`Hello world!`**. **Password:** UTF-8 **`MyPassword`**, same IV; inner symkey = `SHA256(SHA256(passwordUTF8) ‖ iv)` per `Encryptor.passwordToSymkey`. Keys: [FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples).

```json
{
  "type": "Symkey",
  "alg": "AesCbc256@No1_NrC7",
  "cipher": "jqDcLsNdQ9+i8s+TdorP9g==",
  "keyName": "6ede688dea3b",
  "iv": "000102030405060708090a0b0c0d0e0f",
  "sum": "d162994e"
}
```

```json
{
  "type": "Password",
  "alg": "AesCbc256@No1_NrC7",
  "cipher": "dIxQ9wE5S214zqX9vDZKeA==",
  "keyName": "f25e7f92ceef",
  "iv": "000102030405060708090a0b0c0d0e0f",
  "sum": "2d917a6e"
}
```

## Security Considerations

- **CBC without AEAD** is **malleable**; **`sum`** binds **`symkey`**, **`iv`**, and **`did`** but is only **32 bits** of SHA-256 output—use for compatibility, not as a substitute for strong authentication in high-threat models.
- **IV reuse** with the same key is unsafe; use a fresh random **16-byte** IV per message.
- Prefer **[FTSP12](FTSP12V1_AesGcm256.md)** for new designs that need AEAD.

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-03-24|C_armX, No1_NrC7|Initial FTSP14: `AesCbc256`, `did`/`sum`, FVEP8 bundle.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FTSP0|FTSP governance.|
|FTSP15|ECDH + SHA512 + this CBC profile.|
|FTSP12|AES-GCM alternative (no `sum`).|
|FVEP8|16-byte IV, `sum` layout, Symkey bundle.|
|FVEP2|[FVEP2V1_ID](../FVEP/FVEP2V1_ID.md) — `did` style content hashing.|

## Reference Implementation

|Component|Location|
|---|---|
|`AesCbc256`| [FC-JDK/src/main/java/core/crypto/Algorithm/AesCbc256.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/AesCbc256.java) |
|`Encryptor.encryptBySymkeyBase`| [FC-JDK/src/main/java/core/crypto/Encryptor.java](../../FC-JDK/src/main/java/core/crypto/Encryptor.java) |
|`Decryptor.decryptBySymkeyBase`| [FC-JDK/src/main/java/core/crypto/Decryptor.java](../../FC-JDK/src/main/java/core/crypto/Decryptor.java) |
|`CryptoDataByte.makeSum4` / `checkSum`| [FC-JDK/src/main/java/core/crypto/CryptoDataByte.java](../../FC-JDK/src/main/java/core/crypto/CryptoDataByte.java) |
|`CipherInputStreamWithHash`| [FC-JDK/src/main/java/core/crypto/Algorithm/aesCbc256/CipherInputStreamWithHash.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/aesCbc256/CipherInputStreamWithHash.java) |
