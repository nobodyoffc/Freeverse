# FEIP0V1_FEIP

## Contents

[Summary](#summary)

[Abstract](#abstract)

[What is FEIP](#what-is-feip)

[On-chain Data Format](#on-chain-data-format)

[General Rules](#general-rules)

[Protocol Document Structure](#protocol-document-structure)

[FEIP List](#feip-list)

---

## Summary

|Field|Content|
|---|---|
|Title|FEIP|
|Type|FEIP|
|SN|0|
|Version|1|
|Status|Active|
|Author|C_armX, No1_NrC7|
|Created|2026-03-12|
|PID||

## Abstract

FEIP (Freecash Extension Improvement Protocol) is a set of protocols for writing structured data into the Freecash blockchain. Each FEIP defines a specific data format and the consensus rules for parsing that data. By embedding data in the OP_RETURN output of Freecash transactions, FEIP enables decentralized identity, social networking, content publishing, token management, and other applications on top of the Freecash blockchain.

FEIP is one of several protocol series in the Freecash ecosystem.

This document (FEIP0) defines the general rules shared by all FEIP protocols.

## What is FEIP

### Naming

FEIP stands for **Freecash Extension Improvement Protocol**.

- **F** - Freecash: All FEIP data is written on the Freecash blockchain.
- **E** - Extension: FEIP extends the Freecash blockchain with higher-level application protocols.
- **I** - Improvement: FEIP protocols evolve through versioning to improve functionality.
- **P** - Protocol: Each FEIP defines a formal specification that all implementations MUST follow.

### Identification

Each FEIP protocol is identified by its serial number (`sn`) and version (`ver`). The naming convention is:

```
FEIP{sn}V{ver}_{Name}
```
For example: `FEIP3V4_CID` refers to the CID protocol, serial number 3, version 4.

## On-chain Data Format

### Transaction Structure

FEIP data is written in the **OP_RETURN** output of a Freecash transaction. The content of the OP_RETURN is a UTF-8 encoded JSON string.

### JSON Envelope

All FEIP protocols share a common JSON envelope:

```json
{
  "type": "FEIP",
  "sn": "<serial number>",
  "ver": "<version>",
  "name": "<protocol name>",
  "pid": "<protocol ID>",
  "did": "<document ID>",
  "data": {}
}
```

|Field| Required |Type|Description|
|---|----------|---|---|
|type| Y        |String|Fixed: "FEIP"|
|sn| Y        |String|Serial number of the protocol|
|ver| Y        |String|Version of the protocol|
|name| Y        |String|Name of the protocol|
|pid| N        |String|Protocol ID (the txid of the on-chain publish transaction that defined this protocol)|
|did| N        |String|Document ID, used when referencing a specific on-chain document|
|data| N        |Object |Protocol-specific data, defined by each FEIP|

### The `data` Field

The `data` field contains the protocol-specific payload. Most FEIP protocols define an `op` field inside `data` to indicate the operation type (e.g., `register`, `update`, `delete`). The remaining fields in `data` depend on the specific protocol and operation.

## General Rules

### 1. SIGHASH Flag

The SIGHASH flag of all transaction inputs MUST be `ALL` (0x41). This ensures that the signer commits to all inputs and outputs of the transaction, including the OP_RETURN content. Transactions with any other SIGHASH flag MUST be ignored by parsers.

### 2. OP_RETURN Size Limit

The maximum size of the OP_RETURN content is **4092 bytes**. FEIP transactions with OP_RETURN content exceeding this limit MUST be ignored by parsers.

### 3. Signer

The **signer** of a FEIP transaction is the address (FID) that signs the first input of the transaction. The signer is the entity performing the operation. All FEIP operations are attributed to the signer.

### 4. Timestamp and Block Height

- The **timestamp** of a FEIP operation is the timestamp of the block that contains the transaction.
- The **block height** is the height of that block.
- The **transaction index** is the position of the transaction within the block.
- When multiple FEIP operations from the same signer appear in the same block, the transaction index determines the order.

### 5. Transaction ID

The **transaction ID** (txid) serves as the unique identifier for each FEIP operation. It is commonly used as the `id` of the history record.

### 6. Parsing Order

FEIP operations MUST be parsed in strict blockchain order: by block height first, then by transaction index within the block. This ensures all parsers produce the same state.

### 7. Invalid Data

If the OP_RETURN content is not valid JSON, or does not conform to the expected format of the declared protocol (`type` + `sn` + `ver`), the transaction MUST be ignored by parsers. No state change should occur.

### 8. Blockchain Reorganization

When a blockchain reorganization occurs, parsers MUST ensure that the resulting state is identical to what would be produced by parsing the reorganized chain from genesis. The specific implementation strategy for achieving this is not prescribed.

### 9. CoinDays Destroyed (CDD)

CoinDays Destroyed (CDD) is calculated as the sum of (value × age_in_days) for all inputs consumed by the transaction. This mechanism prevents spam and gives weight to operations from FIDs with longer holding history.

- **Before block height 4,000,000**: No CDD requirement. All FEIP operations are accepted regardless of CDD.
- **Since block height 4,000,000**: A minimum of **1 CDD** is required for every FEIP on-chain operation. Transactions with less than 1 CDD MUST be ignored by parsers.

Individual FEIP protocols MAY define additional CDD requirements beyond the general minimum.

## Protocol Document Structure

Each FEIP protocol document SHOULD follow this structure:

```
# FEIP{sn}V{ver}_{Name}

## Contents
## Summary           - Identification table (Title, SN, Version, Category, Status, Author, PID)
## Abstract           - 2-3 sentence description
## Motivation         - Why this protocol exists
## Specification
   ### Operations     - Each operation with field table and OP_RETURN example
   ### Parsing Rules  - Numbered consensus rules
   ### Output         - Fields written to entities and history (entities are defined in FVEP)
## Examples           - Complete end-to-end examples
## Versioning         - Version history table
## Related Protocols
## Reference Implementation
```

### Summary Table Fields

|Field| Description                                                       |
|---|-------------------------------------------------------------------|
|Title| Protocol name                                                     |
|Type| Fixed: "FEIP"                                                     |
|SN| Serial number                                                     |
|Version| Current version number                                            |
|Category|To be classified into a certain category|
|Status| One of: Draft, Active, Deprecated, Replaced                       |
|Author| Author FID or name                                                |
|Created| Creation date                                                     |
|PID| Protocol ID (txid of the on-chain publish transaction)            |

### Categories

|Category| Description                                                                                      |
|---|--------------------------------------------------------------------------------------------------|
|Identity| Protocols related to identity management (CID, Nobody, Master, Home, NoticeFee, NID, Reputation) |
|Personal| Protocols for personal data (Contact, Mail, Secret, Box)                                         |
|Organize| Protocols for organizational structures (Square, Team)                                           |
|Publish| Protocols for content publishing (Statement, Text, Remark, Sound, Image, Video)                  |
|Finance| Protocols for financial operations (Token, Proof, Service, App)                                  |
|Construct| Protocols for defining protocols and code (Protocol, Code)                                       |

More categories can be added.

## FEIP List

|SN| Name       |Category| Description                                                  |
|---|------------|---|--------------------------------------------------------------|
|0| FEIP       |Construct| This document. Defines the general rules of FEIP.            |
|1| Protocol   |Construct| Define and manage protocols on-chain                         |
|2| Code       |Construct| Define and manage code references on-chain                   |
|3| CID        |Identity| Register human-readable Crypto Identity names                |
|4| Nobody     |Identity| Declare an address as abandoned by revealing its private key |
|5| Service    |Finance| Register and manage decentralized services                   |
|6| Master     |Identity| Assign a master address that owns all rights                 |
|7| Mail       |Personal| Send encrypted messages between FIDs ([FEIP7V4_Mail](FEIP7V4_Mail.md)) |
|8| Statement  |Publish| Publish signed statements                                    |
|9| Home       |Identity| Set homepage links for an address                            |
|10| NoticeFee  |Identity| Set a fee required to send notices                           |
|11| NID        |Identity| Set a human-readable name for an entity ID on-chain.         |
|12| Contact    |Personal| Manage encrypted contact information ([FEIP12V3_Contact](FEIP12V3_Contact.md)) |
|13| Box        |Personal| Virtual containers for personal data ([FEIP13V1_Box](FEIP13V1_Box.md)) |
|14| Proof      |Finance| On-chain proofs with cosigning and ownership ([FEIP14V1_Proof](FEIP14V1_Proof.md)) |
|15| APP        |Finance| Register and manage decentralized applications               |
|16| Reputation |Identity| Rate the reputation of other FIDs                            |
|17| Secret     |Personal| Share encrypted secrets between FIDs ([FEIP17V3_Secret](FEIP17V3_Secret.md)) |
|18| Team       |Organize| Owner-managed teams with consensus ([FEIP18V1_Team](FEIP18V1_Team.md)) |
|19| Square     |Organize| Unmanaged squares: create, join, leave, update ([FEIP19V4_Square](FEIP19V4_Square.md)) |
|20| Token      |Finance| Deploy, issue, transfer, destroy, close ([FEIP20V1_Token](FEIP20V1_Token.md)) |
|21| Text       |Publish| Text publications: publish, update, delete, rate ([FEIP21V1_Text](FEIP21V1_Text.md)) |
|22| Remark     |Publish| Remarks / annotations with `onDid` ([FEIP22V1_Remark](FEIP22V1_Remark.md)) |
|23| Sound      |Publish| Sound references: publish, update, delete, rate ([FEIP23V1_Sound](FEIP23V1_Sound.md)) |
|24| Image      |Publish| Image references: publish, update, delete, rate ([FEIP24V1_Image](FEIP24V1_Image.md)) |
|25| Video      |Publish| Video references: publish, update, delete, rate ([FEIP25V1_Video](FEIP25V1_Video.md)) |
