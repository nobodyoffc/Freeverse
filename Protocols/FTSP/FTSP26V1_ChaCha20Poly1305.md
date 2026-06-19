# FTSP26V1_ChaCha20Poly1305

## Summary

|Field|Content|
|---|---|
|Title|ChaCha20Poly1305|
|Type|FTSP|
|SN|26|
|Ver|1|
|Category|Encryption|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-05-31|
|PID||

Parent: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**ChaCha20-Poly1305** (reference) is the **AEAD** counterpart of plain ChaCha20 ([FTSP20](FTSP20V1_ChaCha20.md)): **`Cipher.getInstance("ChaCha20-Poly1305")`**, **32-byte** key, **12-byte** nonce/IV, **`GCMParameterSpec(128, iv)`** (RFC 8439). Unlike [FTSP20](FTSP20V1_ChaCha20.md), authentication is **built-in** via the **128-bit Poly1305 tag** appended to the ciphertext (**`cipher` = ciphertext ∥ tag**), so there is **no `sum`** — integrity is provided by the tag, exactly as in **AES-GCM** ([FTSP12](FTSP12V1_AesGcm256.md)). The reference sets **`did = SHA256( SHA256(plaintext) )`** during encrypt (Guava SHA-256 over plaintext, then **`Decryptor.sha256`**). **`AlgorithmId`**: **`ChaCha20Poly1305@No1_NrC7`**; bundle last byte **`0x08`**.

## Specification

- **Transform:** **`ChaCha20-Poly1305`** (key spec algorithm **`ChaCha20`**).
- **Tag:** **128-bit** Poly1305 tag (**`CryptoConstants.GCM_TAG_LENGTH_BITS`**), appended to ciphertext by the JCA AEAD implementation.
- **Encrypt:** stream plaintext, hash plaintext for **`did`**, set **`FC_ChaCha20Poly1305_No1_NrC7`**, default **`EncryptType.Symkey`**; **no `makeSum4`** (AEAD).
- **Decrypt:** stream decrypt (tag verified by `doFinal`); **`makeDid()`** on plaintext; **no `checkSum()`**. A failed tag check yields **`Code1029FailedToDecrypt`**.
- **Validation:** key MUST be **32** bytes (**`Code4008WrongKeyLength`**); IV MUST be **12** bytes (**`Code4009MissingIv`**).

### EncryptType and bundle (FVEP8)

|Item|Value|
|---|---|
|`EncryptType`|**Symkey** (byte **0**)|
|Bundle `algBytes` (6 bytes)|`00 00 00 00 00 08`|
|Binary bundle after `algBytes` + type|**`keyName` [6]** + **`iv` [12]** + **`cipher` [variable, ciphertext ∥ tag]**|
|`keyName`|First **6** bytes of **`SHA256(symkey)`** (FVEP8)|
|`sum`|Omitted for this profile (AEAD)|

## Test Vectors

### TV-FTSP26-1 — Symkey ChaCha20-Poly1305

|Field|Value|
|---|---|
|`symkey` (32 bytes, hex)|`4142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f60`|
|`iv` / nonce (12 bytes, hex)|`6162636465666768696a6b6c`|
|Plaintext (UTF-8)|**`FTSP26-ChaChaPoly`**|

**Decrypt check:** decrypt with the same **`symkey`**, **`iv`**, and **`cipher`** (ciphertext ∥ 16-byte tag) MUST yield plaintext **`FTSP26-ChaChaPoly`**; a modified tag or ciphertext MUST fail authentication.

### Regression

FC-JDK **`FtspProtocolVectorTest.ftsp26_chacha20poly1305_sym_roundTrip`**.

## Developer JSON example

**Symkey** from [FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples); IV = 12-byte `000102030405060708090a0b`; plaintext UTF-8 **`Hello world!`**.

```json
{
  "type": "Symkey",
  "alg": "ChaCha20Poly1305@No1_NrC7",
  "cipher": "<ciphertext ∥ Poly1305 tag, Base64>",
  "keyName": "6ede688dea3b",
  "iv": "000102030405060708090a0b"
}
```

## Security Considerations

- **Nonce uniqueness:** MUST NOT reuse **`iv`** with the same **32-byte** key; reuse breaks ChaCha20-Poly1305 confidentiality and integrity.
- **Key handling:** **`symkey`** MUST NOT appear in public JSON; protect at rest and in memory.
- **Authentication:** Integrity relies on the Poly1305 tag; a failed decrypt indicates tampering or a wrong key/IV.
- **Tag length:** Fixed at **128** bits in the reference; do not truncate.

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-05-31|C_armX, No1_NrC7|Initial FTSP26 from `ChaCha20Poly1305.java`.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FTSP20|Plain ChaCha20 (no AEAD; uses `sum`).|
|FTSP12|AES-GCM AEAD alternative (same `ciphertext ∥ tag`, no `sum`).|
|FTSP21|ECDH + HKDF + ChaCha20 (asymmetric envelope).|
|FVEP8|12-byte IV; symkey envelope, `keyName`; no `sum` for AEAD profiles.|
|FVEP2|`did` definition (double SHA-256 of plaintext).|

## Reference Implementation

[FC-JDK/src/main/java/core/crypto/Algorithm/ChaCha20Poly1305.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/ChaCha20Poly1305.java)
