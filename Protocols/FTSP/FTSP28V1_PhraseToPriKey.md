# FTSP28V1_PhraseToPriKey

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
|Title|PhraseToPriKey|
|Type|FTSP|
|SN|28|
|Ver|1|
|Category|Hashing / KeyDerivation|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-07-10|
|PID||

Parent rules: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**PhraseToPriKey** defines the Freeverse **brainwallet** key derivation procedure that converts a human-chosen **passphrase** into a **32-byte secp256k1 private key** using **Argon2id** (memory-hard KDF). The passphrase is UTF-8 encoded and fed to Argon2id with a **fixed, empty salt** and the parameter set identified by `Argon2id@No1_NrC7`; the 32-byte output is used directly as the private key scalar (big-endian). Because the salt is empty and every parameter is fixed, the **same passphrase always derives the same private key** on every conforming implementation — this is what lets a key created in one wallet (e.g. *Safe*) be reproduced in another (e.g. *Freer*).

## Motivation

- **Deterministic, portable brainwallets.** A user who remembers only a passphrase MUST be able to recover the exact same private key on any conforming wallet, with no stored file. This requires every input to Argon2id — including the salt — to be fixed and specified byte-for-byte.
- **Memory-hardness over legacy `SHA256(phrase)`.** The older brainwallet form `priKey = SHA256(phrase)` (still offered as an explicit, warned "SHA256" fallback in the wallet) is trivially GPU/ASIC-parallelizable. Argon2id with 64 MiB / 3 passes raises the per-guess cost by orders of magnitude for the same passphrase.
- **Cross-wallet interoperability was silently broken.** An implementation that passed a *non-empty* salt (see [Legacy salted variant](#legacy-salted-variant-non-conformant)) produced a different key for the same passphrase, stranding users migrating between wallets. This document pins the salt to **empty** to close that gap.

## Specification

### Algorithm ID

|Item|Value|
|---|---|
|KDF primitive id|`Argon2id@No1_NrC7`|
|Reference enum|`Kdf.Argon2id_No1_NrC7`|

This profile reuses the `Argon2id@No1_NrC7` KDF primitive (the same primitive used by `EncryptType.Password` when a wallet is configured for Argon2id). PhraseToPriKey is the specific **application** of that primitive with a **fixed empty salt** whose output is interpreted as a private key rather than a symmetric key.

### Parameters

All parameters are **fixed** and MUST NOT vary between implementations.

|Parameter|Value|
|---|---|
|Argon2 type|**Argon2id** (`ARGON2_id`)|
|Argon2 version|**0x13** (19, Argon2 v1.3 — `ARGON2_VERSION_13`)|
|Iterations (time cost `t`)|**3**|
|Memory (`m`)|**65536** KiB (**64 MiB**)|
|Parallelism (lanes `p`)|**1**|
|Salt|**empty** — zero-length byte array (`new byte[0]`)|
|Secret key / associated data|**none** (absent)|
|Output length (tag length)|**32** bytes|
|Password encoding|**UTF-8** of the passphrase characters|

> **Note on the empty salt.** RFC 9106 recommends a salt of at least 8 bytes. This profile intentionally uses a **zero-length** salt because a brainwallet has no stored per-key state to carry a random salt, and any salt deterministically derived *from the passphrase itself* adds no entropy against a targeted attacker. The memory-hard cost — not the salt — is the defense here. The BouncyCastle `Argon2BytesGenerator` used by the reference does not reject a zero-length salt.

### Procedure

```
function phraseToPriKey(phrase):        // phrase: Unicode string
    pwdBytes = UTF8_ENCODE(phrase)      // e.g. "Hello world!" -> 48 65 6c ...
    params = Argon2id(
        version     = 0x13,
        iterations  = 3,
        memoryKiB   = 65536,
        parallelism = 1,
        salt        = <empty, 0 bytes>,
        outLen      = 32)
    priKey32 = Argon2id_generate(params, pwdBytes)   // 32 bytes
    return priKey32                                   // secp256k1 scalar, big-endian
```

1. **Encode** the passphrase to bytes with **UTF-8**. In the reference this is `phrase.toCharArray()` passed to BouncyCastle, whose default `CharToByteConverter` is `PasswordConverter.UTF8`; this is byte-identical to `phrase.getBytes(UTF_8)`. No trimming, case-folding, or Unicode normalization is applied — the passphrase bytes are used exactly as entered.
2. **Derive** with Argon2id using the fixed [Parameters](#parameters) and the **empty salt**.
3. **Output** the 32-byte tag. These 32 bytes are the **secp256k1 private key**, interpreted as a big-endian unsigned integer `d`.

### Data Format

|Field|Description|
|---|---|
|Input `phrase`|Arbitrary-length Unicode string (the user's passphrase)|
|Output `priKey32`|**32** bytes — secp256k1 private key `d` (big-endian). Public key / FID are derived from `d` by the standard secp256k1 path (out of scope for this document).|

### Error Handling

- The 32-byte output is used as the private key **as-is**. A valid secp256k1 key requires `1 ≤ d < n` (the curve order). The probability that Argon2id yields `d = 0` or `d ≥ n` is negligible (≈ 2⁻¹²⁸), and the reference does **not** reduce mod `n` or reject-and-retry. Implementations claiming byte-for-byte compatibility MUST also use the raw 32 bytes without reduction, so that any future handling of that astronomically unlikely case is specified separately rather than diverging silently.
- Empty passphrase: callers SHOULD reject an empty passphrase at the UI layer; the KDF itself is well-defined on the empty byte string but such a key is worthless.

### Legacy salted variant (NON-CONFORMANT)

Some pre-release *Freer* builds derived a **non-empty** salt from the passphrase:

```
salt = first 16 bytes of SHA256( UTF8(phrase) )     // NON-CONFORMANT — do not use for new keys
```

and then ran the identical Argon2id parameters. This yields a **different** private key for the same passphrase and is therefore **incompatible** with this profile and with *Safe*. It is documented here only so that a key created by such a build can be **recovered**: re-run Argon2id for the affected passphrase with that 16-byte salt instead of the empty salt (see [TV-LEGACY](#tv-legacy--non-conformant-salted-variant-recovery-only)). New keys MUST use the empty salt.

### Legacy FreerForMac salt (NON-CONFORMANT)

FreerForMac builds before this correction ran the identical Argon2id parameters with a fixed ASCII salt:

```
salt = UTF8("fc.freer.phrase.v1")     // 18 bytes: 66632e66726565722e7068726173652e7631 — NON-CONFORMANT
```

This also yields a **different** private key from the conformant one for the same passphrase. Keys already stored in FreerForMac keep working, because the wallet stores the private key rather than the passphrase. The problem appears when the passphrase is re-entered on any conforming wallet: it derives a different FID. Implementations MAY offer this variant, behind a warning, only to recover such keys (see [TV-LEGACY-MAC](#tv-legacy-mac--non-conformant-freerformac-salt-recovery-only)).

## Test Vectors

All values are **hex** (lowercase). Argon2id is spec-deterministic, so output is independent of the BouncyCastle version. Parameters for every vector: Argon2id, v0x13, `t=3`, `m=65536` KiB, `p=1`, **empty salt**, 32-byte output.

### TV1 — ASCII passphrase `"Hello world!"`

|Field|Value|
|---|---|
|`phrase` (UTF-8)|`48656c6c6f20776f726c6421`|
|`salt`|*(empty)*|
|**`priKey32` (32 bytes)**|`3107f02758ff375bfed40885d7e7a24239e4a3bf55caa9cbea7ffeddfd7ddbf6`|

### TV2 — passphrase `"correct horse battery staple"`

|Field|Value|
|---|---|
|`phrase` (UTF-8)|`636f727265637420686f727365206261747465727920737461706c65`|
|`salt`|*(empty)*|
|**`priKey32` (32 bytes)**|`338c985a49e05a31cd2eb80a149dcea46df6ae7a487fc86a53a65da1fa8ec201`|

### TV3 — non-ASCII passphrase `"你好，世界"` (UTF-8 multibyte)

|Field|Value|
|---|---|
|`phrase` (UTF-8)|`e4bda0e5a5bdefbc8ce4b896e7958c`|
|`salt`|*(empty)*|
|**`priKey32` (32 bytes)**|`70d94592a1242af620ec77f7fe9a7b2df5e39e28059d50d2e30f9f119e165cad`|

### TV-LEGACY — non-conformant salted variant (recovery only)

Demonstrates the deprecated 16-byte phrase-derived salt for passphrase `"correct horse battery staple"`. **Not** a conformant PhraseToPriKey result — provided only so legacy keys can be recovered.

|Field|Value|
|---|---|
|`phrase` (UTF-8)|`636f727265637420686f727365206261747465727920737461706c65`|
|`salt` = `SHA256(phrase)[0:16]`|`c4bbcb1fbec99d65bf59d85c8cb62ee2`|
|**`priKey32` (32 bytes)**|`afa4d6d948331f7152dbc602a3e5b1900126225a0189bde2cbf1f0c5a2fd49b9`|

### TV-LEGACY-MAC — non-conformant FreerForMac salt (recovery only)

|Field|Value|
|---|---|
|`phrase` (UTF-8)|`636f727265637420686f727365206261747465727920737461706c65`|
|`salt` = `UTF8("fc.freer.phrase.v1")`|`66632e66726565722e7068726173652e7631`|
|**`priKey32` (32 bytes)**|`7ef2e239b0f1dc435f22cc6a02a19290e5b92753550b77d48797e8689c28de0d`|

All of these, plus a phrase containing a 4-byte UTF-8 character, are in [vectors/phrase.json](vectors/phrase.json).

**Verification:** Run `Kdf.Argon2id_No1_NrC7.deriveSymkey(phrase.toCharArray(), new byte[0])` (empty salt) for TV1–TV3; hex MUST match (generated 2026-07-10 against BouncyCastle `bcprov-jdk15to18` 1.70).

## Developer JSON example

PhraseToPriKey output is not serialized as `CryptoDataByte` JSON — it is a private key, not a ciphertext. This object records the derivation inputs/outputs for a single sample:

```json
{
  "kdf": "Argon2id@No1_NrC7",
  "phrase": "Hello world!",
  "phraseUtf8Hex": "48656c6c6f20776f726c6421",
  "argon2": { "type": "id", "version": 19, "iterations": 3, "memoryKiB": 65536, "parallelism": 1 },
  "salt": "",
  "outLen": 32,
  "priKey32": "3107f02758ff375bfed40885d7e7a24239e4a3bf55caa9cbea7ffeddfd7ddbf6"
}
```

## Security Considerations

- **This is a brainwallet — entropy comes entirely from the passphrase.** Argon2id slows guessing but cannot manufacture entropy. A low-entropy passphrase (dictionary phrase, quote, common password) is recoverable by an offline attacker even at 64 MiB / 3 passes. Users SHOULD choose passphrases with high entropy (e.g. a long diceware phrase). Wallets SHOULD warn accordingly.
- **No per-user salt.** Because the salt is empty (fixed), one precomputed effort against a candidate passphrase attacks *every* user who chose it, and there is no defense against precomputation for popular phrases. Memory-hardness raises the absolute cost but does not restore salt's uniqueness property. This trade-off is deliberate and is the price of stored-nothing portability.
- **Memory-hard parameters are a floor, not a ceiling.** 64 MiB / `t=3` / `p=1` targets interactive wallet use on mobile. They are **frozen** for this profile: changing any parameter changes the derived key and breaks recovery, so a stronger parameter set MUST be published as a **new** profile / KDF id (per [FTSP0 §6](FTSP0V1_FTSP.md#general-rules)), never as an in-place tweak.
- **Prefer Argon2id over the `SHA256(phrase)` fallback.** Implementations that expose the legacy single-SHA256 brainwallet MUST gate it behind an explicit user warning; it offers essentially no resistance to brute force.
- **Scalar validity.** See [Error Handling](#error-handling): the raw 32 bytes are used as the secp256k1 scalar; the out-of-range case is negligible and handled uniformly (no reduction) by all conforming implementations.
- **Do not confuse with the password KDFs (FTSP29, FTSP25).** [FTSP29](FTSP29V1_Argon2idPasswordToSymkey.md) runs these same Argon2id parameters, but salts them with each cipher's random IV to derive a *symmetric* key. The legacy [FTSP25](FTSP25V1_PasswordToSymkey.md) derives that symmetric key with two SHA-256 passes instead. PhraseToPriKey derives an *asymmetric* private key with a fixed empty salt, so the key can be reproduced from the phrase alone. The three procedures have different threat models and MUST NOT be substituted for one another.

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-07-10|C_armX, No1_NrC7|Argon2id brainwallet (phrase → secp256k1 private key), fixed empty salt; parameters, TV1–TV3, legacy salted-variant recovery vector, interop notes.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|[FTSP0](FTSP0V1_FTSP.md)|FTSP governance; breaking parameter changes require a new profile / KDF id.|
|[FTSP13 HKDF](FTSP13V1_HKDF.md)|Sibling KDF (HMAC-SHA512, RFC 5869). HKDF expands existing key material; PhraseToPriKey stretches a low-entropy passphrase with a memory-hard function.|
|[FTSP29 Argon2idPasswordToSymkey](FTSP29V1_Argon2idPasswordToSymkey.md)|The same Argon2id parameters, salted with the cipher IV, deriving *symmetric* keys for `EncryptType.Password`.|
|[FTSP25 PasswordToSymkey](FTSP25V1_PasswordToSymkey.md)|Legacy SHA-256 password KDF; unrelated to phrase keys.|
|RFC 9106|Argon2 memory-hard function definition (this profile fixes the id/version/cost parameters).|

## Reference Implementation

|Component|Location|
|---|---|
|`Kdf.Argon2id_No1_NrC7` (Argon2id primitive)|[FC-JDK/src/main/java/core/crypto/Kdf.java](../../FC-JDK/src/main/java/core/crypto/Kdf.java)|
|Android port of the primitive|`Freer/FC-AJDK/src/main/java/com/fc/fc_ajdk/core/crypto/Kdf.java`|
|Phrase → private key caller (empty salt)|`Freer/app/src/main/java/com/fc/freer/myKeys/CreateKeyByPhraseActivity.java` (`deriveKeyInfoAsync`, `DerivationMode.ARGON2ID`)|
|Cross-implementation vectors|[vectors/phrase.json](vectors/phrase.json), checked by FC-JDK `CryptoVectorsTest`|
