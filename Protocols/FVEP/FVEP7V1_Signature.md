# FVEP7V1_Signature

## Contents

[Summary](#summary)

[Abstract](#abstract)

[Motivation](#motivation)

[Specification](#specification)

[Examples](#examples)

[Versioning](#versioning)

[Related Protocols](#related-protocols)

[Reference Implementation](#reference-implementation)

---

## Summary

|Field|Content|
|---|---|
|Title|Signature|
|Type|FVEP|
|SN|7|
|Ver|1|
|Category|Foundation|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-22|
|PID||

## Abstract

This protocol defines the general structure and rules for **digital signatures** and **verification** in the Freeverse ecosystem: how a signed message is represented (JSON and optional binary bundle), how the signer is identified (FID for asymmetric schemes, `keyName` for symmetric schemes), and how verification relates the signature to the message and the claimed identity. Concrete algorithms (ECDSA message format, Schnorr steps, SHA-256 symmetric digest) are specified in **FTSP**; this document defines the **carrier format** and **processing rules** common to all signature algorithms registered under `AlgorithmId`.

## Motivation

APIs, clients, and services need a single, interoperable way to attach and verify signatures on messages (e.g. HTTP bodies, JSON payloads). FVEP7 separates:

- **Structure** (this protocol): fields, encodings, bundle layout, SymSign vs AsySign.
- **Algorithms** (FTSP): exact signing and verification math and byte rules per `AlgorithmId`.

## Specification

### Definitions

|Term|Description|
|---|---|
|**SymSign**|Symmetric signing: the verifier holds the same secret key (symkey) as the signer. The public artifact uses **`keyName`** — the first 6 bytes of `SHA-256(symkey)`, hex-encoded in JSON — to hint which key was used without revealing the key.|
|**AsySign**|Asymmetric signing: the signer uses a **prikey**; verifiers use the signer's **FID** (hash160-based Freecash address) and the algorithm to recover or check the signature. Applies to ECDSA-on-message and Schnorr message schemes in the reference stack.|
|**msg**|The signed payload, carried as a **string** in JSON. Implementations SHOULD treat the message as **UTF-8** text when converting to bytes for signing and verification unless a specific algorithm profile in FTSP defines otherwise.|
|**sign**|Algorithm-dependent signature material. In JSON: **ECDSA** (Bitcoin message signing style) is typically **Base64**; **Schnorr** (reference) is **Base64** over `pubkey (33 bytes) \|\| schnorr_signature`; **FC_Sha256SymSignMsg** is **hex** of `SHA-256( SHA-256( msgBytes \|\| symkey ) )` (see FTSP / reference code).|
|**alg**|Identifies the signing profile. Serialized in JSON using the `AlgorithmId` **display name** string (e.g. `Sha256SymSignMsg@No1_NrC7`, `BTC-EcdsaSignMsg@No1_NrC7`, `SchnorrSignMsg@No1_NrC7`).|
|**Bundle**|Optional compact binary encoding of the same logical content as JSON (see below).|

### Signature algorithm ID bytes (bundle prefix)

The first **6 bytes** of a signature bundle identify the signing algorithm. Leading bytes are zero; the last byte is:

|Last byte (decimal)|Typical `AlgorithmId` (reference)|
|---|---|
|3|`FC_Sha256SymSignMsg_No1_NrC7`|
|4|`BTC_EcdsaSignMsg_No1_NrC7`|
|5|`FC_SchnorrSignMsg_No1_NrC7`|

New algorithms MUST allocate a new 6-byte prefix in FTSP and register it consistently in implementations.

### Data formats — JSON (canonical interchange)

The canonical signed-object shape uses these **logical** fields (reference uses a compact `ShortSign` projection):

|Field|Required|SymSign|AsySign|Description|
|---|---|---|---|---|
|`alg`|Y|Y|Y|Algorithm id (display name string).|
|`msg`|Y|Y|Y|Signed message.|
|`sign`|Y|Y|Y|Signature string (encoding per algorithm).|
|`keyName`|Y|Y (hex, 12 hex chars = 6 bytes)|Omit|Symmetric key fingerprint.|
|`fid`|Y|Omit|Y|Signer FID (Freecash address).|

**Rules:**

1. For **SymSign**, `keyName` MUST be present; `fid` MUST NOT be used as the primary signer id.
2. For **AsySign**, `fid` MUST be present; `keyName` is omitted in the compact JSON form (it may exist internally for debugging in some implementations).
3. Legacy JSON MAY duplicate fields (`address`/`fid`, `message`/`msg`, `signature`/`sign`, `algorithm`/`alg`); parsers SHOULD normalize via a single `makeSignature()`-style merge.

**Legacy string format (deprecated):** `msg----fid----sign` (triple-separated). If parsed, it SHOULD be treated as **AsySign** with `BTC_EcdsaSignMsg_No1_NrC7`.

### Data formats — binary bundle

Layout (big-endian length where noted):

```
algBytes[6]
  + (if alg = symmetric / byte 3) keyName[6]
  + (if alg = ECDSA or Schnorr / bytes 4 or 5) fidHash160[20]
  + signLength[2]   // big-endian uint16, length of signBytes
  + signBytes[]     // raw signature bytes (not Base64)
  + msgBytes[]      // remainder of bundle; typically UTF-8 bytes of msg
```

**Rules:**

4. `msg` and `sign` MUST be non-null to produce a bundle; implementation prepares `signBytes` by decoding Base64 when the JSON form used Base64.
5. For symmetric algorithm (byte 3), **keyName** (6 raw bytes) MUST follow `algBytes`.
6. For asymmetric algorithms (bytes 4 and 5), **FID** MUST be encoded as **20-byte hash160** (not Base58).
7. **Schnorr** uses the same FID layout as ECDSA in the bundle (byte 5 + 20-byte hash160).

### Signing and verification workflow

1. **Inputs:** message `msg`, secret `key` (symkey bytes or prikey bytes), `alg`.
2. **Sign:** Compute `sign` per FTSP for `alg`; set `keyName` or `fid` per SymSign / AsySign; emit JSON and/or bundle.
3. **Verify:**
   - **SymSign:** Recompute digest from `msg` and verifier's symkey; compare to `sign` (hex); optionally confirm `keyName` matches first 6 bytes of `SHA-256(symkey)`.
   - **AsySign (ECDSA):** Recover pubkey from `msg` and `sign`, map to FID, compare to `fid`.
   - **AsySign (Schnorr):** Decode Base64 `sign`, extract embedded pubkey, check FID matches, verify Schnorr per FTSP.

4. If `alg` is missing in a legacy object, verifiers MAY default to `BTC_EcdsaSignMsg_No1_NrC7` (reference behavior); new payloads MUST always include `alg`.

### Rules (normative summary)

|#|Rule|
|---|---|
|1|JSON is the default interchange format for signatures.|
|2|SymSign payloads MUST include `keyName`; AsySign MUST include `fid`.|
|3|`alg` MUST identify the algorithm; byte-level signing and verification details belong in FTSP.|
|4|Bundle `algBytes` MUST match the algorithm used for `sign` and `msgBytes`.|
|5|Implementations MUST NOT expose raw symkey or prikey in serialized JSON.|
|6|The key words "MUST", "SHOULD", etc. are interpreted per [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).|

### Relationship to FTSP

FVEP7 does **not** define curve parameters, ECDSA encoding, or Schnorr equations. Those are **FTSP** documents keyed by `AlgorithmId`. FVEP7 defines the **envelope**: fields, JSON shape, bundle layout, SymSign vs AsySign.

## Examples

### Example 1 — SymSign JSON (conceptual)

```json
{
  "alg": "Sha256SymSignMsg@No1_NrC7",
  "keyName": "a1b2c3d4e5f6",
  "msg": "{\"op\":\"ping\"}",
  "sign": "<64-hex-chars SHA256x2(msg||key)>"
}
```

### Example 2 — AsySign JSON (ECDSA, conceptual)

```json
{
  "alg": "BTC-EcdsaSignMsg@No1_NrC7",
  "fid": "FEk41MvNLA85EqYuSWpkXUrgs9UGkdhmDLF",
  "msg": "hello",
  "sign": "<Base64 bitcoin-message signature>"
}
```

### Example 3 — Bundle layout sizes

- Symmetric: `6 + 6 + 2 + len(sign) + len(msg)`.
- Asymmetric (ECDSA or Schnorr): `6 + 20 + 2 + len(sign) + len(msg)`.

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-03-22|C_armX, No1_NrC7|Initial draft.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FVEP0|General FVEP rules.|
|FVEP1|Subject / Object; signing keys belong to Subjects.|
|FVEP2|FID as subjectId; typed identities.|
|FTSP|Per-algorithm signature specifications (`AlgorithmId`).|

## Reference Implementation

|Component|Location|
|---|---|
|`Signature`| [FC-JDK/src/main/java/data/fcData/Signature.java](../../FC-JDK/src/main/java/data/fcData/Signature.java) |
|Android port| `FC-AJDK/.../data/fcData/Signature.java` |
