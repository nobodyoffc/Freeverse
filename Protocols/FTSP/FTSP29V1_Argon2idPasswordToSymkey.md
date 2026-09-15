# FTSP29V1_Argon2idPasswordToSymkey

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
|Title|Argon2idPasswordToSymkey|
|Type|FTSP|
|SN|29|
|Ver|1|
|Category|Hashing / KeyDerivation|
|Status|Draft|
|Author|No1_NrC7|
|Created|2026-09-15|
|PID||

Parent rules: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**Argon2idPasswordToSymkey** is the default key derivation for **`EncryptType.Password`**. It turns a UTF-8 password and the cipher's IV into a **32-byte** symmetric key with **Argon2id** (t = 3, m = 64 MiB, p = 1). The symmetric profile named in `alg` then uses that key; normally that is [FTSP12](FTSP12V1_AesGcm256.md) AesGcm256. Its KDF id is **`Argon2id@No1_NrC7`** and its bundle KDF byte is **`0x02`**. It replaces [FTSP25](FTSP25V1_PasswordToSymkey.md) for every new cipher.

## Motivation

- **Memory-hard password stretching.** FTSP25 costs two SHA-256 calls per guess, so a GPU can test billions of passwords per second against a captured cipher. Argon2id needs 64 MiB and three passes per guess, which removes most of the GPU/ASIC advantage.
- **Same envelope as FTSP25.** The salt is the IV the cipher already carries, so a Password cipher gains no new field. Only `kdf` in JSON, or the KDF byte in a bundle ([FTSP30](FTSP30V1_CryptoBundle.md)), tells a reader which derivation to run.
- **One parameter set everywhere.** [FTSP28](FTSP28V1_PhraseToPriKey.md) uses the same parameters, so one Argon2id implementation serves both.

## Specification

### Algorithm ID

|Item|Value|
|---|---|
|KDF id (JSON `kdf`)|`Argon2id@No1_NrC7`|
|Bundle KDF byte|`0x02`|
|Reference enum|`Kdf.Argon2id_No1_NrC7`|

### Parameters

All parameters are fixed. Changing any of them changes every derived key, so a different parameter set MUST get a new KDF id and byte ([FTSP0](FTSP0V1_FTSP.md) §6).

|Parameter|Value|
|---|---|
|Argon2 type|Argon2id|
|Version|0x13 (v1.3)|
|Iterations `t`|3|
|Memory `m`|65536 KiB (64 MiB)|
|Parallelism `p`|1|
|Salt|The cipher IV: 12 bytes for GCM and ChaCha20 profiles, 16 bytes for CBC|
|Secret key / associated data|None|
|Output length|32 bytes|
|Password encoding|UTF-8, with no normalization or trimming|

### Procedure

```
function argon2idPasswordToSymkey(password, iv):
    return Argon2id(password = UTF8_ENCODE(password), salt = iv,
                    version = 0x13, t = 3, m = 65536, p = 1, outLen = 32)
```

**Encrypt** (`Encryptor.encryptByPassword`, whose default KDF is this one):

1. Generate a random IV of the length the `alg` profile requires.
2. `symkey = argon2idPasswordToSymkey(password, iv)`.
3. Encrypt the plaintext with the `alg` profile, using `symkey` and `iv`.
4. Set `type` to **Password** and `kdf` to **`Argon2id@No1_NrC7`**. JSON writers MUST include `kdf`. Bundle writers use type byte 4 followed by KDF byte `0x02` ([FTSP30](FTSP30V1_CryptoBundle.md)).

**Decrypt** (`Decryptor.decryptByPassword`):

1. If the cipher names a KDF (JSON `kdf`, or a type-4 bundle), derive with that KDF only.
2. If it names none (JSON without `kdf`, or a type-3 bundle), derive with this KDF first. If decryption fails, derive with [FTSP25](FTSP25V1_PasswordToSymkey.md) and try again. Keep the first that succeeds, and report which KDF it was.
3. On failure, apply a delay (the reference waits 200 ms) and report no KDF.

### Data Format

|Field|Description|
|---|---|
|Input `password`|UTF-8 string. [FVEP8](../FVEP/FVEP8V1_Encryption.md) recommends at most 64 bytes.|
|Input `iv`|The cipher IV, used as the salt.|
|Output `symkey`|32 bytes.|

In JSON ciphers, `keyName` is `SHA256(symkey)[0:6]` of the derived key, as in FTSP25, so it changes with every IV. Password bundles carry no `keyName`.

### Error Handling

- A `kdf` name that isn't recognized, or a bundle KDF byte that isn't registered, MUST be rejected. Implementations MUST NOT guess a KDF for a cipher that names an unknown one.
- If Argon2id cannot allocate its memory, that is an error. Implementations MUST NOT fall back to FTSP25 for a cipher that names Argon2id.

## Test Vectors

Password `MyPassword` (UTF-8 `4d7950617373776f7264`), with the parameters above.

### TV-A2PW-1 — 12-byte IV (GCM)

|Field|Value|
|---|---|
|Salt = IV|`000102030405060708090a0b`|
|**symkey**|`9ee69df427169f8eab1e285ec544cfa96587094ab33898b58b8c3cf458808f54`|

### TV-A2PW-2 — 16-byte IV (CBC)

|Field|Value|
|---|---|
|Salt = IV|`000102030405060708090a0b0c0d0e0f`|
|**symkey**|`2e0f4d2d8834d9ded0cc51a1766dad94ff7678274c14a9f5b2eae7af3b3c9d96`|

The same values are `KDF-ARGON2ID-SALT12` and `KDF-ARGON2ID-SALT16` in [vectors/kdf.json](vectors/kdf.json). Password ciphers built on them are in [vectors/cipher-json.json](vectors/cipher-json.json) and [vectors/bundle.json](vectors/bundle.json), including JSON that omits `kdf` and both bundle layouts.

## Developer JSON example

AesGcm256 with password `MyPassword`, IV `000102030405060708090a0b` and plaintext `Hello world!` (derived symkey: TV-A2PW-1):

```json
{
  "type": "Password",
  "alg": "AesGcm256@No1_NrC7",
  "kdf": "Argon2id@No1_NrC7",
  "cipher": "YcBKnPD9dP6i04r45LDWTkrL1Zek4sQ9LIZF7g==",
  "keyName": "f700db5983ea",
  "iv": "000102030405060708090a0b"
}
```

The same cipher as a type-4 bundle ([FTSP30](FTSP30V1_CryptoBundle.md)), field by field: `76f7b226a8b3` `04` `02` `000102030405060708090a0b` `61c04a9cf0fd74fea2d38af8e4b0d64e4acbd597a4e2c43d2c8645ee`.

## Security Considerations

- **Password entropy still decides.** Argon2id raises the cost of each guess, but it cannot make a weak password strong. Wallets SHOULD encourage long passwords.
- **Salt length.** The 12-byte GCM IV is a 96-bit salt: shorter than the 16 bytes RFC 9106 recommends, but random per cipher and above Argon2's 8-byte minimum. A salt's job here is to be unique, and 96 random bits are enough for that.
- **IV reuse.** The same password and IV give the same key, and GCM breaks under a repeated key and IV. IVs MUST be fresh random bytes for every encryption, as every profile already requires.
- **Cost per decryption.** Every decryption pays the full Argon2id cost: 64 MiB and tens to hundreds of milliseconds. A wallet that keeps many records under one password SHOULD derive once and wrap a random data key, rather than password-encrypting each record.
- **Trial decryption when no KDF is named.** For AEAD profiles, the 128-bit tag decides whether a trial succeeded. For non-AEAD profiles (CBC, ChaCha20) the 4-byte `sum` decides, so a wrong-KDF trial is falsely accepted with probability about 2⁻³². That is one more reason new ciphers MUST name their KDF and use AEAD profiles. Stripping the KDF marker only forces the trial; it does not weaken the key.
- **The delay on failure** slows online guessing only. It does nothing against an offline attacker who holds the cipher.

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-09-15|No1_NrC7|Argon2id password KDF salted with the IV; KDF id and bundle byte; decrypt rule for ciphers that name no KDF; TV-A2PW-1–2.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|[FTSP0](FTSP0V1_FTSP.md)|Governance; shared example keys used by the vectors.|
|[FTSP12](FTSP12V1_AesGcm256.md)|AesGcm256, the profile new Password ciphers use.|
|[FTSP25](FTSP25V1_PasswordToSymkey.md)|Legacy SHA-256 password KDF (byte `0x01`) that this replaces; still tried when a cipher names no KDF.|
|[FTSP28](FTSP28V1_PhraseToPriKey.md)|The same Argon2id parameters with an empty salt, deriving private keys from phrases.|
|[FTSP30](FTSP30V1_CryptoBundle.md)|Bundle type 4 and the KDF registry.|
|[FVEP8](../FVEP/FVEP8V1_Encryption.md)|Encryption envelope; defines `kdf`.|

## Reference Implementation

|Component|Location|
|---|---|
|`Kdf.Argon2id_No1_NrC7`|[FC-JDK/src/main/java/core/crypto/Kdf.java](../../FC-JDK/src/main/java/core/crypto/Kdf.java)|
|`Encryptor.encryptByPassword`|[FC-JDK/src/main/java/core/crypto/Encryptor.java](../../FC-JDK/src/main/java/core/crypto/Encryptor.java)|
|`Decryptor.decryptByPassword`|[FC-JDK/src/main/java/core/crypto/Decryptor.java](../../FC-JDK/src/main/java/core/crypto/Decryptor.java)|
|Vector test|[FC-JDK/src/test/java/core/crypto/CryptoVectorsTest.java](../../FC-JDK/src/test/java/core/crypto/CryptoVectorsTest.java)|
