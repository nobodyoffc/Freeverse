# FTSP11V1_Ecc256K1AesGcm256

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
|Title|Ecc256K1AesGcm256|
|Type|FTSP|
|SN|11|
|Ver|1|
|Category|Encryption / KeyExchange|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

Parent rules: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**Ecc256K1AesGcm256** is the Freeverse reference profile **secp256k1 ECDH → HKDF → AES-256-GCM**. A **32-byte** shared secret is derived with **ECDH** (compressed **33-byte** public keys), expanded to a **32-byte** content key with **HKDF** using the **12-byte** nonce as **salt** and fixed **info** `hkdf`, then **AES/GCM/NoPadding** encrypts the plaintext with a **128-bit** GCM authentication tag appended to the ciphertext. The **`AlgorithmId`** is **`EccK1AesGcm256@No1_NrC7`** (bundle prefix last byte **`0x04`**). Envelope fields (`EncryptType`, JSON layout, absence of **`sum`**) follow [FVEP8V1_Encryption](../FVEP/FVEP8V1_Encryption.md).

## Motivation

- **Authenticated encryption** for **AsyOneWay** and **AsyTwoWay** modes without a separate **`sum`** field.
- **One normative byte-level recipe** so FC-JDK, APIs, and peers agree on ECDH encoding, KDF, IV length, and cipher output.

## Interoperability

A compliant implementation MUST chain **three** normative pieces in order:

1. **ECDH shared secret `Z` (32 bytes)** — [Normative ECDH encoding](#normative-ecdh-shared-secret-encoding-secp256k1) below (same as `Ecc256K1Hkdf.getSharedSecret`).
2. **HKDF** — [FTSP13](FTSP13V1_HKDF.md): `symkey = hkdf(ikm = Z, salt = nonce12, info = ASCII "hkdf", L = 32)`.
3. **AES-256-GCM** — [FTSP12](FTSP12V1_AesGcm256.md): encrypt/decrypt with **`symkey`**, **`iv = nonce12`**, empty AAD, 128-bit tag.

Implementations MUST **not** substitute HKDF-SHA256 or a different IV length without defining a new `AlgorithmId`.

## Specification

### Algorithm ID

|Form|Value|
|---|---|
|Display name (JSON `alg`)|`EccK1AesGcm256@No1_NrC7`|
|Enum (reference)|`AlgorithmId.FC_EccK1AesGcm256_No1_NrC7`|
|Bundle `algBytes` (6 bytes)|`00 00 00 00 00 04`|

### Cryptographic primitives

|Step|Primitive|Parameters (reference)|
|---|---|---|
|Curve|**secp256k1** (Bitcoin-style)|
|Private key|**32** bytes|
|Public key|**33** bytes, **compressed** encoding (as produced/consumed by `KeyTools` in FC-JDK)|
|ECDH|**ECDHBasicAgreement** (BouncyCastle): shared field element **Z** serialized to **32** bytes (big-endian, left-padded with zeros if needed; see `Ecc256K1Hkdf.getSharedSecret`)|
|KDF|**HKDF** (`HKDF.hkdf`): **Extract** = HMAC-SHA**512** with **salt** = `nonce` (12 bytes; if salt were empty, RFC 5869 zero salt applies — here salt is always the 12-byte IV), **IKM** = shared secret; **Expand** with **info** = UTF-8 bytes of the ASCII string **`hkdf`**, output length **32** bytes|
|AEAD|**AES-256-GCM**, **12-byte** IV = same **`nonce`**, **128-bit** tag, provider/transform **`AES/GCM/NoPadding`** (BouncyCastle **BC** in reference)|

**Note:** The reference HKDF implementation uses **HMAC-SHA512** and a **64-byte** PRK width (`HKDF.java`), not HMAC-SHA256. Interoperating implementations MUST match this exactly — see [FTSP13](FTSP13V1_HKDF.md).

### Normative ECDH shared secret encoding (secp256k1)

This matches **`Ecc256K1Hkdf.getSharedSecret(priKeyBytes, pubKeyBytes)`** (BouncyCastle **`ECDHBasicAgreement`** on **secp256k1**).

**Inputs**

- **`priKeyBytes`:** **32** bytes, big-endian unsigned integer **d** (private scalar), in range **[1, n−1]** for subgroup order **n** (invalid keys are rejected by the reference `KeyTools` path).
- **`pubKeyBytes`:** **33** bytes, **SEC1 compressed** public key: **`0x02` or `0x03`** prefix + **32-byte** **X** coordinate.

**Steps**

1. Decode **`priKeyBytes`** and **`pubKeyBytes`** into EC domain parameters for **secp256k1** (reference: `KeyTools.prikeyFromBytes` / `pubkeyFromBytes`).
2. Compute **`agreement = ECDHBasicAgreement.calculateAgreement(peerPublic)`** → **`java.math.BigInteger Z`** (shared secret integer; in practice the **x**-coordinate field element of **`d · Q`** after normalization).
3. **`zBytes = Z.toByteArray()`** (signed big-endian; may be **33** bytes with leading **`0x00`** if the top bit would otherwise imply a negative number).
4. **Right-align** into a fixed **32-byte** array **`secret`**:
   - `srcPos = 1` if **`zBytes.length == 33`** and **`zBytes[0] == 0`**, else **`srcPos = 0`**.
   - `length = min(zBytes.length - srcPos, 32)`.
   - `destPos = 32 - length`.
   - **`System.arraycopy(zBytes, srcPos, secret, destPos, length)`**; **`secret`** was zero-initialized so unused leading bytes remain **0x00**.

**Output:** **`secret`**, always **32** bytes.

**Interoperability:** Any library that produces a **different** 32-byte encoding for the same **`(d, Q)`** will derive a **different** HKDF key and **will not** decrypt FC-JDK ciphertext.

### Nonce (IV)

- **Length:** **12** bytes (96-bit), per NIST recommendation for GCM.
- **Role (this profile):** The same value is used as **HKDF salt** and as **AES-GCM IV**.
- **Uniqueness:** Implementations MUST use a **fresh** random 12-byte nonce for each encryption under a given static key pair usage; **nonce reuse** with the same symkey breaks GCM confidentiality and integrity.

### Key agreement → symmetric key

For own private key **`priKey`** (32 bytes) and peer public key **`pubKey`** (33 bytes compressed), and nonce **`N`** (12 bytes):

1. `Z = ECDH(priKey, pubKey)` → 32 bytes ([normative encoding](#normative-ecdh-shared-secret-encoding-secp256k1)).
2. `symkey = HKDF.hkdf(Z, N, info = ASCII "hkdf", L = 32)` — [FTSP13](FTSP13V1_HKDF.md).
3. Zeroize **`Z`** in memory after derivation (reference clears the array).

Encryption and decryption both derive **`symkey`** the same way from **`(priKey, pubKey, N)`**; only **which** key is “own” vs “peer” swaps between encrypt and decrypt roles per [FVEP8](../FVEP/FVEP8V1_Encryption.md) **AsyOneWay** / **AsyTwoWay** rules.

### Composition summary (normative)

```
symkey = FTSP13.hkdf( FTSP11.ecdh(priKey, pubKey), salt = nonce12, info = "hkdf", L = 32 )
cipher = FTSP12.aes_gcm256_encrypt(symkey, iv = nonce12, plaintext)
plaintext = FTSP12.aes_gcm256_decrypt(symkey, iv = nonce12, cipher)
```

### Encryption procedure

**Inputs:** `plaintext`, `priKey` (sender/ephemeral private), `pubKey` (recipient public), `nonce` (12 random bytes).

1. `symkey = asyKeyToSymkey(priKey, pubKey, nonce)` (same as **HKDF** step above).
2. `ciphertext = AES-GCM-Encrypt(key = symkey, iv = nonce, plaintext)` per [FTSP12](FTSP12V1_AesGcm256.md); output is **body ∥ tag**.

**Outputs (logical):** `cipher` bytes, `iv` = `nonce` (hex in JSON), `alg` = `EccK1AesGcm256@No1_NrC7`, and for asymmetric types **`pubkeyA`** (and optionally **`pubkeyB`**) per FVEP8. **`sum`** MUST NOT be used for this profile.

### Decryption procedure

**Inputs:** `cipher`, `priKey` (recipient private), `pubKey` (sender/ephemeral public), `nonce` (12 bytes, same as encrypt).

1. `symkey = asyKeyToSymkey(priKey, pubKey, nonce)`.
2. `plaintext = AES-GCM-Decrypt(key = symkey, iv = nonce, cipher)`; verification fails if the tag is invalid.

Reference (`AesGcm256.decrypt`) may set an internal symmetric algorithm id during decrypt, then restore **`FC_EccK1AesGcm256_No1_NrC7`** on the outer `CryptoDataByte`.

### Streaming

`Ecc256K1AesGcm256.encryptStream` / `decryptStream` apply the same **KDF** and **AES-GCM** rules over streams (`AesGcm256` stream helpers). GCM still consumes the full ciphertext for tag verification on decrypt.

### FVEP8 alignment

|FVEP8 rule|This profile|
|---|---|
|IV length|**12** bytes|
|`sum`|Omitted (AEAD tag)|
|Bundle `algBytes`|**… 04**|
|`EncryptType`|**AsyOneWay** / **AsyTwoWay** (and interchange metadata)|

Full JSON and binary bundle layout: [FVEP8V1_Encryption](../FVEP/FVEP8V1_Encryption.md).

## Test Vectors

### TV-ECDH-1 — shared secret for **d = 1**, generator point

|Field|Hex (lowercase)|
|---|---|
|`priKey` (32 bytes)|`0000000000000000000000000000000000000000000000000000000000000001`|
|`pubKey` (33 bytes, compressed G)|`0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798`|
|**`Z` (32 bytes)**|`79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798`|

### TV-FTSP11-1 — full pipeline: same **`Z`**, HKDF + AES-GCM

Use **`Z`** from **TV-ECDH-1**. Nonce **`N`** (salt + IV):

|`N` (12 bytes)|`101112131415161718191a1b`|

HKDF: **`ikm = Z`**, **`salt = N`**, **`info = ASCII "hkdf"`**, **`L = 32`** ([FTSP13](FTSP13V1_HKDF.md) TV rules).

|**`symkey` (32 bytes)**|`2a2768b8c286dbed4a5c7299d49b9a8aaaedbd7c250862fa8dc6f1b4b56ceb8c`|

AES-GCM: **`key = symkey`**, **`iv = N`**, plaintext **UTF-8 `a`** (single byte **`61`**), **empty AAD**, [FTSP12](FTSP12V1_AesGcm256.md).

|**`cipher` (17 bytes = 1 + 16 tag)**|`fad929f0256cda6e921589b0b2c542a3fb`|

**Decrypt check:** From **`symkey`**, **`N`**, and **`cipher`**, plaintext MUST be **`61`**. **`did`** for that plaintext is in [FTSP12 TV-AESGCM-2](FTSP12V1_AesGcm256.md#tv-aesgcm-2--did-for-plaintext-a).

**Verification:** Values reproduced with FC-JDK **`Ecc256K1Hkdf`**, **`HKDF.hkdf`**, BouncyCastle **`AES/GCM/NoPadding`**, 2026-03-24.

### Regression

Implementations SHOULD also pass FC-JDK tests: `NewAlgorithmTest.testEccK1AesGcm256AsymmetricEncryption`, `BundleRoundTripTest`, `AsyGcmDebugTest`, and **`FtspProtocolVectorTest.ftsp11_ecdh_z_and_symkey_and_aesgcm_byte_a`** ([FtspProtocolVectorTest.java](../../FC-JDK/src/test/java/core/crypto/FtspProtocolVectorTest.java)) — it checks **TV-FTSP11-1** using **`Z`** from **TV-ECDH-1** (some stacks reject **d = 1** via higher-level key APIs; the test still pins **HKDF + AES-GCM** outputs).

## Developer JSON example

**AsyOneWay** to **fidA** (recipient static pubkey): ephemeral sender secret = **fidB** private key; IV = 12-byte `000102030405060708090a0b`; plaintext UTF-8 **`Hello world!`**. Keys: [FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples).

```json
{
  "type": "AsyOneWay",
  "alg": "EccK1AesGcm256@No1_NrC7",
  "cipher": "15g2ijHqF+CWJfWXOYLlmn+AjHnT7mkVMVcWTg==",
  "pubkeyA": "02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67",
  "iv": "000102030405060708090a0b"
}
```

## Security Considerations

- **Nonce reuse** with the same derived key is catastrophic for GCM; use CSPRNG for **`nonce`**.
- **Private keys** MUST be protected; ephemeral keys SHOULD be discarded after use (**AsyOneWay**).
- **Curve validation**: use the reference’s public-key parsing (`KeyTools.pubkeyFromBytes`) to avoid invalid-point issues.
- **Tag length**: 128-bit GCM tag is non-configurable in the reference profile.
- HKDF **salt** tied to **IV** means the same **nonce** must be stored/transmitted with the ciphertext; do not substitute a different salt for HKDF while reusing another IV for GCM.

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-03-24|C_armX, No1_NrC7|`Ecc256K1AesGcm256` / FVEP8; ECDH encoding; FTSP11–13 composition; TV-ECDH-1, TV-FTSP11-1.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FTSP0|FTSP governance and categories.|
|FTSP12|AES-256-GCM layer (tag, AAD, test vectors).|
|FTSP15|Legacy ECDH + SHA512 + CBC — different ECDH encoding; not interchangeable.|
|FTSP13|HKDF layer (HMAC-SHA512, test vectors).|
|FVEP8|Encrypt types, JSON/bundle envelope, `sum` / `iv` rules.|
|FVEP2|`did` from plaintext (double SHA-256) where applicable.|

## Reference Implementation

|Component|Location|
|---|---|
|`Ecc256K1AesGcm256`| [FC-JDK/src/main/java/core/crypto/Algorithm/Ecc256K1AesGcm256.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/Ecc256K1AesGcm256.java) |
|`Ecc256K1Hkdf` (ECDH + HKDF extract/expand inputs)| [FC-JDK/src/main/java/core/crypto/Algorithm/Ecc256K1Hkdf.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/Ecc256K1Hkdf.java) |
|`HKDF`| [FC-JDK/src/main/java/core/crypto/Algorithm/HKDF.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/HKDF.java) |
|`AesGcm256`| [FC-JDK/src/main/java/core/crypto/Algorithm/AesGcm256.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/AesGcm256.java) |
|`AlgorithmId`| [FC-JDK/src/main/java/data/fcData/AlgorithmId.java](../../FC-JDK/src/main/java/data/fcData/AlgorithmId.java) |
|`Encryptor` / `Decryptor`| [FC-JDK/src/main/java/core/crypto/Encryptor.java](../../FC-JDK/src/main/java/core/crypto/Encryptor.java), [Decryptor.java](../../FC-JDK/src/main/java/core/crypto/Decryptor.java) |
