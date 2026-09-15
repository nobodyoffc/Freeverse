# FTSP30V1_CryptoBundle

## Contents

[Summary](#summary)

[Abstract](#abstract)

[Motivation](#motivation)

[Specification](#specification)

[Test Vectors](#test-vectors)

[Security Considerations](#security-considerations)

[Versioning](#versioning)

[Related Protocols](#related-protocols)

[Reference Implementation](#reference-implementation)

---

## Summary

|Field|Content|
|---|---|
|Title|CryptoBundle|
|Type|FTSP|
|SN|30|
|Ver|1|
|Category|Encoding|
|Status|Draft|
|Author|No1_NrC7|
|Created|2026-09-15|
|PID||

Parent rules: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**CryptoBundle** is the binary form of an encrypted `CryptoDataByte` ([FVEP8](../FVEP/FVEP8V1_Encryption.md)). It carries the same material as the JSON form — algorithm, encryption type, sender public key, key name, KDF, IV, ciphertext and `sum` — as positional, fixed-width fields. The header costs 25 bytes for a Symkey GCM cipher and 52 bytes for an AsyTwoWay one, plus the 16-byte tag. FIMP v2 message bodies, FUDP packets and wallet key backups use it. This document is the normative byte layout; FVEP8 defines what the fields mean.

## Motivation

- **One layout for every implementation.** FC-JDK, both Android wallets, SafeForMac and FreerForMac all read and write bundles. The layout used to live only in FVEP8 prose and in code, and the implementations drifted apart.
- **Recording the password KDF.** The type-3 Password layout does not say which KDF produced the key, so readers had to guess. Type 4 adds one KDF byte ([FTSP29](FTSP29V1_Argon2idPasswordToSymkey.md)).
- **Compactness.** A binary header avoids JSON field names and a Base64 layer inside messages and packets.

## Specification

### Layout

Fields appear in this order.

|#|Field|Length (bytes)|Present when|
|---|---|---|---|
|1|`alg`|6|Always. The first 6 bytes of the algorithm's PID ([Algorithm prefixes](#algorithm-prefixes)).|
|2|`type`|1|Always ([Type byte](#type-byte)).|
|3|`pubkeyA`|33; 32 for `X25519AesGcm256@No1_NrC7`|Type 1 or 2.|
|4|`keyName`|6|Type 0. `SHA256(symkey)[0:6]`.|
|5|`kdfId`|1|Type 4 ([KDF registry](#kdf-registry)).|
|6|`iv`|12 or 16 ([IV length](#iv-length))|Always.|
|7|`cipher`|The rest of the bundle, minus `sum`; at least 1|Always. For AEAD profiles, ciphertext ‖ tag.|
|8|`sum`|0, 4 or 32 ([sum length](#sum-length))|Non-AEAD profiles.|

At most one of fields 3–5 is present, because each belongs to a different type.

### Algorithm prefixes

|`alg` display name|Prefix (PID, first 6 bytes)|Legacy prefix (read only)|
|---|---|---|
|`AesCbc256@No1_NrC7`|`51515d32878c`|`000000000001`|
|`EccK1AesCbc256@No1_NrC7`|`3ea47cd61381`|`000000000002`|
|`AesGcm256@No1_NrC7`|`76f7b226a8b3`|`000000000003`|
|`EccK1AesGcm256@No1_NrC7`|`a5acd7077805`|`000000000004`|
|`X25519AesGcm256@No1_NrC7`|`b4a25b3c3043`|`000000000005`|
|`ChaCha20@No1_NrC7`|`bcc39a9628e2`|`000000000006`|
|`EccK1ChaCha20@No1_NrC7`|`355319f84bd5`|`000000000007`|
|`ChaCha20Poly1305@No1_NrC7`|`b1788c3b7320`|`000000000008`|
|`EccK1ChaCha20Poly1305@No1_NrC7`|`d1691132aee1`|`000000000009`|
|`ECC256k1-AES256CBC` (BitCore, [FTSP16](FTSP16V1_BitCore_EccAes256.md))|`e308bc027946`|—|

Writers MUST use the PID prefix. Readers MUST accept both columns.

**BitCore is not a native bundle profile.** Bitcore and bitcoin-qt ciphers are the raw `encbuf` of [FTSP16](FTSP16V1_BitCore_EccAes256.md) — `ephemeralPub ‖ IV ‖ ciphertext ‖ HMAC-SHA256` — with no prefix and no type byte, and they have no JSON form. That layout MUST stay unchanged so these ciphers remain compatible with Bitcore software. The `e308bc027946` row covers only FC's optional wrapper around the same bytes (`prefix ‖ type 1 ‖ ephemeralPub(33) ‖ IV(16) ‖ ciphertext ‖ HMAC(32)`), which readers accept. Anything exchanged with Bitcore software MUST use the raw `encbuf`.

### Type byte

|Byte|Meaning|JSON form|
|---|---|---|
|0|Symkey|`type: Symkey`|
|1|AsyOneWay; `pubkeyA` is the ephemeral sender key|`type: AsyOneWay`|
|2|AsyTwoWay; `pubkeyA` is the sender's key|`type: AsyTwoWay`|
|3|Password, KDF not recorded (legacy layout)|`type: Password`, no `kdf`|
|4|Password, followed by `kdfId`|`type: Password` with `kdf`|

### KDF registry

|Byte|JSON `kdf`|Specification|Status|
|---|---|---|---|
|`0x00`|—|Invalid|—|
|`0x01`|`Sha256Iv@No1_NrC7`|[FTSP25](FTSP25V1_PasswordToSymkey.md)|Decrypt only|
|`0x02`|`Argon2id@No1_NrC7`|[FTSP29](FTSP29V1_Argon2idPasswordToSymkey.md)|Default|

A new KDF takes the next unused byte. Bytes are never reused or renumbered.

### IV length

12 bytes for `AesGcm256`, `EccK1AesGcm256`, `X25519AesGcm256`, `ChaCha20`, `EccK1ChaCha20`, `ChaCha20Poly1305` and `EccK1ChaCha20Poly1305`. 16 bytes for every other algorithm.

### sum length

- **0** for the AEAD profiles: `AesGcm256`, `EccK1AesGcm256`, `X25519AesGcm256`, `ChaCha20Poly1305`, `EccK1ChaCha20Poly1305`.
- **32** for the BitCore wrapper (the unchanged Bitcore HMAC-SHA256).
- **4** for every other algorithm (the FVEP8 `sum`).

### Parsing

1. Reject a bundle shorter than 8 bytes.
2. Map `alg` through [Algorithm prefixes](#algorithm-prefixes). Reject an unknown prefix.
3. Read `type`. Reject any byte other than 0–4.
4. Read the one conditional field the type calls for (`pubkeyA`, `keyName` or `kdfId`). Reject if the bundle ends first, or if `kdfId` is not registered.
5. Read `iv`. Reject if the bundle ends first.
6. `cipher` is the remaining bytes minus the `sum` length. Reject if that leaves less than 1 byte.
7. Read `sum`.

A reader MUST NOT attempt decryption after rejecting. Readers written before type 4 existed reject type-4 bundles at step 3; that is the intended transition behaviour, since they fail cleanly instead of decrypting wrongly.

### Writing

1. Write the fields in layout order.
2. For **Password**, write type 4 and the `kdfId` when the KDF is known; otherwise write type 3.
3. **Transition rule:** a deployment MUST NOT start writing type 4 until every reader it exchanges bundles with accepts type 4. Until then, writers produce type 3. In the reference, the switch is `CryptoDataByte.WRITE_PASSWORD_BUNDLE_WITH_KDF`.
4. New bundles MUST use an AEAD profile: `AesGcm256@No1_NrC7` for Symkey and Password, `EccK1AesGcm256@No1_NrC7` for AsyOneWay and AsyTwoWay. The other prefixes exist to read existing data ([FVEP8](../FVEP/FVEP8V1_Encryption.md) rule 9).

### Text form

Where a bundle travels as text, such as `prikeyCipher` in wallet key backups, it is standard Base64 with padding (RFC 4648 §4).

### Decryption

Choose the secret by `type`:

- Type 0: the symkey.
- Type 1: the recipient's private key.
- Type 2: a private key plus the peer's public key.
- Types 3 and 4: the password. For type 4, run the named KDF; for type 3, try Argon2id and then Sha256Iv as [FTSP29](FTSP29V1_Argon2idPasswordToSymkey.md) describes.

Then decrypt with the `alg` profile.

## Test Vectors

Inputs are the [FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples) example keys, plaintext `Hello world!`, and IVs `000102…0b` (12 bytes) or `000102…0f` (16 bytes). The complete set, with rejection cases, is [vectors/bundle.json](vectors/bundle.json).

### TV-BUNDLE-1 — Symkey, AesGcm256 (type 0)

|Field|Bytes|
|---|---|
|`alg`|`76f7b226a8b3`|
|`type`|`00`|
|`keyName`|`6ede688dea3b`|
|`iv`|`000102030405060708090a0b`|
|`cipher`|`508e39217d5becfa840674d93c3241e99f003519262284412e7613fd`|

Bundle: `76f7b226a8b3006ede688dea3b000102030405060708090a0b508e39217d5becfa840674d93c3241e99f003519262284412e7613fd`

### TV-BUNDLE-2 — AsyTwoWay, EccK1AesGcm256 (type 2), fidA to fidB

|Field|Bytes|
|---|---|
|`alg`|`a5acd7077805`|
|`type`|`02`|
|`pubkeyA`|`030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a`|
|`iv`|`000102030405060708090a0b`|
|`cipher`|`d798368a31ea17e09625f5973982e59a7f808c79d3ee69153157164e`|

Decrypt with fidB's private key and fidA's public key.

### TV-BUNDLE-3 — Password, AesGcm256, Argon2id, legacy layout (type 3)

`76f7b226a8b3` `03` `000102030405060708090a0b` `61c04a9cf0fd74fea2d38af8e4b0d64e4acbd597a4e2c43d2c8645ee`

### TV-BUNDLE-4 — the same cipher with its KDF recorded (type 4)

`76f7b226a8b3` `04` `02` `000102030405060708090a0b` `61c04a9cf0fd74fea2d38af8e4b0d64e4acbd597a4e2c43d2c8645ee`

The password is `MyPassword`. TV-BUNDLE-3 and TV-BUNDLE-4 hold identical ciphertext; only the header differs.

### TV-BUNDLE-5 — Password, AesCbc256, Sha256Iv (type 4)

`51515d32878c` `04` `01` `000102030405060708090a0b0c0d0e0f` `748c50f701394b6d78cea5fdbc364a78` `2d917a6e`

The last two groups are `cipher` (one 16-byte block) and the 4-byte `sum`.

## Security Considerations

- **The header is not authenticated data.** Changing `alg`, `type`, `pubkeyA` or `kdfId` changes the derived key or the cipher, so decryption fails instead of producing wrong plaintext. For AEAD profiles the 128-bit tag guarantees that; for `sum` profiles a forged header passes with probability about 2⁻³².
- **Downgrading type 4 to type 3** only forces trial decryption. It does not weaken the key.
- **`keyName` links bundles.** Bundles under the same symkey share a `keyName`. That is intended, so group members can pick the right key rotation without trying each one, but it does reveal which bundles share a key.
- **Bounds-check every field.** A truncated bundle MUST be rejected, not read past its end. Implementations that skipped these checks crashed on short input.

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-09-15|No1_NrC7|Normative bundle layout, taken from FVEP8 and the reference; PID and legacy prefixes; type 4 with KDF id; KDF registry; transition rule; TV-BUNDLE-1–5.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|[FVEP8](../FVEP/FVEP8V1_Encryption.md)|Meaning of every field; the JSON form of the same cipher.|
|[FTSP11](FTSP11V1_Ecc256K1AesGcm256.md)–[FTSP27](FTSP27V1_EccK1ChaCha20Poly1305.md)|The cipher profiles that `alg` names.|
|[FTSP25](FTSP25V1_PasswordToSymkey.md), [FTSP29](FTSP29V1_Argon2idPasswordToSymkey.md)|The registered password KDFs.|
|[FIMP0V2](../IM/FIMP0V2_FIMP.md)|Sealed message bodies are bundles.|
|[FUDP1](../FUDP/FUDP1V1_CoreTransport.md), [FUDP4](../FUDP/FUDP4V1_Security.md)|Encrypted packet payloads are bundles.|

## Reference Implementation

|Component|Location|
|---|---|
|`CryptoDataByte.toBundle`, `fromBundle`, `toBase64`, `fromBase64`|[FC-JDK/src/main/java/core/crypto/CryptoDataByte.java](../../FC-JDK/src/main/java/core/crypto/CryptoDataByte.java)|
|Lengths and the type-4 byte|[FC-JDK/src/main/java/core/crypto/CryptoConstants.java](../../FC-JDK/src/main/java/core/crypto/CryptoConstants.java)|
|KDF ids|[FC-JDK/src/main/java/core/crypto/Kdf.java](../../FC-JDK/src/main/java/core/crypto/Kdf.java)|
|Vector test|[FC-JDK/src/test/java/core/crypto/CryptoVectorsTest.java](../../FC-JDK/src/test/java/core/crypto/CryptoVectorsTest.java)|
