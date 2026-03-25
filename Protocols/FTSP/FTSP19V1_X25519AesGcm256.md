# FTSP19V1_X25519AesGcm256

## Summary

|Field|Content|
|---|---|
|Title|X25519AesGcm256|
|Type|FTSP|
|SN|19|
|Ver|1|
|Category|Encryption / KeyExchange|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-24|
|PID||

Parent: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

**X25519AesGcm256** composes **[FTSP18](FTSP18V1_X25519.md)** (32-byte keys, 32-byte shared secret), **[FTSP13](FTSP13V1_HKDF.md)** HKDF with **`info = "hkdf"`** and **12-byte** salt = nonce, and **[FTSP12](FTSP12V1_AesGcm256.md)** AES-256-GCM (no separate **`sum`**). **`AlgorithmId`**: **`X25519AesGcm256@No1_NrC7`**; bundle last byte **`0x05`**; **`pubkeyA`** in bundles is **32** bytes (not 33 like secp256k1).

## Composition (normative)

```
symkey = FTSP13.hkdf( FTSP18.getSharedSecret(priKey, pubKey), salt = nonce12, info = "hkdf", L = 32 )
cipher = FTSP12.aes_gcm256_encrypt(symkey, iv = nonce12, plaintext)
```

Decrypt mirrors; inner decrypt may temporarily use **`FC_AesGcm256_No1_NrC7`** then restore **`FC_X25519AesGcm256_No1_NrC7`**.

## Interoperability

- **Pubkey length 32** in **`CryptoDataByte` bundles** ([`CryptoDataByte`](../../FC-JDK/src/main/java/core/crypto/CryptoDataByte.java) branching).
- Must **not** use secp256k1 **33-byte** pubkeys in this profile.

## Test Vectors

### Regression

FC-JDK **`FtspProtocolVectorTest.ftsp19_x25519_aes_gcm_roundTrip`** — **AsyTwoWay** with **`priA` / `priB` / pubs** from [FTSP18 TV-FTSP18-1](FTSP18V1_X25519.md#tv-ftsp18-1--reciprocal-agreement--hkdf), IV **`3132333435363738393a3b3c`**, plaintext UTF-8 **`FTSP19-X25519-GCM`**, full encrypt/decrypt via **`Encryptor`** / **`Decryptor`**.

## Developer JSON example

**AsyTwoWay** with the same **X25519** keys as [FTSP18 Developer JSON example](FTSP18V1_X25519.md#developer-json-example); IV = 12-byte `000102030405060708090a0b`; plaintext UTF-8 **`Hello world!`**.

```json
{
  "type": "AsyTwoWay",
  "alg": "X25519AesGcm256@No1_NrC7",
  "cipher": "n331/g1nWO0ep8/C5bcerHF+jtNDd4CcOS0KWw==",
  "pubkeyA": "7a1a4e709bf085ac494aba0469b9b1eda0ab1f78b16aabb79ffeda90623e8522",
  "pubkeyB": "132c442be010fbd57e72603328aa76e71fccc1503aae219327d14d9c9993f472",
  "iv": "000102030405060708090a0b"
}
```

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-03-24|C_armX, No1_NrC7|Initial FTSP19 from `X25519AesGcm256.java`.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FTSP12|AES-GCM layer.|
|FTSP13|HKDF.|
|FTSP18|X25519 agreement.|
|FVEP8|Encrypt envelope; 32-byte pubkeyA for this alg.|

## Reference Implementation

[FC-JDK/src/main/java/core/crypto/Algorithm/X25519AesGcm256.java](../../FC-JDK/src/main/java/core/crypto/Algorithm/X25519AesGcm256.java)
