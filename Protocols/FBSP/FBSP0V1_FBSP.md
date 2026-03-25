# FBSP0V1_FBSP

## Contents

[Summary](#summary)

[Abstract](#abstract)

[What is FBSP](#what-is-fbsp)

[Scope Boundaries](#scope-boundaries)

[General Rules](#general-rules)

[Protocol Document Structure](#protocol-document-structure)

[FBSP List](#fbsp-list)

---

## Summary

|Field|Content|
|---|---|
|Title|FBSP|
|Type|FBSP|
|SN|0|
|Ver|1|
|Status|Draft|
|Author|C_armX, No1_NrC7|
|Created|2026-03-22|
|PID||

## Abstract

FBSP (Freeverse Business Standard Protocol) defines the standards for commercial services and business activities within the Freeverse ecosystem — swap services, marketplace rules, API pricing, payment flows, and other commercial patterns. FBSP protocols are voluntarily adopted by service providers and participants to ensure fair, transparent, and interoperable commercial interactions.

This document (FBSP0) defines the general rules shared by all FBSP protocols.

## What is FBSP

### Naming

FBSP stands for **Freeverse Business Standard Protocol**.

- **F** - Freeverse: FBSP serves the Freeverse ecosystem.
- **BS** - Business Standard: FBSP defines standards for commercial activities and services.
- **P** - Protocol: Each FBSP defines a formal specification for a business pattern.

### Identification

Each FBSP protocol is identified by its serial number (`sn`) and version (`ver`). The naming convention is:

```
FBSP{sn}V{ver}_{Name}
```

For example: `FBSP1V1_Swap` refers to the Swap service protocol, serial number 1, version 1.

### The Need for FBSP

As the Freeverse ecosystem grows, commercial services emerge organically — swap services, API providers, storage services, communication services, etc. Without shared business standards, each service defines its own rules, making it difficult for users to compare services, switch providers, or trust that services operate fairly.

FBSP fills this gap by defining common business patterns:

- **How** a swap service matches and executes trades.
- **How** API services price their access and manage subscriptions.
- **How** storage services handle payment and service levels.
- **How** disputes are resolved between service providers and consumers.

### Relationship with Other Protocol Series

FBSP builds on top of other protocol series:

|Layer|Protocol Series|Role for FBSP|
|---|---|---|
|Blockchain Consensus|FBP|Provides the trusted transaction layer|
|On-chain Application|FEIP|Provides on-chain service registration (Service, App), identity (CID), payments|
|Ecosystem Foundation|FVEP|Provides entity model, ID system, currency definitions|
|Technical Standard|FTSP|Provides cryptographic and transport implementations|
|**Business Standard**|**FBSP**|**Defines commercial service rules and business logic**|

|Aspect|FEIP (Service/App)|FBSP|
|---|---|---|
|Focus|Service *registration* on-chain|Service *operation* standards|
|Content|"Service X exists, owned by FID Y"|"How swap services match orders, calculate fees, settle trades"|
|Enforcement|Parser consensus|Voluntary adoption|
|Scope|Identity of the service|Behavior of the service|

### Position in the Protocol Stack

FBSP is the highest layer of the Freeverse protocol stack, closest to the end user:

|Layer|Protocol Series|Scope|
|---|---|---|
|Blockchain Consensus|FBP|Block validation, transaction rules, mining|
|On-chain Application|FEIP|Structured data in OP_RETURN|
|Ecosystem Foundation|FVEP|Entities, IDs, time, currency (concepts)|
|Technical Standard|FTSP|Algorithms, encoding, transport (implementations)|
|**Business Standard**|**FBSP**|**Commercial services, marketplace rules, business logic**|

## Scope Boundaries

### What Belongs in FBSP

FBSP protocols define standards for commercial activities that benefit from shared rules:

1. **Exchange Services**: Swap service rules — order matching, price discovery, liquidity provision, settlement procedures.
2. **API Services**: API access pricing, session management, quota policies, SLA standards.
3. **Storage Services**: Data storage pricing, availability guarantees, retrieval rules.
4. **Communication Services**: Messaging service standards, delivery guarantees, pricing models.
5. **Payment Patterns**: Common payment flows, refund policies, subscription models.
6. **Service Discovery**: How services advertise capabilities, pricing, and status.

### What Does NOT Belong in FBSP

- **Service identity and registration** → FEIP (on-chain Service/App entities).
- **Currency and value definitions** → FVEP (FCH, Satoshi, CoinDay).
- **Cryptographic procedures** → FTSP (encryption, signing algorithms).
- **Blockchain consensus** → FBP (block validation, transaction rules).

## General Rules

### 1. Voluntary Adoption

FBSP protocols are adopted voluntarily by service providers. Unlike FBP (mandatory for all nodes) or FTSP (mandatory for interoperability), FBSP defines recommended business practices. Service providers MAY deviate from FBSP standards but SHOULD document any deviations.

### 2. Transparency

FBSP protocols MUST be designed to promote transparency. Users SHOULD be able to verify that a service operates according to the FBSP standard it claims to follow. Where possible, service behavior SHOULD be verifiable on-chain or through cryptographic proofs.

### 3. Fair Competition

FBSP protocols MUST NOT create barriers to entry or favor incumbent service providers. The standards SHOULD enable any participant to offer competing services on equal footing.

### 4. User Protection

FBSP protocols SHOULD include provisions for user protection:
- Clear pricing and fee disclosure.
- Dispute resolution mechanisms.
- Data portability (users can move between providers).
- Service level expectations.

### 5. On-chain Anchoring

Where practical, FBSP protocols SHOULD anchor critical business events on-chain (via FEIP transactions) to provide an immutable audit trail. Examples include service registration, large trades, and dispute resolutions.

### 6. Versioning

FBSP protocols evolve through versioning:
- The version number is incremented for each change.
- Service providers SHOULD support the latest version.
- Breaking changes MUST result in a new major version.
- Transition periods SHOULD be provided for version upgrades.

### 7. Compliance Declaration

Service providers implementing an FBSP standard SHOULD declare their compliance level:
- **Full**: The service implements all MUST and SHOULD requirements.
- **Partial**: The service implements all MUST requirements but deviates from some SHOULD requirements.
- **Custom**: The service uses the FBSP as a reference but has significant deviations.

### 8. RFC 2119 Keywords

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in FBSP documents are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

## Protocol Document Structure

Each FBSP protocol document SHOULD follow this structure:

```
# FBSP{sn}V{ver}_{Name}

## Contents
## Summary               - Identification table (Title, SN, Ver, Category, Status, Author, PID)
## Abstract               - 2-3 sentence description
## Motivation             - Why this business standard is needed
## Specification
   ### Roles              - Participants and their responsibilities
   ### Workflow            - Step-by-step business process
   ### Data Formats        - Request/response structures
   ### Pricing             - Fee structures and payment rules
   ### Rules               - Numbered business rules and constraints
## Examples               - Complete business scenario examples
## Security Considerations
## Versioning             - Version history table
## Related Protocols
## Reference Implementation
```

### Summary Table Fields

|Field|Description|
|---|---|
|Title|Protocol name|
|Type|Fixed: "FBSP"|
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
|Exchange|Token swap, trading, and exchange services|
|API|API access, pricing, and service level standards|
|Storage|Data storage and retrieval service standards|
|Communication|Messaging and notification service standards|
|Payment|Payment flows, settlement, and refund patterns|

More categories can be added as the ecosystem evolves.

## FBSP List

|SN|Name|Category|Description|
|---|---|---|---|
|0|FBSP|—|This document. Defines the general rules of FBSP.|
