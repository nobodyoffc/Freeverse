# FTSP25V1_PasswordToSymkey

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
|Title|PasswordToSymkey|
|Type|FTSP|
|SN|25|
|Ver|1|
|Category|Hashing / KeyDerivation|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-04-14|
|PID||

Parent rules: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**PasswordToSymkey** defines the Freeverse key derivation procedure that converts a UTF-8 password and an IV into a **32-byte AES symmetric key** using two rounds of SHA-256. It is **not** an encryption algorithm itself — it is a key derivation step used by **`EncryptType.Password`** before dispatching to a symmetric cipher profile such as [FTSP12](FTSP12V1_AesGcm256.md) (AesGcm256), [FTSP14](FTSP14V1_AesCbc256.md) (AesCbc256), or [FTSP20](FTSP20V1_ChaCha20.md) (ChaCha20).

## Motivation

- **Uniform key derivation** across all symmetric cipher profiles when the user supplies a password rather than a raw symmetric key.
- **IV binding** — the same password with a different IV produces a different symkey, preventing precomputed-table attacks (rainbow tables) without requiring a separate salt field.
- **Simplicity and speed** — a single SHA-256 pass per stage keeps derivation fast for interactive wallet use, where the password is user-chosen and the threat model assumes device-local access.

## Specification

### Input

|Parameter|Description|
|---|---|
|`password`|UTF-8 string (arbitrary length)|
|`iv`|Byte array — typically **12** bytes (GCM / ChaCha20) or **16** bytes (CBC). This is the **same IV** that will be used for the subsequent encryption or decryption.|

### Procedure

```
function passwordToSymkey(password, iv):
    passwordBytes  = UTF8_ENCODE(password)
    passwordHash   = SHA256(passwordBytes)          // 32 bytes
    symkey         = SHA256(passwordHash || iv)      // 32 bytes  (|| = concatenation)
    return symkey
```

1. **Encode** the password to a byte array using UTF-8 (`BytesUtils.charArrayToByteArray(password, UTF_8)` in the reference).
2. **Hash** the password bytes: `passwordHash = SHA256(passwordBytes)`.
3. **Derive** the symmetric key: `symkey = SHA256(passwordHash || iv)`.
4. **Return** the 32-byte `symkey`.

### Output

|Parameter|Description|
|---|---|
|`symkey`|**32** bytes — ready to use as the AES-256 (or ChaCha20) key for the cipher profile selected by `AlgorithmId`.|

### Usage in Encryption / Decryption

**Encrypt** (`Encryptor.encryptByPassword`):

1. Generate a random IV (length determined by the `AlgorithmId`).
2. `symkey = passwordToSymkey(password, iv)`.
3. Encrypt plaintext with the chosen cipher profile using `symkey` and `iv`.
4. Set `EncryptType` to **Password** in the output `CryptoDataByte`.

**Decrypt** (`Decryptor.decryptByPassword`):

1. Read `iv` from the cipher data.
2. `symkey = passwordToSymkey(password, iv)`.
3. Set `EncryptType` to **Symkey** and delegate to `decryptBySymkey`, which dispatches by `AlgorithmId`.
4. Restore `EncryptType` to **Password** in the result.
5. On failure, a **200 ms delay** is applied to mitigate brute-force attempts.

### keyName

For `EncryptType.Password`, `keyName` is derived from the **derived symkey** (not the password directly):

```
keyName = first 6 bytes of SHA256(symkey)
```

where `symkey = SHA256(SHA256(passwordUTF8) || iv)`.

Because `symkey` changes with each IV, **`keyName` also changes per message**, unlike the Symkey profile where the same key always produces the same `keyName`.

### passwordHash Caching

The reference implementation also provides `encryptByPasswordHash(msg, passwordHash)` which accepts a pre-computed `passwordHash = SHA256(passwordUTF8)` to avoid re-hashing the password for multiple operations in a session. The password itself is never stored; only `passwordHash` MAY be cached in memory.

## Test Vectors

### TV-PW2SK-1 — 12-byte IV (GCM / ChaCha20)

|Field|Value|
|---|---|
|Password (UTF-8)|`MyPassword`|
|IV (12 bytes, hex)|`000102030405060708090a0b`|
|**passwordHash** (32 bytes, hex)|`dc1e7c03e162397b355b6f1c895dfdf3790d98c10b920c55e91272b8eecada2a`|
|**symkey** (32 bytes, hex)|`18f6a17f6fe849af1a43a7c006c2315006d5e644daf69f8b0555ad57defce54f`|
|**keyName** (6 bytes, hex)|`bbeddbe89c74`|

**Verification:** `SHA256("MyPassword".getBytes(UTF_8))` = `dc1e7c03...`; `SHA256(passwordHash || iv)` = `18f6a17f...`.

### TV-PW2SK-2 — 16-byte IV (CBC)

|Field|Value|
|---|---|
|Password (UTF-8)|`MyPassword`|
|IV (16 bytes, hex)|`000102030405060708090a0b0c0d0e0f`|
|**passwordHash** (32 bytes, hex)|`dc1e7c03e162397b355b6f1c895dfdf3790d98c10b920c55e91272b8eecada2a`|
|**symkey** (32 bytes, hex)|`442c5118b7f5ae123a6c07ff37e5793fb511496a0677a0a7ad6806e030906630`|
|**keyName** (6 bytes, hex)|`f25e7f92ceef`|

**Note:** The `passwordHash` is the same in both vectors (it depends only on the password). The `symkey` and `keyName` differ because the IV differs.

**Reference run:** Verified against FC-JDK `Encryptor.passwordToSymkey` and Python `hashlib.sha256`, 2026-04-14.

## Developer JSON example

**Password** profile with AES-256-GCM: Password = `MyPassword`, IV = 12-byte `000102030405060708090a0b`; plaintext UTF-8 **`Hello world!`**. Keys: [FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples).

The derived `symkey` is `18f6a17f6fe849af1a43a7c006c2315006d5e644daf69f8b0555ad57defce54f` (TV-PW2SK-1). After derivation, the `EncryptType` is set to `Password` and the cipher profile (here AesGcm256) encrypts the plaintext:

```json
{
  "type": "Password",
  "alg": "AesGcm256@No1_NrC7",
  "keyName": "bbeddbe89c74",
  "iv": "000102030405060708090a0b"
}
```

The `cipher` field (Base64) depends on the AES-GCM encryption output with the derived symkey and is omitted here — see the FTSP12 and FTSP14 Password examples for complete cipher JSON.

**Password** profile with AES-256-CBC: see [FTSP14 Developer JSON example](FTSP14V1_AesCbc256.md#developer-json-example) — `keyName` = `f25e7f92ceef` matches TV-PW2SK-2.

## Security Considerations

- **Single-pass SHA-256 — no iteration count or memory-hardness.** Unlike PBKDF2, bcrypt, or Argon2, this KDF uses only two SHA-256 invocations. It is fast to compute and therefore susceptible to brute-force if password entropy is low. GPU/ASIC-accelerated SHA-256 can test billions of candidate passwords per second.
- **IV as salt:** Same password + different IV = different symkey. This prevents rainbow-table and precomputation attacks. However, the IV is transmitted in cleartext alongside the ciphertext, so an attacker who knows the IV can still attempt brute-force against the password.
- **Password strength is the primary security factor.** High-entropy passwords (long, random, mixed-character) are essential. Short or dictionary-based passwords offer minimal protection regardless of the cipher profile used.
- **Not suitable for scenarios requiring resistance to GPU/ASIC brute-force.** This KDF is adequate for wallet-level protection where the password is user-chosen and the threat model assumes device-local access. For server-side password storage or high-value secrets exposed to offline attack, use a memory-hard KDF (Argon2, scrypt) instead.
- **No separate salt field.** The IV serves double duty as both encryption nonce and KDF salt. If the same password and IV are reused (which violates GCM IV-uniqueness rules anyway), the derived symkey will be identical, compounding the security failure.
- **Brute-force delay:** The reference `Decryptor.decryptByPassword` adds a 200 ms sleep on decryption failure to slow online brute-force attempts; this does not protect against offline attacks on captured ciphertext.

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-04-14|C_armX, No1_NrC7|Initial specification: SHA256-based password-to-symkey KDF, test vectors TV-PW2SK-1–2.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FTSP0|FTSP governance; shared example keys (§2.1) used in test vectors.|
|FTSP12|AesGcm256 — symmetric cipher dispatched after PasswordToSymkey derivation (GCM profile).|
|FTSP14|AesCbc256 — symmetric cipher dispatched after PasswordToSymkey derivation (CBC profile).|
|FTSP20|ChaCha20 — symmetric cipher dispatched after PasswordToSymkey derivation (ChaCha20 profile).|
|FVEP8|Encryption envelope — defines `EncryptType.Password`, `keyName`, and bundle layout.|

## Reference Implementation

|Component|Location|
|---|---|
|`Encryptor.passwordToSymkey`| [FC-JDK/src/main/java/core/crypto/Encryptor.java](../../FC-JDK/src/main/java/core/crypto/Encryptor.java) |
|`Encryptor.encryptByPassword`| [FC-JDK/src/main/java/core/crypto/Encryptor.java](../../FC-JDK/src/main/java/core/crypto/Encryptor.java) |
|`Decryptor.decryptByPassword`| [FC-JDK/src/main/java/core/crypto/Decryptor.java](../../FC-JDK/src/main/java/core/crypto/Decryptor.java) |
|`CryptoDataByte.makeKeyName`| [FC-JDK/src/main/java/core/crypto/CryptoDataByte.java](../../FC-JDK/src/main/java/core/crypto/CryptoDataByte.java) |
