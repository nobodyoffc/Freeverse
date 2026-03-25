# FVEP0V1_FVEP

## Contents

[Summary](#summary)

[Abstract](#abstract)

[What is FVEP](#what-is-fvep)

[Terms](#terms)

[General Rules](#general-rules)

[Protocol Document Structure](#protocol-document-structure)

[FVEP List](#fvep-list)

---

## Summary

|Field|Content|
|---|---|
|Title|FVEP|
|Type|FVEP|
|SN|0|
|Ver|1|
|Status|Active|
|Author|C_armX, No1_NrC7|
|Created|2026-03-21|
|PID||

## Abstract

FVEP (Freeverse Ecosystem Protocol) is a set of protocols for constructing the Freeverse ecosystem. Unlike FEIP, FVEP protocols do **not** write data on-chain and will **not** lead to a hard fork of the Freecash main network. Instead, FVEP defines off-chain standards for entities, APIs, services, data formats, and coordination rules that operate alongside on-chain data.

FVEP protocols are strongly recommended for adoption across all participants in the Freeverse ecosystem to ensure interoperability, openness, and decentralization.

This document (FVEP0) defines the general rules shared by all FVEP protocols.

## What is FVEP

### Naming

FVEP stands for **Freeverse Ecosystem Protocol**.

- **FV** - Freeverse: FVEP serves the Freeverse ecosystem built around the Freecash blockchain.
- **E** - Ecosystem: FVEP focuses on the off-chain ecosystem rather than on-chain data.
- **P** - Protocol: Each FVEP defines a formal specification that implementations SHOULD follow to ensure interoperability.

### Identification

Each FVEP protocol is identified by its serial number (`sn`) and version (`ver`). The naming convention is:

```
FVEP{sn}V{ver}_{Name}
```

For example: `FVEP1V1_Entity` refers to the Entity protocol, serial number 1, version 1.

### Relationship with FEIP

FVEP complements FEIP. While FEIP defines on-chain data formats and consensus rules that are enforced by parsers, FVEP defines off-chain conventions and standards that are adopted voluntarily by ecosystem participants.

|Aspect|FEIP|FVEP|
|---|---|---|
|Data Location|On-chain (OP_RETURN)|Off-chain|
|Enforcement|Parser consensus|Voluntary adoption|
|Hard Fork Risk|Possible|None|
|Scope|Blockchain data protocols|Ecosystem standards|

### Design Principles

FVEP protocols are guided by the following principles:

1. **Open**: All FVEP specifications are publicly available. Anyone can implement, extend, or build upon them without permission.

2. **Decentralized**: FVEP protocols MUST NOT introduce central points of control or single points of failure. Ecosystem participants operate independently and coordinate through shared standards rather than central authorities.

3. **Interoperable**: FVEP protocols define common interfaces and data formats so that independent implementations can work together seamlessly.

4. **Blockchain-Derived**: While FVEP data is not stored on-chain, FVEP protocols typically derive from, reference, or operate alongside on-chain FEIP data. The blockchain remains the ultimate source of truth for identity, ownership, and authorization.

5. **Non-Invasive**: FVEP protocols MUST NOT require changes to the Freecash blockchain consensus rules. They operate entirely at the application and service layer.

## Terms

The following terms and abbreviations are used throughout the Freeverse ecosystem — in protocols, APIs, code, and documentation.

### Cryptography

|Term|Full Name|Description|
|---|---|---|
|prikey|Private Key|The secret key of an asymmetric key pair. Used for signing and decryption.|
|pubkey|Public Key|The public key of an asymmetric key pair. Used for verification and encryption.|
|symkey|Symmetric Key|A shared secret key used for symmetric encryption and decryption (e.g., AES).|
|multisig|Multiple Signature|A scheme requiring multiple private keys to authorize an operation.|
|keyName|Key Name|The first 6 bytes of `SHA-256(symkey)`, hex-encoded in JSON, used to identify a symmetric key without revealing it (see FVEP7, FVEP8).|

### Signature

|Term|Full Name|Description|
|---|---|---|
|SymSign|Symmetric Sign|Signing with a shared symkey; payloads use `keyName` instead of `fid` (see FVEP7).|
|AsySign|Asymmetric Sign|Signing with a prikey; payloads use the signer's `fid` (see FVEP7).|

### Encryption types

|Term|Full Name|Description|
|---|---|---|
|EncryptType|Encryption Type|How the content encryption key is established: Symkey, Password, AsyOneWay, or AsyTwoWay (see FVEP8).|
|AsyOneWay|Asymmetric One-Way Encryption|Encrypt to the recipient's pubkey using an ephemeral key pair; only the recipient's prikey decrypts (see FVEP8).|
|AsyTwoWay|Asymmetric Two-Way Encryption|Encrypt with sender prikey and recipient pubkey; decryption uses the paired keys (see FVEP8).|

### Identity

|Term|Full Name|Description|
|---|---|---|
|EID|Entity ID|The general identifier of any entity in Freeverse (see FVEP2).|
|FID|Freecash ID|The identifier of a Subject, derived from its pubkey (see FVEP2).|
|OID|Object ID|The general identifier of an Object (see FVEP2).|
|DID|Data ID|A content-hash-based identifier of an Object (see FVEP2).|
|CID|Crypto ID|A human-readable subjectId (human-readable FID, via FEIP3).|
|NID|Named ID|A human-readable OID (via FEIP11).|
|AID|App ID|The identifier of an App entity.|
|BID|Box ID|The identifier of a Box entity.|
|IID|Internal ID|An identifier used internally within a system or service.|
|PID|Protocol ID|The identifier of a Protocol entity.|
|SID|Service ID|The identifier of a Service entity.|
|TID|Team ID|The identifier of a Team entity.|
|Typed ID|Typed ID|A notation `(type)value` to disambiguate txid-based OIDs (see FVEP2).|

### Blockchain

|Term|Full Name|Description|
|---|---|---|
|FCH|Freecash|The native currency of the Freecash blockchain. Abbreviation: F. 1 F = 1,000,000 c = 100,000,000 s (see FVEP5).|
|Cash|Cash|The middle unit of Freecash. Abbreviation: c. 1 c = 100 s (see FVEP5).|
|Satoshi|Satoshi|The smallest, indivisible unit of Freecash. Abbreviation: s (see FVEP5).|
|txid|Transaction ID|The hash identifier of a blockchain transaction.|
|UTXO|Unspent Transaction Output|An unspent output of a transaction, also called "Cash" in Freeverse.|
|CD|CoinDay|The accumulated value-time product of a UTXO (unspent Cash), using currentTime. Unit: cd (see FVEP6).|
|CDD|CoinDay Destroyed|The value-time product of a STXO (spent Cash), frozen at spendTime. Unit: cd. Transaction CDD is the sum of all input CDDs (see FVEP6).|
|cd|cd|The unit of CD and CDD. 1 cd = 1 FCH held for 1 day (see FVEP6).|

### Protocol

|Term|Full Name|Description|
|---|---|---|
|FBP|Freecash Blockchain Protocol|Blockchain consensus rules (block validation, mining, transaction rules).|
|FEIP|Freecash Extension Improvement Protocol|On-chain application protocols written in OP_RETURN.|
|FVEP|Freeverse Ecosystem Protocol|Off-chain ecosystem standards defined in this protocol series.|
|FTSP|Freeverse Technical Standard Protocol|Technical standards for algorithms, encoding, and transport protocols.|
|FBSP|Freeverse Business Standard Protocol|Business standards for commercial services and marketplace rules.|
|ver|Version|The version of an entity.|
|SN|Serial Number|The serial number that identifies a protocol within its series.|

### Entity

|Term|Full Name|Description|
|---|---|---|
|Subject|Subject|An entity that possesses a prikey and pubkey (defined in FVEP1).|
|Object|Object|An entity that can be represented as a byte array (defined in FVEP1).|

### Location

|Term|Full Name|Description|
|---|---|---|
|Slash|Slash (Path Operator)|The `/` operator in location expressions. Expresses parent→child containment (see FVEP3).|
|At|At (Within Operator)|The `@` operator in location expressions. Expresses child→parent containment (see FVEP3).|
|Sharp|Sharp (Part Indicator)|The `#` operator in location expressions. Refers to a data fragment within an entity (see FVEP3).|
|Escape|Escape|The `\` operator in location expressions. Makes the next character literal (see FVEP3).|

### Time

|Term|Full Name|Description|
|---|---|---|
|FcDate|Freeverse Date|A human-readable time representation in the format `Y.D.H.M` based on block height (see FVEP4).|
|Freeverse Minute|Freeverse Minute|The base time unit in Freeverse. 1 Freeverse Minute = 1 block.|
|Freeverse Hour|Freeverse Hour|60 Freeverse Minutes = 60 blocks.|
|Freeverse Day|Freeverse Day|24 Freeverse Hours = 1,440 blocks.|
|Freeverse Year|Freeverse Year|400 Freeverse Days = 576,000 blocks.|

## General Rules

### 1. No On-Chain Data

FVEP protocols MUST NOT define data to be written on-chain. Any protocol that requires on-chain data storage belongs to the FEIP series.

### 2. No Consensus Requirement

Unlike FEIP, FVEP protocols do not require strict consensus among all participants. Different implementations MAY vary in details as long as they conform to the defined interfaces and data formats. However, conformance to FVEP specifications is strongly recommended.

### 3. Versioning

FVEP protocols evolve through versioning. When a protocol is updated:
- The version number is incremented.
- The previous version SHOULD remain supported for a reasonable transition period.
- Breaking changes MUST result in a new major version.

### 4. Identity and Authorization

Where FVEP protocols require identity or authorization, they SHOULD use Freecash addresses (FIDs) and cryptographic signatures consistent with the Freecash blockchain. This ensures that the decentralized identity layer provided by FEIP is reused rather than duplicated.

### 5. Data Formats

FVEP protocols SHOULD use JSON as the default data interchange format. Other formats MAY be used when justified by specific requirements (e.g., binary protocols for performance-critical communication).

### 6. Transport

FVEP protocols do not prescribe a specific transport layer. Implementations MAY use HTTP, WebSocket, UDP, TCP, or any other transport mechanism appropriate for the use case. The protocol document SHOULD specify the recommended transport.

### 7. RFC 2119 Keywords

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in FVEP documents are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

## Protocol Document Structure

Each FVEP protocol document SHOULD follow this structure:

```
# FVEP{sn}V{ver}_{Name}

## Contents
## Summary           - Identification table (Title, SN, Ver, Category, Status, Author, PID)
## Abstract           - 2-3 sentence description
## Motivation         - Why this protocol exists
## Specification
   ### Definitions    - Key terms and concepts
   ### Data Formats   - JSON schemas and field descriptions
   ### Interfaces     - API endpoints, methods, or interaction patterns
   ### Rules          - Numbered rules and constraints
## Examples           - Complete end-to-end examples
## Versioning         - Version history table
## Related Protocols
## Reference Implementation
```

### Summary Table Fields

|Field|Description|
|---|---|
|Title|Protocol name|
|Type|Fixed: "FVEP"|
|SN|Serial number|
|Ver|Current version number|
|Category|To be classified into a certain category|
|Status|One of: Draft, Active, Deprecated, Replaced|
|Author|Author FID or name|
|Created|Creation date|
|PID|Protocol ID (txid of the on-chain publish transaction, if published)|

### Categories

|Category|Description|
|---|---|
|Foundation|Core ecosystem definitions and standards (entity models, naming, data formats)|
|Service|Protocols for service discovery, registration, and interaction|
|API|API specifications and standards for ecosystem services|
|Data|Data storage, exchange, and synchronization standards|
|Communication|Protocols for messaging, notification, and real-time communication|

More categories can be added as the ecosystem evolves.

## FVEP List

|SN|Name|Category|Description|
|---|---|---|---|
|0|FVEP|Foundation|This document. Defines the general rules of FVEP.|
|1|Entity|Foundation|Define the entities in the Freeverse ecosystem|
|2|ID|Foundation|Define the identifiers of the entities|
|3|Location|Foundation|Define the location or path of the entities|
|4|Time|Foundation|Define the Freeverse time system based on block height|
|5|Currency|Foundation|Define the currency of Freeverse and its units|
|6|CoinDay|Foundation|Define CoinDay (CD) and CoinDay Destroyed (CDD)|
|7|Signature|Foundation|Define the structure and rules of digital signatures and verification|
|8|Encryption|Foundation|Define the structure and rules of encryption and decryption|
