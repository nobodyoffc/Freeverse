# FTSP27V1_EccK1ChaCha20Poly1305

## Summary

|Field|Content|
|---|---|
|Title|EccK1ChaCha20Poly1305|
|Type|FTSP|
|SN|27|
|Ver|1|
|Category|Encryption / KeyExchange|
|Status|Draft (asymmetric wiring reserved)|
|Author|C_armX, No1_NrC7|
|Created|2026-05-31|
|PID||

Parent: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**EccK1ChaCha20Poly1305** is the **AEAD** counterpart of [FTSP21](FTSP21V1_EccK1ChaCha20.md): it chains **secp256k1 ECDH** with a **32-byte** shared secret (**`Ecc256K1Hkdf.getSharedSecret`** — same encoding as [FTSP11](FTSP11V1_Ecc256K1AesGcm256.md)), **[FTSP13](FTSP13V1_HKDF.md)** key derivation with **`info = ASCII "hkdf-chacha20"`**, **12-byte** salt = nonce, and **[FTSP26](FTSP26V1_ChaCha20Poly1305.md)** ChaCha20-Poly1305 encryption. Because the symmetric primitive is **AEAD**, integrity is provided by the **128-bit Poly1305 tag** (**`cipher` = ciphertext ∥ tag**) and there is **no `sum`** (unlike [FTSP21](FTSP21V1_EccK1ChaCha20.md), which uses `sum`). **`AlgorithmId`**: **`EccK1ChaCha20Poly1305@No1_NrC7`**; bundle last byte **`0x09`**.

## Composition (normative)

```
Z = Ecc256K1Hkdf.getSharedSecret(priKey, pubKey)   // 32 bytes, FTSP11-style
symkey = HKDF.hkdf(Z, salt = nonce12, info = "hkdf-chacha20", L = 32)
... ChaCha20-Poly1305.encrypt per FTSP26 (cipher = ciphertext ∥ Poly1305 tag) ...
```

**Domain separation:** **`info`** MUST be the **`hkdf-chacha20`** bytes (the AEAD profile reuses ChaCha20 key derivation) — using **`hkdf`** would **not** match FC-JDK.

## Implementation status

This profile is **reserved**: the **`AlgorithmId`** (`EccK1ChaCha20Poly1305@No1_NrC7`), the **bundle byte** `0x09`, the **12-byte IV** length, and the **AEAD `sum`-exclusion** are wired in FC-JDK (`CryptoDataByte`, `Encryptor`, `Decryptor`), but the dedicated **`Ecc256K1ChaCha20Poly1305`** helper is **not yet implemented**. In the current reference, **`FC_EccK1ChaCha20Poly1305_No1_NrC7`** has no explicit case in the asymmetric `asyKeyToSymkey` switches and therefore falls through to the **`default`** branch (legacy **`Ecc256K1.asyKeyToSymkey`**, a SHA-512 KDF) — which does **not** match the intended HKDF composition above. The asymmetric round-trip test is deferred (`CryptoAlgorithmSuiteTest`: *"FC_EccK1ChaCha20Poly1305 asymmetric encryption requires wiring `Ecc256K1ChaCha20Poly1305` — tested separately when implemented."*).

Implementations claiming **FTSP27** conformance MUST follow [Composition](#composition-normative) (ECDH + HKDF `info = "hkdf-chacha20"` + FTSP26 AEAD) once the helper is wired; the legacy default-branch behaviour is **non-conformant** and MUST NOT be relied upon.

## EncryptType and bundle (FVEP8)

|Item|Value|
|---|---|
|`EncryptType`|**AsyOneWay** / **AsyTwoWay**|
|Bundle `algBytes` (6 bytes)|`00 00 00 00 00 09`|
|`iv` / nonce|**12** bytes|
|`cipher`|ciphertext ∥ **16-byte** Poly1305 tag|
|`sum`|Omitted for this profile (AEAD)|

## Test Vectors

### Regression

Deferred — pending `Ecc256K1ChaCha20Poly1305` wiring (see [Implementation status](#implementation-status)). Once implemented, mirror [FTSP21](FTSP21V1_EccK1ChaCha20.md) (e.g. **AsyTwoWay**, sender **`ECKey.fromPrivate(5)`**, recipient **`ECKey.fromPrivate(999983)`**, 12-byte IV, plaintext **`FTSP27`**) with decrypt asserting the Poly1305 tag.

## Developer JSON example

**AsyTwoWay:** sender **fidA**, recipient **fidB**; IV = 12-byte `000102030405060708090a0b`; plaintext UTF-8 **`Hello world!`**. Keys: [FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples).

```json
{
  "type": "AsyTwoWay",
  "alg": "EccK1ChaCha20Poly1305@No1_NrC7",
  "cipher": "<ciphertext ∥ Poly1305 tag, Base64>",
  "pubkeyA": "030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a",
  "pubkeyB": "02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67",
  "iv": "000102030405060708090a0b"
}
```

(No `sum` field — AEAD integrity is carried by the Poly1305 tag inside `cipher`.)

## Security Considerations

- **Nonce uniqueness:** MUST NOT reuse **`iv`** with the same derived **`symkey`**; reuse breaks ChaCha20-Poly1305 confidentiality and integrity.
- **Authentication:** Integrity relies on the Poly1305 tag; a failed decrypt indicates tampering or wrong keys/IV.
- **Domain separation:** HKDF **`info`** binds the derived key to this cipher suite; do not share derived keys across profiles.
- **Non-conformant fallback:** Until the helper is wired, do not use the default-branch SHA-512 derivation for interop — it is not the FTSP27 key schedule.

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-05-31|C_armX, No1_NrC7|Initial FTSP27 (AEAD counterpart of FTSP21); asymmetric wiring reserved.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FTSP21|Plain ChaCha20 ECC envelope (uses `sum`); same ECDH + HKDF `info`.|
|FTSP26|ChaCha20-Poly1305 AEAD symmetric primitive (no `sum`).|
|FTSP11|Same ECDH encoding; different `info` and symmetric cipher.|
|FTSP13|HKDF.|
|FVEP8|Envelope; no `sum` for AEAD profiles.|

## Reference Implementation

|Component|Location|
|---|---|
|`ChaCha20Poly1305` (symmetric primitive)|[FC-JDK/src/main/java/core/crypto/Algorithm/ChaCha20Poly1305.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/ChaCha20Poly1305.java)|
|`Ecc256K1ChaCha20` (FTSP21 sibling; ECDH+HKDF pattern)|[FC-JDK/src/main/java/core/crypto/Algorithm/Ecc256K1ChaCha20.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/Ecc256K1ChaCha20.java)|
|`AlgorithmId`|[FC-JDK/src/main/java/data/fcData/AlgorithmId.java](../../FC-JDK/src/main/java/data/fcData/AlgorithmId.java)|
|`Encryptor` / `Decryptor` (dispatch)|[FC-JDK/src/main/java/core/crypto/Encryptor.java](../../FC-JDK/src/main/java/core/crypto/Encryptor.java)|
