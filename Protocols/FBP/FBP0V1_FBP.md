# FBP0V1_FBP

## Contents

[Summary](#summary)

[Abstract](#abstract)

[What is FBP](#what-is-fbp)

[General Rules](#general-rules)

[Protocol Document Structure](#protocol-document-structure)

[FBP List](#fbp-list)

---

## Summary

|Field|Content|
|---|---|
|Title|FBP|
|Type|FBP|
|SN|0|
|Ver|1|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-22|
|PID||

## Abstract

FBP (Freecash Blockchain Protocol) defines the consensus rules of the Freecash blockchain itself — the rules that all nodes must follow to validate blocks and transactions. FBP protocols operate at the lowest layer of the Freeverse ecosystem: the blockchain consensus layer. Changes to FBP protocols may result in hard forks or soft forks of the Freecash network.

This document (FBP0) defines the general rules shared by all FBP protocols.

## What is FBP

### Naming

FBP stands for **Freecash Blockchain Protocol**.

- **F** - Freecash: FBP defines the rules of the Freecash blockchain.
- **B** - Blockchain: FBP operates at the blockchain consensus layer.
- **P** - Protocol: Each FBP defines a formal specification that all nodes MUST follow.

### Identification

Each FBP protocol is identified by its serial number (`sn`) and version (`ver`). The naming convention is:

```
FBP{sn}V{ver}_{Name}
```

For example: `FBP1V1_Block` refers to the Block protocol, serial number 1, version 1.

### Position in the Protocol Stack

FBP is the foundation layer of the Freeverse protocol stack:

|Layer|Protocol Series|Scope|
|---|---|---|
|Blockchain Consensus|**FBP**|Block validation, transaction rules, mining, network parameters|
|On-chain Application|FEIP|Structured data in OP_RETURN (identity, publishing, tokens, etc.)|
|Ecosystem Foundation|FVEP|Off-chain concepts (entities, IDs, time, currency, etc.)|
|Technical Standard|FTSP|Cryptographic algorithms, encoding, transport protocols|
|Business Standard|FBSP|Commercial services, marketplace rules, swap protocols|

### Relationship with Other Protocol Series

|Aspect|FBP|FEIP|FVEP|
|---|---|---|---|
|Layer|Blockchain consensus|On-chain application|Off-chain ecosystem|
|Enforcement|All nodes enforce|Parsers enforce|Voluntary adoption|
|Hard Fork Risk|Yes|Possible|None|
|Scope|Block/transaction rules|OP_RETURN data formats|Ecosystem standards|
|Change Frequency|Very rare|Moderate|Moderate|

### Design Principles

1. **Stability**: FBP changes are rare and deliberate. The blockchain consensus rules form the foundation of trust for the entire ecosystem. Unnecessary changes risk network splits and loss of confidence.

2. **Backward Compatibility**: FBP changes SHOULD be backward-compatible (soft forks) whenever possible. Hard forks MUST only be introduced when absolutely necessary and with broad community consensus.

3. **Simplicity**: FBP rules SHOULD be as simple as possible. Complexity in consensus rules increases the risk of implementation bugs and network splits.

4. **Decentralization**: FBP rules MUST NOT favor any particular participant, miner, or group. The rules apply equally to all nodes.

5. **Security**: FBP protocols MUST prioritize network security. Changes that weaken the security model require extraordinary justification.

## General Rules

### 1. Consensus Enforcement

All FBP protocols define rules that every full node MUST enforce. Blocks or transactions violating FBP rules MUST be rejected. There is no tolerance for non-compliance — consensus rules are absolute.

### 2. Fork Management

FBP changes that are not backward-compatible constitute a **hard fork** and MUST be activated at a specific block height agreed upon by the community. Backward-compatible changes (soft forks) SHOULD also use block height activation.

### 3. Versioning

FBP protocols evolve through versioning:
- The version number is incremented for each change.
- All previous versions remain documented for historical reference.
- Active nodes MUST implement the latest version of each FBP protocol.

### 4. Reference Client

The Freecash reference client implementation is the authoritative interpretation of FBP protocols. In case of ambiguity between the protocol document and the reference client, the reference client behavior prevails until the protocol document is updated.

### 5. Activation

FBP changes MUST specify:
- The activation block height.
- The minimum node version required.
- A transition period for nodes to upgrade.

### 6. RFC 2119 Keywords

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in FBP documents are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

## Protocol Document Structure

Each FBP protocol document SHOULD follow this structure:

```
# FBP{sn}V{ver}_{Name}

## Contents
## Summary           - Identification table (Title, SN, Ver, Category, Status, Author, PID)
## Abstract           - 2-3 sentence description
## Motivation         - Why this change is needed
## Specification
   ### Parameters     - Network parameters and constants
   ### Validation     - Validation rules
   ### Activation     - Block height and transition plan
   ### Rules          - Numbered consensus rules
## Examples           - Illustrative examples
## Versioning         - Version history table
## Related Protocols
## Reference Implementation
```

### Summary Table Fields

|Field|Description|
|---|---|
|Title|Protocol name|
|Type|Fixed: "FBP"|
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
|Foundation|Core blockchain rules (block structure, transaction format, consensus algorithm)|
|Mining|Mining rules (difficulty adjustment, block reward, halving)|
|Transaction|Transaction validation rules (script opcodes, size limits, fee policies)|
|Network|Network layer rules (peer discovery, message format, propagation)|

More categories can be added as needed.

## FBP List

|SN|Name|Category|Description|
|---|---|---|---|
|0|FBP|Foundation|This document. Defines the general rules of FBP.|
