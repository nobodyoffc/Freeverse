# FTSP0V1_FTSP

## Contents

[Summary](#summary)

[Abstract](#abstract)

[What is FTSP](#what-is-ftsp)

[Scope Boundaries](#scope-boundaries)

[General Rules](#general-rules)

[Protocol Document Structure](#protocol-document-structure)

[FTSP List](#ftsp-list)

---

## Summary

|Field|Content|
|---|---|
|Title|FTSP|
|Type|FTSP|
|SN|0|
|Ver|1|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-22|
|PID||

## Abstract

FTSP (Freeverse Technical Standard Protocol) defines the technical standards and specifications used across the Freeverse ecosystem — cryptographic algorithms, encoding schemes, data serialization formats, transport protocols, and other technical building blocks. FTSP protocols specify **how** to implement technical operations, ensuring that independent implementations produce identical, interoperable results.

This document (FTSP0) defines the general rules shared by all FTSP protocols.

## What is FTSP

### Naming

FTSP stands for **Freeverse Technical Standard Protocol**.

- **F** - Freeverse: FTSP serves the Freeverse ecosystem.
- **TS** - Technical Standard: FTSP defines exact technical implementations that ensure interoperability.
- **P** - Protocol: Each FTSP defines a formal technical specification.

### Identification

Each FTSP protocol is identified by its serial number (`sn`) and version (`ver`). The naming convention is:

```
FTSP{sn}V{ver}_{Name}
```

For example: `FTSP11V1_Ecc256K1AesGcm256` refers to the secp256k1 ECDH + HKDF + AES-256-GCM profile, serial number 11, version 1 ([FTSP11V1_Ecc256K1AesGcm256](FTSP11V1_Ecc256K1AesGcm256.md)).

### The Need for FTSP

The existing protocol series do not cover technical implementation standards:

- **FBP** defines blockchain consensus rules (block validation, mining).
- **FEIP** defines on-chain application data formats (OP_RETURN structures).
- **FVEP** defines off-chain ecosystem concepts (what entities, IDs, time, and currency *are*).

None of these specify **how** a particular encryption algorithm works, **how** data is encoded for transport, or **how** a key exchange is performed. FTSP fills this gap by defining exact technical procedures.

### Relationship with FVEP

FVEP and FTSP are complementary:

- **FVEP** defines *what* (concepts and structures): e.g., "Entities are encrypted using a symmetric key" (see `CryptoDataByte` and `Encryptor`).
- **FTSP** defines *how* (exact implementations): e.g., "The symmetric key is derived via HKDF-SHA256 from the ECDH shared secret using secp256k1, and the plaintext is encrypted with AES-256-GCM using a 12-byte random IV" (see `Ecc256K1AesGcm256`).

|Aspect|FVEP|FTSP|
|---|---|---|
|Focus|Concepts, structures, rules|Algorithms, parameters, procedures|
|Abstraction|High-level (what)|Low-level (how)|
|Example|"Encryption uses 4 types: Symkey, Password, AsyOneWay, AsyTwoWay"|"EccK1AesGcm256: ECDH on secp256k1 → HKDF (HMAC-SHA512, see FTSP11) → AES-256-GCM (12-byte IV)"|
|Interoperability|Conceptual compatibility|Byte-level compatibility|
|Compliance|Recommended|MUST follow exactly for interoperability|

### Position in the Protocol Stack

|Layer|Protocol Series|Scope|
|---|---|---|
|Blockchain Consensus|FBP|Block validation, transaction rules, mining|
|On-chain Application|FEIP|Structured data in OP_RETURN|
|Ecosystem Foundation|FVEP|Entities, IDs, time, currency (concepts)|
|**Technical Standard**|**FTSP**|**Algorithms, encoding, transport (implementations)**|
|Business Standard|FBSP|Commercial services, marketplace rules|

## Scope Boundaries

### What Belongs in FTSP

FTSP protocols define technical implementations that require **exact, byte-level agreement** between independent implementations:

1. **Cryptographic Algorithms**: Specific encryption, signing, hashing, and key derivation procedures with all parameters defined (e.g., `EccK1AesGcm256`, `ChaCha20`, `SchnorrSignMsg`).
2. **Encoding Schemes**: Binary encoding formats, serialization rules, bundle formats (e.g., `CryptoDataByte.toBundle()` format).
3. **Transport Protocols**: Wire formats, handshake procedures, message framing for network communication.
4. **Data Format Standards**: Specific binary or text format specifications that must be followed exactly.

### What Does NOT Belong in FTSP

- **Concepts and naming** → FVEP (e.g., the concept of "EncryptType" is FVEP; the specific AES-GCM parameters are FTSP).
- **On-chain data structures** → FEIP (e.g., OP_RETURN formats).
- **Blockchain consensus rules** → FBP (e.g., block size limits).
- **Business logic and service rules** → FBSP (e.g., swap matching algorithms).

## General Rules

### 1. Exact Specification

FTSP protocols MUST define technical procedures with enough precision that two independent implementations, given the same inputs, produce identical outputs. Ambiguity in FTSP protocols leads to interoperability failures and MUST be avoided.

### 2. Test Vectors

Every FTSP protocol SHOULD include test vectors — specific input/output pairs that implementations can use to verify correctness. Test vectors MUST cover normal cases, edge cases, and error conditions.

**FC-JDK regression suite:** [FtspProtocolVectorTest.java](../../FC-JDK/src/test/java/core/crypto/FtspProtocolVectorTest.java) exercises **FTSP11–FTSP24** together (`mvn test -pl FC-JDK -Dtest=FtspProtocolVectorTest`). Individual FTSP documents link the relevant test method where vectors were pinned.

#### 2.1 Shared example keys (developer JSON samples)

FTSP11–FTSP24 include a **Developer JSON example** with wire-format JSON matching FC-JDK `CryptoDataByte.toNiceJson()` / `Signature.toNiceJson()` (fields such as `alg`, `cipher`, `iv`, `sum`, `type`, `fid`, `sign`). The same identities are reused across those samples unless the protocol states otherwise (e.g. [FTSP18](FTSP18V1_X25519.md) / [FTSP19](FTSP19V1_X25519AesGcm256.md) use **raw 32-byte X25519 scalars**, not secp256k1 FID keys).

|Role|Item|Value|
|---|---|---|
|fidA|FID|`FEk41Kqjar45fLDriztUDTUkdki7mmcjWK`|
|fidA|Compressed pubkey (hex)|`030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a`|
|fidA|Private key (WIF)|`L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8`|
|fidA|Private key (32-byte hex)|`a048f6c843f92bfe036057f7fc2bf2c27353c624cf7ad97e98ed41432f700575`|
|fidB|FID|`F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW`|
|fidB|Compressed pubkey (hex)|`02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67`|
|fidB|Private key (WIF)|`L5DDxf3PkFwi1jArqYokpTsntthLvhDYg44FXyTSgdTx3XEFR1iB`|
|fidB|Private key (32-byte hex)|`ee72e6dd4047ef7f4c9886059cbab42eaab08afe7799cbc0539269ee7e2ec30c`|
|Symkey|32-byte hex|`dc1e7c03e162397b355b6f1c895dfdf3790d98c10b920c55e91272b8eecada2a`|
|Password|UTF-8 string|`MyPassword`|

**Plaintext** for cipher and signature samples: UTF-8 **`Hello world!`**. **Fixed IVs:** 12-byte `000102030405060708090a0b` (AES-GCM, ChaCha20, X25519 AEAD); 16-byte `000102030405060708090a0b0c0d0e0f` (AES-CBC, P7).

**Regenerate** the printed JSON from FC-JDK: [FtspDeveloperExampleJsonGenerator.java](../../FC-JDK/src/test/java/core/crypto/FtspDeveloperExampleJsonGenerator.java) (run as a normal `main` with test classpath after `mvn test-compile -pl FC-JDK`).

### 3. Algorithm Identification

Each algorithm or technical procedure defined by FTSP MUST have a unique identifier (see `AlgorithmId`). This identifier is used in data structures and protocol messages to indicate which FTSP specification was used.

### 4. Security Considerations

FTSP protocols defining cryptographic operations MUST include a "Security Considerations" section that documents:
- The security assumptions.
- Known limitations.
- Recommended key sizes and parameters.
- Conditions under which the algorithm should NOT be used.

### 5. Reference Implementation

Each FTSP protocol SHOULD reference or include a reference implementation. The reference implementation in the FC-JDK is authoritative.

### 6. Versioning

FTSP protocols evolve through versioning:
- The version number is incremented for each change.
- Breaking changes to an algorithm's behavior MUST result in a **new algorithm ID** rather than a version bump, because existing encrypted data relies on the exact algorithm behavior being stable.
- Deprecated algorithms MUST remain documented and implementable for backward compatibility with existing data.

### 7. Dependencies

FTSP protocols MAY depend on external standards (e.g., NIST, SEC, IEEE). When referencing external standards, the specific version and relevant sections MUST be cited.

### 8. RFC 2119 Keywords

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in FTSP documents are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

## Protocol Document Structure

Each FTSP protocol document SHOULD follow this structure:

```
# FTSP{sn}V{ver}_{Name}

## Contents
## Summary               - Identification table (Title, SN, Ver, Category, Status, Author, PID)
## Abstract               - 2-3 sentence description
## Motivation             - Why this standard is needed
## Specification
   ### Algorithm ID       - Unique identifier for this algorithm/standard
   ### Parameters         - Constants, key sizes, block sizes, etc.
   ### Procedure          - Step-by-step technical procedure
   ### Data Format        - Input/output data structures
   ### Error Handling     - Error conditions and responses
## Test Vectors           - Input/output pairs for verification
## Security Considerations
## Versioning             - Version history table
## Related Protocols
## Reference Implementation
```

### Summary Table Fields

|Field|Description|
|---|---|
|Title|Protocol name|
|Type|Fixed: "FTSP"|
|SN|Serial number|
|Ver|Current version number|
|Category|Protocol category|
|Status|One of: Draft, Active, Deprecated, Replaced|
|Author|Author FID or name|
|Created|Creation date|
|PID|Protocol ID (txid of the on-chain publish transaction, if published)|

### Categories

|Category|Description|
|---|---|
|Encryption|Symmetric and asymmetric encryption algorithms (AES, ChaCha20, ECC+AES combinations)|
|Signing|Digital signature schemes (ECDSA, Schnorr)|
|Hashing|Hash functions and key derivation functions (SHA-256, HKDF)|
|Encoding|Data encoding and serialization formats (bundle format, Base64 conventions)|
|Transport|Network transport protocols and wire formats|
|KeyExchange|Key agreement and exchange protocols (ECDH, X25519)|

More categories can be added as needed.

## FTSP List

|SN|Name|Category|Description|
|---|---|---|---|
|0|FTSP|—|This document. Defines the general rules of FTSP.|
|11|[Ecc256K1AesGcm256](FTSP11V1_Ecc256K1AesGcm256.md)|Encryption / KeyExchange|secp256k1 ECDH, HKDF (`hkdf` info), AES-256-GCM (12-byte IV); `AlgorithmId` `EccK1AesGcm256@No1_NrC7`.|
|12|[AesGcm256](FTSP12V1_AesGcm256.md)|Encryption|Symmetric AES-256-GCM (12-byte IV, 128-bit tag); `EncryptType` Symkey; `AlgorithmId` `AesGcm256@No1_NrC7`.|
|13|[HKDF](FTSP13V1_HKDF.md)|Hashing|RFC 5869–style HKDF with HMAC-SHA512, `HashLen` 64; used by ECDH key derivation and elsewhere.|
|14|[AesCbc256](FTSP14V1_AesCbc256.md)|Encryption|AES-256-CBC, PKCS7, 16-byte IV, `sum` + `did`; `AesCbc256@No1_NrC7`.|
|15|[Ecc256K1AesCbc256](FTSP15V1_Ecc256K1AesCbc256.md)|Encryption / KeyExchange|Legacy secp256k1 ECDH (`Ecc256K1`) + SHA512 KDF + FTSP14; `EccK1AesCbc256@No1_NrC7`.|
|16|[BitCore_EccAes256](FTSP16V1_BitCore_EccAes256.md)|Encryption / KeyExchange|Standalone Bitcore: secp256k1 ECDH, SHA-512 split KDF, AES-256-CBC, HMAC-SHA256 MAC; not `CryptoDataByte` / FVEP8 `AlgorithmId`.|
|17|[EccAes256K1P7](FTSP17V1_EccAes256K1P7.md)|Encryption / KeyExchange|Legacy P7 KDF (`SHA256` chain + `symkey`), AES-256-CBC, 4-byte `sum4`; differs from FTSP14 `sum`.|
|18|[X25519](FTSP18V1_X25519.md)|KeyExchange|Curve25519 scalar mult; 32-byte keys/secrets; optional HKDF (`info` = `hkdf`).|
|19|[X25519AesGcm256](FTSP19V1_X25519AesGcm256.md)|Encryption / KeyExchange|X25519 ECDH + HKDF + FTSP12; `X25519AesGcm256@No1_NrC7`.|
|20|[ChaCha20](FTSP20V1_ChaCha20.md)|Encryption|ChaCha20 stream cipher (BC); 32-byte key, 12-byte nonce; `did` + `sum4`; `ChaCha20@No1_NrC7`.|
|21|[EccK1ChaCha20](FTSP21V1_EccK1ChaCha20.md)|Encryption / KeyExchange|secp256k1 ECDH + HKDF (`hkdf-chacha20` info) + FTSP20; `EccK1ChaCha20@No1_NrC7`.|
|22|[BTC_EcdsaSignMsg](FTSP22V1_BTC_EcdsaSignMsg.md)|Signing|Bitcoin message ECDSA (`ECKey.signMessage`); `BTC_EcdsaSignMsg@No1_NrC7`; see FVEP7.|
|23|[Sha256SymSignMsg](FTSP23V1_Sha256SymSignMsg.md)|Signing|Double-SHA256 over `msg ‖ symkey`; `FC_Sha256SymSignMsg@No1_NrC7`; see FVEP7.|
|24|[SchnorrSignMsg](FTSP24V1_SchnorrSignMsg.md)|Signing|BIP340-style Schnorr over `sha256x2(msg)`; `FC_SchnorrSignMsg@No1_NrC7`; see FVEP7.|
|25|[PasswordToSymkey](FTSP25V1_PasswordToSymkey.md)|Hashing / KeyDerivation|SHA256-based password-to-symkey KDF: `symkey = SHA256(SHA256(password) ‖ iv)`; used by `EncryptType.Password` before dispatching to FTSP12/14/20.|
