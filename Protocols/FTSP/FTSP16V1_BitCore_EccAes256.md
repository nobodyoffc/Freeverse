# FTSP16V1_BitCore_EccAes256

## Summary

|Field|Content|
|---|---|
|Title|BitCore EccAes256 (ECIES-style)|
|Type|FTSP|
|SN|16|
|Ver|1|
|Category|Encryption / KeyExchange|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

Parent: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

The **`Bitcore`** helper implements a **compact binary** encrypt/decrypt path on **secp256k1**: ephemeral ECDH, **SHA-512** of a **32-byte** fixed-width shared secret, split into **kE** (AES key) and **kM** (HMAC key), **random 16-byte IV**, **AES/CBC/PKCS5Padding**, **HMAC-SHA256** over **`IV ‖ ciphertext`**, full **32-byte** MAC tag. The output **`encbuf`** layout is **`ephemeralPub ‖ IV ‖ c ‖ d`**. This format is **not** registered as a `CryptoDataByte` / FVEP8 **`AlgorithmId`**; it is a **standalone** API (`Bitcore.encrypt` / `decrypt`).

## Specification

### Curve and keys

- **secp256k1**; recipient **Java `PublicKey`** / decrypt with **32-byte** private scalar (`createPrivateKey`).
- **Ephemeral** key pair per encryption; **compressed** ephemeral public prefix **0x02** / **0x03** (33 bytes) or **uncompressed** **0x04** (65 bytes) — decrypt parses prefix from **`encbuf[0]`**.

### Shared secret

`generateSharedSecret`: **ECDHBasicAgreement**, then **`bigIntegerToBytes(sharedSecret, 32)`** — **32-byte big-endian**, right-padded / trimmed (see `Bitcore.bigIntegerToBytes`). **Distinct** from **`Ecc256K1.toByteArray()`** ([FTSP15](FTSP15V1_Ecc256K1AesCbc256.md)) and from **`Ecc256K1Hkdf`** ([FTSP11](FTSP11V1_Ecc256K1AesGcm256.md)).

### Key split

- **`kEkM = SHA512(sharedSecret32)`** (BouncyCastle **`SHA512Digest`**).
- **`kE = kEkM[0..31]`**, **`kM = kEkM[32..63]`**.

### AE encryption

- **`AES/CBC/PKCS5Padding`**, **`BC`**, **`kE`**, random **`iv` (16)`**.
- **`ciphertextPayload = IV ‖ c`** (IV prepended for MAC input).

### Integrity

- **`d = HMAC-SHA256(key = kM, data = ciphertextPayload)`**, **32** bytes (reference: **`SHORT_TAG = false`**).

### **`encbuf`**

With **`NO_KEY = false`**: **`ephemeralPub ‖ ciphertextPayload ‖ d`**.

### Decrypt

Parse pubkey, split **`ciphertextPayload`** and **`d`**, re-derive **kE/kM**, recompute HMAC, **constant-time** compare; decrypt CBC.

## Test Vectors

### TV-FTSP16-1 — deterministic `SecureRandom` (JDK)

**Inputs:** Recipient private key 32 × **`0x30`** (`Bitcore.createKeyPair`), plaintext UTF-8 **`FTSP16-BitCore`**, `SecureRandom` **`SHA1PRNG`** seeded with 32-byte hex **`0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef`**, `Bitcore.encrypt(..., rng)`.

|**`encbuf` (hex, full)**|`0327c600b42441021486c84f43cf8127e728985678797884d4fa2cf98a535b0287a887d5c91a9f2565948d8626d77dbd060d5d9c324b5efedfabdba2cf6e815264feac70fa9a338bab4483d51cb17167d6ab2521646c4fd696a1a2aebf71f11731`|

**Caveat:** **`SHA1PRNG`** output can differ across JDK vendors/versions; this vector matches **FC-JDK** tests on the reference JDK used when the test was added.

### Regression

FC-JDK **`FtspProtocolVectorTest.ftsp16_bitcore_deterministic_rng_roundTrip`**.

## Developer JSON example

**BitCore** does not use `CryptoDataByte` JSON. Recipient = **fidA** ([FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples)); plaintext UTF-8 **`Hello world!`**; **`SHA1PRNG`** seeded with 32-byte hex `0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef` (same seed pattern as the regression test, but a different recipient key than TV-FTSP16-1). **`encbuf`** layout: **`ephemeralPub ‖ IV ‖ ciphertext ‖ HMAC-SHA256`**.

```json
{
  "note": "encbuf = ephemeralPub ‖ IV ‖ ciphertext ‖ HMAC-SHA256 (see FTSP16); SHA1PRNG seed fixed for reproducibility on typical OpenJDK.",
  "recipientFid": "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK",
  "encbuf": "0327c600b42441021486c84f43cf8127e728985678797884d4fa2cf98a535b0287a887d5c91a9f2565948d8626d77dbd0645445dd55ce1be0ef460ba3c1a5f78b5d76e41040effda36f3a9eea6228e1fc3cf23221db5f89def9497128fef608c93"
}
```

## Security Considerations

- Prefer registered **FVEP8** profiles (**FTSP11/12/19**, etc.) for new interchange unless this exact **`Bitcore`** layout is required.
- **Malleability**: CBC + MAC over **IV‖c**; verify MAC before decrypting plaintext in security-critical code (reference returns **null** on MAC failure before AES decrypt).

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-03-24|C_armX, No1_NrC7|Initial FTSP16 from `Bitcore.java`.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FTSP11 / FTSP15|Other secp256k1 ECDH byte encodings — not interchangeable.|
|FVEP8|Standard encrypted-object envelope (different from this raw buffer).|

## Reference Implementation

[FC-JDK/src/main/java/core/crypto/Algorithm/Bitcore.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/Bitcore.java)
