# FAPI4V1_Economics

|Field|Content|
|---|---|
|Title|Economics|
|Type|FAPI|
|SN|4|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Contents

- [Abstract](#abstract)
- [Summary](#summary)
- [1. Introduction](#1-introduction)
- [2. Currency and Units](#2-currency-and-units)
- [3. Balance Management](#3-balance-management)
  - [3.1. Account State](#31-account-state)
  - [3.2. Credit System](#32-credit-system)
  - [3.3. Balance Updates](#33-balance-updates)
- [4. Pricing Model](#4-pricing-model)
  - [4.1. Service-Level Pricing](#41-service-level-pricing)
  - [4.2. Cost Calculation](#42-cost-calculation)
  - [4.3. Cost Limit (maxCost)](#43-cost-limit-maxcost)
  - [4.4. Free Operations](#44-free-operations)
- [5. Charging Flow](#5-charging-flow)
  - [5.1. Procedure](#51-procedure)
  - [5.2. Idempotency](#52-idempotency)
  - [5.3. Charge Key Storage](#53-charge-key-storage)
- [6. Revenue Sharing](#6-revenue-sharing)
  - [6.1. Channel (via) System](#61-channel-via-system)
  - [6.2. Stakeholder Distribution](#62-stakeholder-distribution)
- [7. Recharge](#7-recharge)
  - [7.1. Recharge Flow](#71-recharge-flow)
- [8. Settlement](#8-settlement)
  - [8.1. Settlement Parameters](#81-settlement-parameters)
  - [8.2. Settlement Procedure](#82-settlement-procedure)
- [9. Audit Trail](#9-audit-trail)
- [10. Security Considerations](#10-security-considerations)
- [11. References](#11-references)

## Abstract

FAPI4V1 defines the Economics specification for the FAPI (Freecash API) protocol series. It specifies the billing model, balance management, pricing, charging flow, revenue sharing, recharge mechanism, and periodic on-chain settlement used by FAPI services. All monetary values are denominated in satoshi (1 FCH = 100,000,000 satoshi). This specification is language-agnostic and intended for implementation across any platform or runtime.

## Summary

FAPI operates a micropayment model in which clients pay per-request fees denominated in satoshi. Clients are identified by their peer FID as established by the underlying FUDP transport layer. Each client maintains an account balance on the server, with a configurable credit limit that permits requests before any on-chain payment. Pricing is determined per service through on-chain configuration parameters. The server calculates cost before execution, enforces optional client-side cost limits (`maxCost`), charges the client upon successful execution, and returns balance information in every response via the `balance`, `balanceSeq`, and `charged` fields defined in FAPI1. Revenue is shared between service operators, stakeholders, and referral channels. Clients fund their accounts by sending FCH to the server's dealer address. Accumulated fees are distributed to stakeholders through periodic on-chain settlement.

## 1. Introduction

Decentralized API services require a billing mechanism that is low-friction, transparent, and resistant to abuse. FAPI addresses these requirements through a credit-based micropayment system with the following design goals:

- **Low-friction access**: Clients can begin making requests immediately without upfront payment, within a configurable credit limit.
- **Per-request metering**: Cost varies by operation type and data volume, ensuring clients pay proportionally to resource consumption.
- **Revenue sharing**: Referral channels and service stakeholders receive configurable shares of collected fees.
- **Periodic on-chain settlement**: Accumulated fees are distributed to stakeholders at regular blockchain intervals, minimizing on-chain transaction overhead.
- **Cost transparency**: Every charged response includes the exact amount deducted, enabling client-side verification.

This specification defines the economic rules that all conforming FAPI server and client implementations MUST follow. It references fields and structures defined in FAPI1 (Core Protocol) and assumes FUDP as the transport layer for peer identity.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in RFC 2119.

## 2. Currency and Units

All FAPI internal accounting uses satoshi as the base unit. On-chain configuration values are expressed in FCH and converted internally.

| Unit | Value | Usage |
|---|---|---|
| FCH | 1 FCH = 100,000,000 satoshi | On-chain amounts, service configuration |
| satoshi | Base unit | All FAPI internal accounting |

On-chain configuration values (e.g., `pricePerKB`, `minCredit`) are denominated in FCH. FAPI implementations MUST convert these to satoshi by multiplying by 100,000,000 before use in internal calculations. All arithmetic MUST be performed in integer satoshi to avoid floating-point precision errors.

## 3. Balance Management

Each client, identified by its peer FID as established by the FUDP connection, has an account balance maintained by the server.

### 3.1. Account State

The server MUST maintain the following state for each client account:

| Field | Type | Description |
|---|---|---|
| balance | integer | Current balance in satoshi. May be negative, down to the negative of the credit limit. |
| balanceSeq | integer | Monotonically increasing sequence number, incremented on every balance-modifying operation. Used for optimistic concurrency control. |

A new client account is initialized with `balance = 0` and `balanceSeq = 0`.

### 3.2. Credit System

To enable low-friction access, new clients are granted a credit limit that allows requests before any payment is made. The credit limit defines the maximum negative balance permitted.

| Parameter | Default Value | Description |
|---|---|---|
| creditLimit | 10,000 satoshi | Maximum negative balance allowed for a client account |
| minCredit | (from service config, in FCH) | On-chain minimum credit requirement for the service |

A request is affordable if and only if:

```
balance - cost >= -creditLimit
```

If this condition is not met, the server MUST reject the request with status code 402 (PAYMENT_REQUIRED) as defined in FAPI1 Section 5.

Servers MAY adjust the credit limit per client based on reputation, payment history, or other criteria. The default credit limit applies to clients with no prior interaction.

### 3.3. Balance Updates

The server MUST include the `balance` and `balanceSeq` fields in every response to a charged request, as defined in FAPI1 Section 4.2. This provides the client with an up-to-date view of its account state after each operation.

Clients SHOULD track `balanceSeq` to detect missed or out-of-order balance updates. If a received `balanceSeq` is not exactly one greater than the previously observed value, the client SHOULD treat its local balance state as potentially stale.

## 4. Pricing Model

### 4.1. Service-Level Pricing

Pricing is configured per service in the on-chain service declaration. The following parameters define the pricing structure:

| Parameter | Unit | Description |
|---|---|---|
| pricePerKB | FCH | General per-KB charge for data operations |
| pricePerKBIn | FCH | Per-KB charge for ingress (upload) operations |
| pricePerKBOut | FCH | Per-KB charge for egress (download) operations |
| pricePerKBDay | FCH | Per-KB per-day charge for storage operations (DOCK component) |

These values are converted to satoshi internally as described in Section 2. If a specific directional price (e.g., `pricePerKBIn`) is not configured, the general `pricePerKB` applies.

### 4.2. Cost Calculation

The server MUST calculate cost before executing the request. Cost formulas vary by operation type:

**Data transfer operations:**

```
cost = ceil(dataSize / 1024) * pricePerKBSatoshi
```

Where `dataSize` is in bytes and `pricePerKBSatoshi` is `pricePerKBIn` or `pricePerKBOut` (converted to satoshi) depending on direction.

**Storage operations (DOCK component):**

```
cost = ceil(dataSize / 1024) * pricePerKBDaySatoshi * ttlDays
```

Where `ttlDays` is the requested storage duration in days.

**Query operations:**

```
cost = baseCostPerQuery
```

Where `baseCostPerQuery` is a server-defined constant for the specific query method.

**Broadcast operations:**

```
cost = fixedBroadcastFee
```

Where `fixedBroadcastFee` is a server-defined constant for relay and broadcast methods.

All cost values MUST be non-negative integers. The `ceil` function rounds up to the nearest integer, ensuring a minimum charge of one KB-unit for any non-zero data size.

### 4.3. Cost Limit (maxCost)

Clients MAY set the `maxCost` field in the request (see FAPI1 Section 3.2) to limit spending on a per-request basis.

If `maxCost` is set and the calculated cost exceeds `maxCost`, the server MUST reject the request with status code 402 (PAYMENT_REQUIRED) and MUST NOT execute the operation. The response `message` field SHOULD indicate that the cost limit was exceeded.

This mechanism protects clients from unexpected charges, particularly for relay operations and other methods where cost may vary significantly based on server-side conditions.

A `maxCost` value of 0 or null indicates no cost limit.

### 4.4. Free Operations

The following operations MUST NOT incur any charge:

- PING/PONG keep-alive messages
- CHAT_ACK delivery confirmations
- PEER_INFO exchanges
- Health check requests (e.g., `base.health`)

Servers MUST NOT deduct from a client's balance for these operations. Responses to free operations SHOULD still include the `balance` and `balanceSeq` fields for informational purposes.

## 5. Charging Flow

### 5.1. Procedure

The complete charging procedure for a FAPI request proceeds in the following order:

1. **Validate**: The server validates the request format and parameters per FAPI1 Section 6.
2. **Calculate cost**: The server computes the cost based on operation type and data size as defined in Section 4.2.
3. **Check maxCost**: If the request includes a `maxCost` value and the calculated cost exceeds it, the server MUST reject the request with code 402.
4. **Check affordability**: If `balance - cost < -creditLimit`, the server MUST reject the request with code 402.
5. **Execute**: The server performs the requested operation.
6. **Charge**: The server atomically deducts the cost from the client's balance and increments `balanceSeq`.
7. **Respond**: The server returns a response including the `charged`, `balance`, and `balanceSeq` fields as defined in FAPI1 Section 4.2.

If the operation fails during step 5 (Execute), the server MUST NOT charge the client. The response MUST include the appropriate error code, and the `charged` field MUST be 0 or absent.

### 5.2. Idempotency

Each charge operation is associated with a unique charge key to prevent double-charging on network retries or duplicate requests.

The charge key format is:

```
"{component}.{method}:{requestId}:{timestamp}"
```

Where:
- `component.method` is the `api` field from the request.
- `requestId` is the `id` field from the request.
- `timestamp` is the Unix epoch millisecond timestamp of when the charge was processed.

If a charge key has already been processed, the server MUST NOT charge again. The server MUST return the previously generated response for that charge key.

### 5.3. Charge Key Storage

Charge keys are stored with a bounded retention period to prevent unbounded storage growth while still providing protection against delayed retries.

| Parameter | Default Value | Description |
|---|---|---|
| chargeKeyRetentionDays | 100 days | Duration for which charge keys are retained |

After the retention period, charge keys MAY be purged. Clients SHOULD NOT retry requests older than the retention period, as double-charging protection is no longer guaranteed.

## 6. Revenue Sharing

### 6.1. Channel (via) System

When a request includes the `via` field (see FAPI1 Section 3.2), the referenced FID acts as a referral channel and receives a share of the fee charged for that request.

| Parameter | Unit | Default | Description |
|---|---|---|---|
| orderViaShareBps | basis points | configurable | Share of fee allocated to the order channel |
| consumeViaShareBps | basis points | configurable | Share of fee allocated to the consume channel |

One basis point equals 0.01%. A value of 1000 basis points equals 10%.

The channel share is calculated as:

```
channelShare = floor(charged * viaShareBps / 10000)
```

The channel share is deducted from the service operator's portion of the fee, not added to the client's cost.

### 6.2. Stakeholder Distribution

Service operators define stakeholders who receive portions of the revenue. Stakeholders are declared in the service's on-chain configuration:

```json
{
  "stakeholders": {
    "FID1": 10000,
    "FID2": 5000
  }
}
```

Values are in basis points (10000 = 100%). The sum of all stakeholder shares MUST NOT exceed 10000. Revenue remaining after channel shares is distributed proportionally to stakeholders during settlement (see Section 8).

If the sum of stakeholder shares is less than 10000, the residual revenue is retained by the service operator's primary address.

## 7. Recharge

Clients fund their accounts by sending FCH to the server's dealer address on the Freecash blockchain.

### 7.1. Recharge Flow

1. The client obtains the server's dealer address through the service configuration or a dedicated info endpoint.
2. The client constructs and broadcasts a standard Freecash transaction sending FCH to the dealer address.
3. The server's blockchain parser detects the incoming UTXO addressed to the dealer.
4. The server identifies the sender FID from the transaction inputs.
5. The server credits the sender's account balance:

```
balance += utxoValue (in satoshi)
balanceSeq += 1
```

6. The credit operation uses the transaction ID (txid) as an idempotency key to prevent double-crediting from blockchain reorganizations or parser replays.

The server MUST NOT credit the balance until the transaction has at least one confirmation. Servers SHOULD wait for a configurable number of confirmations (RECOMMENDED: 1) before crediting.

If the sender FID cannot be determined from the transaction inputs, the server MUST NOT credit any account and SHOULD log the unattributed payment for manual resolution.

## 8. Settlement

Settlement is the process of distributing accumulated fees from the server's operational pool to stakeholders and channel recipients.

### 8.1. Settlement Parameters

| Parameter | Default Value | Description |
|---|---|---|
| settleCycle | 14,400 blocks (~10 days) | Number of blocks between settlement cycles |
| minSettleAmount | 100,000 satoshi (0.001 FCH) | Minimum accumulated fee to trigger a settlement payout |
| rollbackWindow | 30 blocks | Safety margin subtracted from the current block height to avoid settling fees from blocks that may be reorganized |

### 8.2. Settlement Procedure

Settlement is triggered when:

```
currentBlockHeight mod settleCycle == 0
```

The procedure is as follows:

1. **Calculate effective height**: `effectiveHeight = currentBlockHeight - rollbackWindow`. Only fees accumulated from blocks at or below `effectiveHeight` are eligible for settlement.
2. **Aggregate fees**: Sum all charged fees since the last settlement, restricted to the effective height range.
3. **Check minimum**: If the total accumulated fees are less than `minSettleAmount`, settlement is deferred to the next cycle.
4. **Deduct channel shares**: For each `via` FID with accumulated channel shares, calculate the total owed and add it to the settlement distribution.
5. **Distribute to stakeholders**: Distribute the remaining fees to stakeholders according to their basis point allocations as defined in Section 6.2.
6. **Record settlement**: Record the settlement with a cycle ID as an idempotency key, formatted as `"settle:{settleCycleNumber}"`. If this cycle ID has already been processed, the settlement MUST NOT execute again.
7. **Execute on-chain transfers**: Construct and broadcast Freecash transactions to distribute funds to stakeholder and channel addresses.

Settlement transactions SHOULD batch multiple payouts into a single transaction where possible to minimize on-chain fees.

## 9. Audit Trail

All balance-modifying operations MUST be recorded in an append-only audit log maintained by the server.

Each audit entry MUST include the following fields:

| Field | Type | Description |
|---|---|---|
| timestamp | integer | Unix epoch milliseconds when the operation was recorded |
| peerFid | string | FID of the client whose balance was modified |
| operationType | string | Type of operation: `charge`, `credit`, `settle`, `adjust` |
| amount | integer | Amount in satoshi (positive for credits, negative for charges) |
| balanceAfter | integer | Client's balance after the operation |
| chargeKey | string | Idempotency key for the operation (charge key, txid, or cycle ID) |

Audit logs are retained for compliance, dispute resolution, and operational monitoring. The retention period for audit logs is implementation-defined but SHOULD be at least 365 days.

Servers SHOULD provide an administrative interface for querying audit logs by FID, time range, and operation type.

## 10. Security Considerations

1. **Balance authority**: The server is the single source of truth for all account balances. Clients cannot forge or modify balance claims. The `balanceSeq` field enables clients to detect stale or tampered balance data.

2. **Credit risk**: The credit limit bounds the server's financial exposure to non-paying clients. Servers SHOULD monitor accounts with negative balances and MAY reduce the credit limit or deny service to clients that do not recharge within a reasonable period.

3. **Double-charging prevention**: Charge key idempotency (Section 5.2) prevents duplicate charges caused by network retries, FUDP stream retransmissions, or client-side retry logic.

4. **Cost transparency**: The `charged` field in every response enables clients to independently verify that they were charged correctly. Clients SHOULD log and audit `charged` values against their expected costs.

5. **Cost control**: The `maxCost` field (Section 4.3) allows clients to set per-request spending limits, preventing unexpectedly high charges from variable-cost operations.

6. **Settlement atomicity**: Settlement uses cycle-based idempotency keys (Section 8.2) to prevent duplicate distributions caused by parser replays or server restarts.

7. **Recharge integrity**: Transaction ID-based idempotency (Section 7.1) prevents double-crediting from blockchain reorganizations. The rollback window in settlement (Section 8.1) provides additional protection against settling fees from subsequently orphaned blocks.

8. **Integer arithmetic**: All monetary calculations MUST use integer arithmetic in satoshi. Floating-point arithmetic MUST NOT be used for any balance, cost, or distribution computation to prevent rounding errors and ensure deterministic results across implementations.

## 11. References

- **FAPI1**: Core Protocol. Defines the wire format, request/response structures (including `balance`, `balanceSeq`, `charged`, `maxCost`, `via` fields), status codes, and API routing used throughout this specification.
- **FUDP**: Freecash UDP Protocol. Provides the transport layer, including peer identity (FID) used for account identification.
- **RFC 2119**: Key words for use in RFCs to indicate requirement levels.
