# FTSP13V1_HKDF

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
|Title|HKDF|
|Type|FTSP|
|SN|13|
|Ver|1|
|Category|Hashing|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

Parent rules: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

This document specifies the **HKDF** used in FC-JDK: **RFC 5869** structure (**Extract** then **Expand**) implemented with **HMAC-SHA512**. The pseudorandom key **PRK** is **64** bytes; **OKM** length **L** MUST satisfy **1 ≤ L ≤ 16320** (**255 × 64**). **`salt`** may be null or empty (then a **64-byte** zero salt is used). **`info`** may be null (treated as empty). This is the primitive behind profiles such as [FTSP11 Ecc256K1AesGcm256](FTSP11V1_Ecc256K1AesGcm256.md) (`Ecc256K1Hkdf` / `Ecc256K1AesGcm256`).

## Motivation

- **Interoperability**: Independent code must match FC-JDK byte-for-byte when deriving keys from **IKM** + **salt** + **info**.
- **Explicit hash**: The reference does **not** use HKDF-SHA256; it uses **HMAC-SHA512** end-to-end.

## Interoperability

Implementations claiming compatibility with **FTSP13** MUST:

1. Use **HMAC-SHA512** for both **Extract** and **Expand** (Java `Mac.getInstance("HmacSHA512")` or equivalent).
2. Use **HashLen = 64** for all length calculations and for the **zero salt** substitute.
3. Match the **Expand** input layout **`previousT || info || (byte)i`** with **`i = 1…N`** and **`previousT`** = **full prior T** (64 bytes), starting with **`previousT` empty** before **`i = 1`**.
4. Treat **`info == null`** as **no bytes** appended (not UTF-8 `"null"`).

Higher-level profiles ([FTSP11](FTSP11V1_Ecc256K1AesGcm256.md), [FTSP12](FTSP12V1_AesGcm256.md)) depend on this KDF; errors here break encryption interoperability.

## Specification

### Primitive

|Item|Value|
|---|---|
|HMAC algorithm|`HmacSHA512` (Java `Mac.getInstance`)|
|Hash output length **HashLen**|**64** bytes|
|PRK length|**64** bytes (length of HMAC-SHA512 output)|

### HKDF-Extract

**Inputs:** `salt` (optional), `ikm` (input keying material; MUST NOT be null in callers—if null, behavior is undefined at JVM level).

**Procedure (reference `extract`):**

1. If `salt` is **null** or **length 0**, set `effectiveSalt` to a **64-byte** array of **0x00** (RFC 5869 salt = `HashLen` zeros).
2. `PRK = HMAC-SHA512(key = effectiveSalt, data = ikm)`.

**Output:** `PRK`, **64** bytes.

### HKDF-Expand

**Inputs:** `prk` (**64** bytes), `info` (optional byte string, may be **null**), `L` (desired OKM length in bytes).

**Constraints:** `L` MUST satisfy **1 ≤ L ≤ 255 × HashLen** (**16320**). Otherwise `IllegalArgumentException`.

**Procedure (reference `expand`):**

1. `N = ceil(L / HashLen)`.
2. Initialize `previousT` as empty byte array.
3. For `i = 1` to `N`:
   - `T(i) = HMAC-SHA512(key = prk, data = previousT || info || byte(i))`  
     where **`byte(i)`** is the single byte with value **`i`** (0x01, 0x02, …), and **`||`** is concatenation. If `info` is **null**, do not append any info bytes (equivalent to empty).
   - Copy the first **`min(HashLen, L - copied)`** bytes of `T(i)` into the output buffer.
   - Set `previousT = T(i)` (full **64** bytes) for the next iteration.
4. Return the first **`L`** bytes as **OKM**.

**Note:** The reference uses the **full** 64-byte `T(i)` as `previousT` for the next HMAC input, not a truncated block.

### HKDF (one-shot)

**API:** `hkdf(ikm, salt, info, length)` → `OKM`

**Procedure (reference `hkdf`):**

1. `PRK = Extract(salt, ikm)`.
2. `OKM = Expand(PRK, info, length)`.

### Reference pseudocode (normative)

The following reproduces `HKDF.java` behavior (language-neutral):

```
function hmac_sha512(key, data):
    return HMAC-SHA512(key, data)

function extract(salt, ikm):
    if salt is null or length(salt) == 0:
        salt = 64 bytes of 0x00
    return hmac_sha512(salt, ikm)   // 64-byte PRK

function expand(prk, info, L):
    if L < 1 or L > 255 * 64: error
    N = ceil(L / 64)
    OKM = empty
    previousT = empty byte sequence
    for i from 1 to N inclusive:
        buf = previousT
        if info is not null:
            buf = buf || info
        buf = buf || (single byte with value i)
        T = hmac_sha512(prk, buf)     // always 64 bytes
        append first min(64, L - length(OKM)) bytes of T to OKM
        previousT = T                 // full 64 bytes for next i
    return OKM

function hkdf(ikm, salt, info, L):
    return expand(extract(salt, ikm), info, L)
```

### Relationship to RFC 5869

- The **shape** (Extract / Expand, counter byte, chaining `T`) matches **RFC 5869**.
- The **hash** is **SHA-512**, not SHA-256, so **HashLen = 64** and numeric limits differ from a SHA-256 HKDF stack.

## Test Vectors

All values are **hex** (lowercase). Implementations MUST reproduce **`OKM`** (and **`PRK`** where listed) exactly.

### TV1 — `hkdf`, zero IKM, 12-byte zero salt, `info = ASCII "hkdf"`, L = 32

|Field|Value|
|---|---|
|`ikm`|64 hex chars = 32 × `00`|
|`salt`|24 hex chars = 12 × `00`|
|`info`|ASCII bytes `68 6b 64 66` (`"hkdf"`)|
|`L`|32|
|**`OKM` (32 bytes)**|`79d55d067d55fd67266b49e13949f6ea3fec4e752bbaabe0c52ddc7ac7c02a64`|

### TV2 — `extract(null, ikm)` with zero 32-byte `ikm`

|**`PRK` (64 bytes)**|`bae46cebebbb90409abc5acf7ac21fdb339c01ce15192c52fb9e8aa11a8de9a4ea15a045f2be245fbb98916a9ae81b353e33b9c42a55380c5158241daeb3c6dd`|

### TV3 — `hkdf`, `ikm[i] = i` for `i = 1…32`, 12-byte zero salt, `info = "hkdf"`, L = 32

|`ikm`|`0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20`|
|**`OKM` (32 bytes)**|`a90aad642250bb8562417ac75dc4ca02d7b1f0d9533d14ab5a5a122939a69421`|

**Verification:** Run `HKDF.hkdf` / `HKDF.extract` in FC-JDK with the same inputs; hex MUST match (verified 2026-03-24 against `FC-JDK` + BouncyCastle classpath).

### Regression

FC-JDK **`FtspProtocolVectorTest.ftsp13_hkdf_tv1_and_tv3`** pins **TV1** and **TV3** ([FtspProtocolVectorTest.java](../../FC-JDK/src/test/java/core/crypto/FtspProtocolVectorTest.java)).

## Developer JSON example

HKDF is not serialized as `CryptoDataByte` JSON; this object records inputs/outputs for the same **symkey** as [FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples). **`info`** is UTF-8 **`FTSP13-dev-example`** (arbitrary label for this sample).

```json
{
  "ikm": "dc1e7c03e162397b355b6f1c895dfdf3790d98c10b920c55e91272b8eecada2a",
  "salt": "000102030405060708090a0b0c0d0e0f",
  "info": "FTSP13-dev-example",
  "L": 32,
  "okm": "708ef1deb1b177657cc1b3817ad0ba2f2d940a3c61d708b57bdcbb09f094032b"
}
```

## Security Considerations

- **IKM quality:** HKDF does not create entropy; **IKM** SHOULD be a high-entropy secret (e.g. ECDH output).
- **Salt:** Using a random **salt** (e.g. 12-byte IV in [FTSP11](FTSP11V1_Ecc256K1AesGcm256.md)) supports binding derived keys to a context; null/empty salt is allowed per RFC but weaker for some threat models.
- **Info:** Use distinct **`info`** strings to domain-separate keys for different purposes.
- **L:** Request only the length needed; do not oversize **OKM**.

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-03-24|C_armX, No1_NrC7|FC-JDK HKDF (HMAC-SHA512); normative pseudocode, TV1–TV3, interop checklist.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FTSP0|FTSP governance.|
|FTSP11|Uses **`HKDF.hkdf`** with fixed **`info`** for secp256k1 ECDH → AES-GCM key.|
|RFC 5869|Conceptual HKDF definition (hash function differs in this profile).|

## Reference Implementation

|Component|Location|
|---|---|
|`HKDF`| [FC-JDK/src/main/java/core/crypto/Algorithm/HKDF.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/HKDF.java) |
|`Ecc256K1Hkdf.sharedSecretToSymkey`| [FC-JDK/src/main/java/core/crypto/Algorithm/Ecc256K1Hkdf.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/Ecc256K1Hkdf.java) |
