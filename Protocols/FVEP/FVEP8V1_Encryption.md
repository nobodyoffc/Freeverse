# FVEP8V1_Encryption

## Contents

[Summary](#summary)

[Abstract](#abstract)

[Motivation](#motivation)

[Specification](#specification)

[Examples](#examples)

[Versioning](#versioning)

[Related Protocols](#related-protocols)

[Reference Implementation](#reference-implementation)

---

## Summary

|Field|Content|
|---|---|
|Title|Encryption|
|Type|FVEP|
|SN|8|
|Ver|1|
|Category|Foundation|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-22|
|PID||

## Abstract

This protocol defines the general structure and rules for **encrypting and decrypting** data in the Freeverse ecosystem: the four **encryption types** (symmetric key, password, asymmetric one-way, asymmetric two-way), the **logical fields** carried in JSON (`CryptoDataStr`) and optional **binary bundle** layout (`CryptoDataByte`), and **integrity / identity** fields (`did`, `cipherId`, `sum`, `keyName`). Symmetric cipher algorithms, ECDH key agreement, and stream transforms are specified in **FTSP**; FVEP8 defines the **envelope** and **type semantics** shared by all registered `AlgorithmId` values used with `EncryptType`.

## Motivation

Applications need one consistent model for “encrypted payloads”: whether the user holds a symkey, a password, or key pairs for ECDH-based encryption. FVEP8 aligns HTTP/JSON APIs, file encryption, and binary bundles so different implementations can exchange ciphertext safely. Low-level crypto parameters remain in FTSP to avoid duplicating algorithm specs in every ecosystem document.

## Specification

### Definitions

|Term|Description|
|---|---|
|**EncryptType**|Enumerates how the **content encryption key (symkey)** is established or used.|
|**Symkey (0)**|A **32-byte** symmetric key encrypts the plaintext directly (after optional algorithm-specific setup). This is the **base** mode: other types derive a symkey (or equivalent) before applying the symmetric cipher.|
|**Password (3)**|A user **password** (UTF-8, SHOULD be at most **64 bytes** before KDF) is converted to a symkey using the **IV** embedded in the payload: `symkey = SHA-256( SHA-256(passwordUTF8) \|\| iv )` (byte concatenation; see reference `Encryptor.passwordToSymkey`).|
|**AsyOneWay (1)**|Encrypt for a recipient **pubkey B**. An **ephemeral** key pair is generated; **pubkeyA** in the ciphertext header is the ephemeral public key. Decryption uses the recipient's **prikey** matching **pubkeyB** (and the recorded **pubkeyA**).|
|**AsyTwoWay (2)**|Encrypt using **prikeyA** (sender) and **pubkeyB** (recipient). Decryption is possible with **(prikeyB, pubkeyA)** or **(prikeyA, pubkeyB)** depending on role; ECDH derives the symkey per FTSP.|
|**alg**|Symmetric or composite algorithm id (`AlgorithmId`), serialized in JSON by **display name** (e.g. `AesGcm256@No1_NrC7`, `EccK1AesGcm256@No1_NrC7`).|
|**cipher**|Ciphertext bytes, Base64-encoded in JSON.|
|**iv**|Initialization vector, **hex-encoded** in JSON. Length depends on algorithm profile (see IV rules).|
|**sum**|Four-byte integrity tag for algorithms **without** built-in AEAD authentication in the reference stack (e.g. AES-CBC, ChaCha20 in FC-JDK). **Omit** from JSON when using AES-GCM profiles that rely on the cipher's auth tag only (see rules).|
|**did**|**Data ID**: `SHA-256( SHA-256( plaintext ) )` — identifies the decrypted content (FVEP2 DID-style double hash on raw plaintext bytes).|
|**cipherId**|Optional identifier of ciphertext stream/hash context in streaming APIs (implementation-dependent; used in some streaming helpers).|
|**keyName**|First **6 bytes** of `SHA-256(symkey)` — identifies which symkey was used without revealing it. Required in bundle form for **Symkey** type in the reference implementation.|

### Encryption type byte values

|EncryptType|Byte value|
|---|---|
|Symkey|0|
|AsyOneWay|1|
|AsyTwoWay|2|
|Password|3|

### Algorithm ID bytes (bundle prefix, encryption)

First **6 bytes** of a crypto bundle; trailing byte distinguishes symmetric/AEAD suite in the reference:

|Last byte|Typical `AlgorithmId`|
|---|---|
|1|`FC_AesCbc256_No1_NrC7`|
|2|`FC_EccK1AesCbc256_No1_NrC7`|
|3|`FC_AesGcm256_No1_NrC7`|
|4|`FC_EccK1AesGcm256_No1_NrC7`|
|5|`FC_X25519AesGcm256_No1_NrC7`|
|6|`FC_ChaCha20_No1_NrC7`|
|7|`FC_EccK1ChaCha20_No1_NrC7`|

New algorithms MUST register mapping in FTSP and implementations.

### Data formats — JSON (`CryptoDataStr`)

Fields commonly present in **public** JSON (sensitive material MUST be transient / omitted):

|Field|Typical presence|Description|
|---|---|---|
|`type`|Y|`Symkey`, `AsyOneWay`, `AsyTwoWay`, `Password` (enum name).|
|`alg`|Y|Algorithm display name.|
|`cipher`|Y|Base64 ciphertext.|
|`iv`|Y|Hex IV.|
|`pubkeyA`|Asy|Hex ephemeral or sender pubkey (33-byte secp256k1 compressed, or 32-byte X25519 per algorithm).|
|`pubkeyB`|Asy (optional in output)|Recipient pubkey when needed for two-way.|
|`sum`|If required|Hex, 8 hex chars = 4 bytes, when algorithm profile uses `sum`.|
|`keyName`|Symkey bundles / some flows|Hex, 12 hex chars = 6 bytes.|

**MUST NOT** appear in serialized JSON: `symkey`, `password`, `prikeyA`, `prikeyB`, raw `data` (plaintext). These exist only in memory or secure channels.

### Data formats — binary bundle (`CryptoDataByte.toBundle` / `fromBundle`)

Layout:

```
algBytes[6]
+ typeByte[1]                    // EncryptType.getNumber()
+ [if AsyOneWay or AsyTwoWay: pubkeyA — 33 bytes for secp256k1 profiles, 32 bytes for X25519-GCM profile]
+ [if Symkey: keyName[6]]
+ iv[]                           // 12 bytes for GCM / ChaCha20 profiles; 16 bytes for AES-CBC in reference
+ cipher[]                       // variable
+ [if non-GCM profile: sum[4]]   // absent for FC_AesGcm256, FC_EccK1AesGcm256, FC_X25519AesGcm256 in reference
```

**IV length and compatibility**

- **12-byte IV:** AES-GCM and ChaCha20 profiles in the reference implementation.
- **16-byte IV:** AES-CBC profiles.
- If a **16-byte** IV is supplied where **12 bytes** are normative, implementations MAY **truncate to the first 12 bytes** for backward compatibility (`Encryptor.adjustIvLength`).

**`sum` (4-byte) integrity**

When present, reference implementations compute:

`sum = first 4 bytes of SHA-256( symkey || iv || did )`

where `did` is the double-SHA-256 of the **plaintext** (`CryptoDataByte.makeDid`, `makeSum4`). Verification MUST fail if `sum` does not match after decryption and `did` is known.

**GCM profiles:** `sum` is omitted; authentication uses the AEAD tag inside `cipher`.

**ChaCha20 profile:** `sum` is **required** in the reference stack (no built-in AEAD in the same way as GCM).

### Encryption workflow (normative overview)

1. Choose `EncryptType` and `AlgorithmId` (FTSP).
2. Generate random **IV** of the correct length for the algorithm.
3. For **Password**, derive **symkey** from password + IV; set `type = Password` for interchange but perform symmetric cipher with derived symkey.
4. For **AsyOneWay** / **AsyTwoWay**, derive **symkey** via ECDH/HKDF per FTSP, then encrypt plaintext with the symmetric algorithm.
5. Populate `cipher`, `iv`, `alg`, `type`, and optional `pubkeyA` / `pubkeyB`; compute **`did`** from plaintext when applicable; compute **`sum`** when the profile requires it.
6. Emit JSON for APIs; optionally emit **bundle** for compact binary transport.

### Decryption workflow (normative overview)

1. Parse JSON or bundle; obtain `type`, `alg`, `iv`, `cipher`.
2. Supply the appropriate secret: **symkey**, **password** (re-derive symkey with stored `iv`), or **prikey** (and peer **pubkey** for two-way) per `type`.
3. Decrypt to plaintext; recompute **`did`** and verify **`sum`** if present; for GCM, rely on cipher authentication.

### Encrypted files (reference pattern)

For large payloads, the reference implementation MAY write **one JSON object** (metadata: `type`, `alg`, `iv`, `pubkeyA`, `sum`, …) followed by **raw ciphertext bytes** in the same file. Readers MUST parse the leading JSON then decrypt the remainder as a stream. Exact on-disk layout is implementation-defined but SHOULD follow this pattern for interoperability.

### Rules (normative summary)

|#|Rule|
|---|---|
|1|JSON is the default interchange format for encrypted objects in APIs.|
|2|Secrets (symkey, password, private keys) MUST NOT be serialized to JSON in cleartext.|
|3|Symmetric keys used for content encryption SHOULD be **32 bytes** unless FTSP specifies otherwise.|
|4|`type` and `alg` together determine how to obtain the symkey and how to interpret `iv` and `sum`.|
|5|For profiles that use `sum`, verification MUST NOT accept plaintext if `sum` is wrong.|
|6|IV uniqueness: implementations SHOULD use a fresh random IV per encryption under the same key (per FTSP / best practice).|
|7|Algorithm and encoding details (ECDH, HKDF, cipher modes) belong in **FTSP**, not FVEP8.|
|8|RFC 2119 applies.|

### Relationship to FTSP

FVEP8 defines **types**, **field names**, **JSON/binary envelope**, and **integrity field semantics**. FTSP defines **EccK1AesGcm256**, **AesCbc256**, **X25519AesGcm256**, etc., including exact byte lengths and KDF steps.

## Examples

### Example 1 — Symkey JSON (shape only)

```json
{
  "type": "Symkey",
  "alg": "AesGcm256@No1_NrC7",
  "iv": "<24-hex = 12 bytes>",
  "cipher": "<Base64>",
  "keyName": "<12-hex = 6 bytes>"
}
```

### Example 2 — AsyOneWay JSON (shape only)

```json
{
  "type": "AsyOneWay",
  "alg": "EccK1AesGcm256@No1_NrC7",
  "iv": "<24-hex>",
  "pubkeyA": "<66-hex compressed secp256k1>",
  "cipher": "<Base64>"
}
```

### Example 3 — Bundle size hint

Minimum prefix before ciphertext: `6 + 1 + (0, 6, or 33/32) + (12 or 16)` bytes, plus variable `cipher` and optional 4-byte `sum`.

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-03-22|C_armX, No1_NrC7|Initial draft.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FVEP0|General FVEP rules.|
|FVEP2|DID definition; `did` field aligns with content hashing.|
|FVEP7|Signatures; often used together with encrypted channels.|
|FTSP|Per-algorithm encryption and key agreement.|

## Reference Implementation

|Component|Location|
|---|---|
|`Encryptor`| [FC-JDK/src/main/java/core/crypto/Encryptor.java](../../FC-JDK/src/main/java/core/crypto/Encryptor.java) |
|`Decryptor`| [FC-JDK/src/main/java/core/crypto/Decryptor.java](../../FC-JDK/src/main/java/core/crypto/Decryptor.java) |
|`CryptoDataByte`| [FC-JDK/src/main/java/core/crypto/CryptoDataByte.java](../../FC-JDK/src/main/java/core/crypto/CryptoDataByte.java) |
|`CryptoDataStr`| [FC-JDK/src/main/java/core/crypto/CryptoDataStr.java](../../FC-JDK/src/main/java/core/crypto/CryptoDataStr.java) |
|`EncryptType`| [FC-JDK/src/main/java/core/crypto/EncryptType.java](../../FC-JDK/src/main/java/core/crypto/EncryptType.java) |
|Android| `FC-AJDK/.../core/crypto/` counterparts |
