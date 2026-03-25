# FEIP7V4_Mail

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
|Title|Mail|
|Type|FEIP|
|SN|7|
|Version|4|
|Category|Personal|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-22|
|PID||

General consensus of FEIP: [FEIP0V1_FEIP](FEIP0V1_FEIP.md)

## Abstract

The Mail protocol carries **encrypted messages** from a sender FID to a recipient FID on-chain. For **version 4**, the `cipher` field MUST use **FVEP8 `AsyTwoWay`** encryption only. **Sender** and **recipient** FIDs are derived from the enclosing transaction as specified below; for **send**, they MUST be **distinct** (no self-mail). Operations **send**, **delete**, and **recover** manage indexed mail entities. Legacy cipher handling in clients is out of scope for conforming parsers.

## Motivation

- **End-to-end confidentiality** between two Subjects without sharing a long-term symmetric key on-chain.
- **Clear crypto profile**: Restricting to **AsyTwoWay** removes ambiguity and aligns one mail format with [FVEP8](../FVEP/FVEP8V1_Encryption.md).
- **Accounting**: `noticeFee` / paid amount from the transaction context can be stored for service economics ([FEIP10](FEIP10V1_NoticeFee.md) ecosystem).

## Specification

### Ciphertext profile (normative)

1. `cipher` MUST be a **UTF-8 JSON** string parseable as a **FVEP8** crypto object (`CryptoDataStr` / equivalent).
2. The JSON field **`type`** MUST be exactly **`AsyTwoWay`** (the `EncryptType` name used in FVEP8 and reference serializers).
3. Ciphers with `type` **`Symkey`**, **`Password`**, or **`AsyOneWay`**, or non-JSON legacy encodings (e.g. raw Base64 Bitcore blobs without the AsyTwoWay JSON envelope), MUST NOT be accepted as **valid FEIP7V4** mail. Conforming parsers SHOULD reject such `send` operations (and SHOULD NOT index them as v4 mail).

Concrete symmetric/ECDH algorithms inside the AsyTwoWay object are defined in **FTSP** / `AlgorithmId`; the outer envelope rules are in **FVEP8**.

### Sender and recipient (normative)

For any Mail transaction (send, delete, or recover):

1. **`from` (sender FID)** — the **address** of the **first input** (`vin[0]`) of the transaction (the FID that owns that input’s prevout).
2. **`to` (recipient FID)** — the **address** of the **first output** that is **not** an OP_RETURN output: scan transaction outputs in **ascending index order**, skip any output whose script is OP_RETURN (or equivalent “data-only” burn), and take the **first** remaining output’s **payment address** as FID.
3. For **send**, **`from`** MUST NOT equal **`to`**. The recipient cannot be the sender (no self-mail). If the first non-OP_RETURN output pays to the same FID as the first input, conforming parsers SHOULD **reject** the **send** (and SHOULD NOT index it as v4 mail).

For a **send**, the indexed mail’s `from` and `to` MUST be set from these rules on **that** transaction. The OP_RETURN payload MUST still be signed/valid per FEIP0; implementations typically infer the signing key from the first input.

If no non-OP_RETURN output exists, **`to`** is undefined — conforming parsers SHOULD **reject** the **send** (and SHOULD NOT index it as v4 mail).

### Plaintext payload

After **AsyTwoWay** decryption, the cleartext bytes are interpreted as the **message body** (typically **UTF-8** text). The protocol does not require an inner JSON wrapper; clients MAY use structured payloads by convention.

### Mail entity (indexed)

Fields set by the reference parser for **send** (and carried on the document):

|Field|Source|Description|
|---|---|---|
|`id`|Send txid|Unique mail record id.|
|`from`|First-input FID|Sender: address of **first input** of the send tx.|
|`to`|First non–OP_RETURN output FID|Recipient: address of **first non-OP_RETURN output** of the send tx; MUST differ from `from`.|
|`alg`|Optional|`AlgorithmId` display name hint for decryption.|
|`cipher`|String|FVEP8 **AsyTwoWay** JSON ciphertext.|
|`birthTime`|Block time|Send time.|
|`birthHeight`|Height|Block of send.|
|`lastHeight`|Height|Updated on delete/recover.|
|`noticeFee`|Long|Paid amount associated with the operation (`opre.getPaid()` in reference).|
|`active`|Boolean|`true` unless **delete**; **recover** sets `true`.|

### Decryption roles (AsyTwoWay)

Per FVEP8 and [Mail.decryptMail](../../FC-JDK/src/main/java/data/feipData/Mail.java):

- **Recipient** (`myFid == to`): supply **prikey** as **`prikeyB`** / recipient side; **`pubkeyA`** comes from ciphertext.
- **Sender** (`myFid == from`): supply **prikeyA**; obtain **`pubkeyB`** for `to` (reference uses API `getPubkey`).

Either party can decrypt the same body when the AsyTwoWay object was built with **from**’s key pair and **to**’s public key as required by the chosen FTSP profile.

### `data` field — operations

Lowercase `op` strings: `send`, `delete`, `recover`.

#### 1. send

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"send"`|
|`cipher`|Y|String|FVEP8 JSON with **`type": "AsyTwoWay"`**.|
|`alg`|N|String|Optional algorithm display name.|

**Consensus rules**

1. `cipher` MUST satisfy the [Ciphertext profile](#ciphertext-profile-normative) above.
2. `id` MUST be the send transaction id.
3. `from` MUST be the FID from the **first input** of the send transaction; `to` MUST be the FID from the **first non-OP_RETURN output** of the same transaction ([Sender and recipient](#sender-and-recipient-normative)).
4. `from` MUST NOT equal `to` (recipient MUST NOT be the sender).
5. `active` MUST be `true`.
6. `birthTime`, `birthHeight`, `lastHeight`, `noticeFee` MUST follow block/tx context (reference sets `noticeFee` from paid amount).

#### 2. delete

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"delete"`|
|`mailIds`|Y|Array of strings|Mail `id` values to deactivate.|

**Consensus rules**

7. Let **auth** be the FID of the **first input** of the delete/recover transaction. Only mails where **`auth` equals `to`** MAY be modified; if the stored mail has **`to` == null**, then **`auth` MUST equal `from`** (reference iterator logic).
8. Set `active` to `false` and update `lastHeight`.

#### 3. recover

|Field|Required|Type|Description|
|---|---|---|---|
|`op`|Y|String|`"recover"`|
|`mailIds`|Y|Array of strings|Mail ids to reactivate.|

**Consensus rules**

9. Same authorization as **delete**, but set `active` to `true`.

### OP_RETURN envelope

```json
{
  "type": "FEIP",
  "sn": "7",
  "ver": "4",
  "name": "Mail",
  "data": { }
}
```

### Transaction requirements

- **Send:** MUST have a resolvable **`from`** (first input) and **`to`** (first non-OP_RETURN output) per [Sender and recipient](#sender-and-recipient-normative), with **`from` ≠ `to`**. The Mail OP_RETURN is normally among the outputs of the same tx.
- General FEIP rules ([FEIP0](FEIP0V1_FEIP.md)): SIGHASH, OP_RETURN size, CDD, etc.

### Parsing order and reorg

- Strict block order per FEIP0.

## Examples

### Example 1 — send (envelope + AsyTwoWay cipher shape)

```json
{
  "type": "FEIP",
  "sn": "7",
  "ver": "4",
  "name": "Mail",
  "data": {
    "op": "send",
    "alg": "EccK1AesGcm256@No1_NrC7",
    "cipher": "{\"type\":\"AsyTwoWay\",\"alg\":\"EccK1AesGcm256@No1_NrC7\",\"iv\":\"...\",\"pubkeyA\":\"...\",\"pubkeyB\":\"...\",\"cipher\":\"...\"}"
  }
}
```

The `cipher` value is a **string containing JSON** (escaped in OP_RETURN); implementations serialize per their JSON stack. Required top-level keys inside that JSON include at least those mandated by FVEP8 for **AsyTwoWay** (`type`, `alg`, `iv`, inner `cipher`, and the asymmetric key material as specified in FVEP8/FTSP).

### Example 2 — delete

```json
{
  "type": "FEIP",
  "sn": "7",
  "ver": "4",
  "name": "Mail",
  "data": {
    "op": "delete",
    "mailIds": [
      "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
    ]
  }
}
```

### Example 3 — recover

Same as delete with `"op": "recover"`.

## Versioning

|Version|Date|Summary|
|---|---|---|
|4|2026-03-22|**AsyTwoWay-only** ciphertext normative; aligned with `Feip.MAIL` (`7`/`4`) and parser “Version 4” branch.|
|3|—|Earlier behaviour (broader cipher acceptance in clients).|
|…|—|Earlier versions not documented here.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|FEIP0|OP_RETURN, parsing, CDD.|
|FEIP10|Notice fee context for paid delivery.|
|FVEP8|`AsyTwoWay`, JSON/bundle field semantics.|
|FTSP|ECDH + AEAD parameters for `AlgorithmId`.|

## Reference Implementation

|Component|Location|
|---|---|
|`Mail`| [FC-JDK/src/main/java/data/feipData/Mail.java](../../FC-JDK/src/main/java/data/feipData/Mail.java) |
|`MailOpData`| [FC-JDK/src/main/java/data/feipData/MailOpData.java](../../FC-JDK/src/main/java/data/feipData/MailOpData.java) |
|`PersonalParser.parseMail`| [FEIP/FeipParser/src/main/java/personal/PersonalParser.java](../../FEIP/FeipParser/src/main/java/personal/PersonalParser.java) |
|`FeipProtocol.MAIL`| [FC-JDK/src/main/java/data/feipData/Feip.java](../../FC-JDK/src/main/java/data/feipData/Feip.java) |

### Implementation notes (non-normative)

- **`Mail.decryptMail`** still branches on legacy `cipher` shapes (JSON vs `A`… Base64, Bitcore, etc.). **FEIP7V4** conformance for new mail is **AsyTwoWay JSON only**; tightening the parser to reject other forms is recommended.
- **`MailOpData.OP_FIELDS`** references `cipherSend` / `cipherReci`; **`makeSend`** does not set them — likely legacy.
- **`parseMail` send** does not fail when `cipher` is null; conforming implementations SHOULD require `cipher` for **send**.
