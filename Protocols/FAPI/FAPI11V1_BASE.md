# FAPI11V1_BASE

|Field|Content|
|---|---|
|Title|BASE|
|Type|FAPI|
|SN|11|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-03-28|
|PID||

## Contents

- [Abstract](#abstract)
- [Summary](#summary)
- [1. Overview](#1-overview)
- [2. API List](#2-api-list)
- [3. Query and Search Methods](#3-query-and-search-methods)
  - [3.1. base.getByIds](#31-basegetbyids)
  - [3.2. base.search](#32-basesearch)
  - [3.3. base.freerByIds](#33-basefreerbyids)
  - [3.4. base.totals](#34-basetotals)
  - [3.5. base.health](#35-basehealth)
- [4. Balance and UTXO Methods](#4-balance-and-utxo-methods)
  - [4.1. base.balanceByIds](#41-basebalancebyids)
  - [4.2. base.cashValid](#42-basecashvalid)
  - [4.3. base.getUtxo](#43-basegetutxo)
- [5. Chain Information Methods](#5-chain-information-methods)
  - [5.1. base.chainInfo](#51-basechaininfo)
  - [5.2. base.blockTimeHistory](#52-baseblocktimehistory)
  - [5.3. base.difficultyHistory](#53-basedifficultyhistory)
  - [5.4. base.hashRateHistory](#54-basehashratehistory)
- [6. Mempool Methods](#6-mempool-methods)
  - [6.1. base.unconfirmed](#61-baseunconfirmed)
  - [6.2. base.unconfirmedCashes](#62-baseunconfirmedcashes)
- [7. Transaction Operation Methods](#7-transaction-operation-methods)
  - [7.1. base.broadcastTx](#71-basebroadcasttx)
  - [7.2. base.decodeTx](#72-basedecodetx)
  - [7.3. base.estimateFee](#73-baseestimatefee)
- [8. Security Considerations](#8-security-considerations)
- [9. Versioning](#9-versioning)
- [10. References](#10-references)

---

## Abstract

FAPI11V1 defines the BASE component specification for the FAPI (Freecash API) protocol series. BASE is the foundational component that provides blockchain data queries and transaction operations. It exposes 17 API methods organized into five categories: Query and Search, Balance and UTXO, Chain Information, Mempool, and Transaction Operations. BASE requires an Elasticsearch backend for indexed blockchain data and a blockchain RPC node for transaction operations. This document is extracted from the BASE component section of FAPI3 (Components) and promoted to a standalone specification for clarity and independent reference.

## Summary

The BASE component serves as the primary interface between clients and the Freecash blockchain. It enables clients to query indexed blockchain data (blocks, transactions, UTXOs, addresses), retrieve real-time chain state information, inspect the mempool, and submit transactions for broadcast. All query methods accept FCDSL syntax as defined in FAPI2. Transaction operation methods use direct parameters. BASE is identified by the type ID `BASE@No1_NrC7` and is the most commonly deployed component in the FAPI ecosystem. A minimal FAPI server hosting only the BASE component provides a complete blockchain query and transaction service.

## 1. Overview

The BASE component provides blockchain data queries and transaction operations. It is the foundational component that most clients interact with.

BASE requires the following backend infrastructure:

- **Elasticsearch**: Stores indexed blockchain data including blocks, transactions, UTXOs, addresses, and protocol objects. All query and search methods operate against this index.
- **Blockchain RPC node**: Provides access to the live blockchain network for transaction broadcast, mempool inspection, and real-time chain state queries.

**Type ID**: `BASE@No1_NrC7`

BASE does not handle binary data. All requests and responses use the standard JSON envelope defined in FAPI1.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in RFC 2119.

## 2. API List

The BASE component exposes 17 methods organized into five categories:

| # | API | Category | Description |
|---|---|---|---|
| 1 | `base.getByIds` | Query/Search | Fetch entities by ID list |
| 2 | `base.search` | Query/Search | Search entities with filters and sorting |
| 3 | `base.freerByIds` | Query/Search | Get Freer data with real-time balance and coin-day |
| 4 | `base.totals` | Query/Search | Get document counts for all indices |
| 5 | `base.health` | Query/Search | Health check status of ES/RPC backends |
| 6 | `base.balanceByIds` | Balance/UTXO | Get balances for a list of FIDs |
| 7 | `base.cashValid` | Balance/UTXO | Get valid (unspent) UTXOs |
| 8 | `base.getUtxo` | Balance/UTXO | Get UTXOs in standard format |
| 9 | `base.chainInfo` | Chain Info | Current block height, hash rate, difficulty |
| 10 | `base.blockTimeHistory` | Chain Info | Block creation time history |
| 11 | `base.difficultyHistory` | Chain Info | Mining difficulty history |
| 12 | `base.hashRateHistory` | Chain Info | Network hash rate history |
| 13 | `base.unconfirmed` | Mempool | Unconfirmed (pending) transactions |
| 14 | `base.unconfirmedCashes` | Mempool | Unconfirmed UTXOs |
| 15 | `base.broadcastTx` | Transaction Operations | Broadcast a signed transaction |
| 16 | `base.decodeTx` | Transaction Operations | Decode a raw transaction |
| 17 | `base.estimateFee` | Transaction Operations | Estimate transaction fee |

## 3. Query and Search Methods

### 3.1. base.getByIds

Fetch one or more entities by their IDs from any indexed entity type.

- **Category**: Query/Search
- **Request**: `fcdsl` with `entity` (the index/entity type name) and `ids` (list of IDs, maximum 100).
- **Response**: `data` contains an array of matching entity objects. `got` indicates the number of items returned. IDs that do not match any document are silently omitted from the result.

**Request Example**:
```json
{
  "api": "base.getByIds",
  "fcdsl": {
    "entity": "block",
    "ids": ["000000000000034a7dedef4a161fa058a2d67a173a90155f3a2fe6fc132e0ebf"]
  }
}
```

**Response Example**:
```json
{
  "code": 0,
  "msg": "OK",
  "got": 1,
  "data": [
    {
      "blockId": "000000000000034a7dedef4a161fa058a2d67a173a90155f3a2fe6fc132e0ebf",
      "height": 1234567,
      "time": 1700000000,
      "txCount": 5,
      "size": 2048
    }
  ]
}
```

### 3.2. base.search

Search entities using FCDSL filter, sort, and pagination syntax.

- **Category**: Query/Search
- **Request**: `fcdsl` with `entity`, `filter`, `sort`, `size`, and optionally `last` for cursor-based pagination. See FAPI2 for full FCDSL syntax.
- **Response**: `data` contains an array of matching entities. `got` indicates the number of items returned, `total` indicates the total number of matching documents, and `last` provides the cursor value for fetching the next page.

**Request Example**:
```json
{
  "api": "base.search",
  "fcdsl": {
    "entity": "tx",
    "filter": { "range": { "height": { "gte": 1000000 } } },
    "sort": [{ "height": "desc" }],
    "size": 20
  }
}
```

**Response Example**:
```json
{
  "code": 0,
  "msg": "OK",
  "got": 20,
  "total": 543210,
  "last": ["1543209"],
  "data": [
    {
      "txId": "a1b2c3d4...",
      "height": 1543210,
      "blockId": "00000000...",
      "inCount": 1,
      "outCount": 2,
      "fee": 226
    }
  ]
}
```

### 3.3. base.freerByIds

Retrieve Freer (user) information by FID list with real-time computed balance and coin-day values.

- **Category**: Query/Search
- **Request**: `fcdsl` with `entity` set to `"freer"` and `ids` containing a list of FIDs.
- **Response**: `data` contains an array of Freer objects, each including `balance` (current spendable balance in satoshi) and `cd` (coin-day value) computed in real-time rather than from the last indexed snapshot.

### 3.4. base.totals

Return document counts for all indexed entity types.

- **Category**: Query/Search
- **Request**: No parameters required.
- **Response**: `data` contains an object mapping entity names to their document counts.

### 3.5. base.health

Return health check status of the BASE component and its backend services.

- **Category**: Query/Search
- **Request**: No parameters required.
- **Response**: `data` contains a status object indicating the health of the Elasticsearch cluster and blockchain RPC node connections.

## 4. Balance and UTXO Methods

### 4.1. base.balanceByIds

Retrieve FCH balances for a list of FIDs.

- **Category**: Balance/UTXO
- **Request**: `fcdsl` with `ids` containing a list of FIDs (Freecash addresses).
- **Response**: `data` contains an object mapping each FID to its balance in satoshi.

### 4.2. base.cashValid

Retrieve valid (unspent) UTXOs matching the given filter criteria.

- **Category**: Balance/UTXO
- **Request**: `fcdsl` with `filter` specifying UTXO selection criteria (e.g., by owner FID, minimum value).
- **Response**: `data` contains an array of Cash objects with the following fields:
  - `cashId`: The unique identifier of the UTXO.
  - `owner`: The FID (address) that owns this UTXO.
  - `value`: The value in satoshi.
  - `birthHeight`: The block height at which this UTXO was created.
  - `spendTxId`: The transaction ID that spent this UTXO, or `null` for unspent outputs.

### 4.3. base.getUtxo

Retrieve UTXOs in the standardized Utxo format, suitable for transaction construction.

- **Category**: Balance/UTXO
- **Request**: `fcdsl` with `filter` specifying UTXO selection criteria.
- **Response**: `data` contains an array of Utxo objects with the following fields:
  - `txId`: The transaction ID containing this output.
  - `index`: The output index within the transaction.
  - `value`: The value in satoshi.
  - `address`: The address (FID) that owns this output.
  - `script`: The output script in hex encoding.

## 5. Chain Information Methods

### 5.1. base.chainInfo

Return current blockchain state information.

- **Category**: Chain Info
- **Request**: No parameters required.
- **Response**: `data` contains a FchChainInfo object with the following fields:
  - `bestHeight`: The height of the most recently confirmed block.
  - `bestBlockHash`: The hash of the most recently confirmed block.
  - `difficulty`: The current mining difficulty.
  - `hashRate`: The estimated network hash rate.
  - `circulatingSupply`: The total circulating supply of FCH in satoshi.

### 5.2. base.blockTimeHistory

Return block creation time statistics over a historical range.

- **Category**: Chain Info
- **Request**: `fcdsl` with filter and sort parameters to specify the range.
- **Response**: `data` contains an array of time-series data points.

### 5.3. base.difficultyHistory

Return mining difficulty values over a historical range.

- **Category**: Chain Info
- **Request**: `fcdsl` with filter and sort parameters to specify the range.
- **Response**: `data` contains an array of difficulty data points.

### 5.4. base.hashRateHistory

Return network hash rate over a historical range.

- **Category**: Chain Info
- **Request**: `fcdsl` with filter and sort parameters to specify the range.
- **Response**: `data` contains an array of hash rate data points.

## 6. Mempool Methods

### 6.1. base.unconfirmed

Return information about unconfirmed (pending) transactions in the mempool.

- **Category**: Mempool
- **Request**: `fcdsl` with optional filter and pagination parameters.
- **Response**: `data` contains an array of unconfirmed transaction info objects.

### 6.2. base.unconfirmedCashes

Return unconfirmed UTXOs from the mempool.

- **Category**: Mempool
- **Request**: `fcdsl` with optional filter and pagination parameters.
- **Response**: `data` contains an array of Cash objects representing unconfirmed UTXOs.

## 7. Transaction Operation Methods

### 7.1. base.broadcastTx

Broadcast a signed raw transaction to the blockchain network.

- **Category**: Transaction Operations
- **Request**: `params` with `rawTx` (hex-encoded signed transaction string).
- **Response**: `data` contains the transaction ID (txid) if broadcast was successful.
- **Charging**: This is a charged operation. The server deducts a fee from the caller's account balance. If the caller's balance is insufficient, the server MUST return code 402 (PAYMENT_REQUIRED) without broadcasting the transaction.

**Request Example**:
```json
{
  "api": "base.broadcastTx",
  "params": {
    "rawTx": "0100000001abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890000000006a47304402..."
  }
}
```

**Response Example**:
```json
{
  "code": 0,
  "msg": "OK",
  "data": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
}
```

### 7.2. base.decodeTx

Decode a raw transaction without broadcasting it.

- **Category**: Transaction Operations
- **Request**: `params` with `rawTx` (hex-encoded transaction string).
- **Response**: `data` contains the decoded transaction object with inputs, outputs, and metadata.

### 7.3. base.estimateFee

Estimate the transaction fee for a given transaction shape.

- **Category**: Transaction Operations
- **Request**: `params` with `inputCount` (number of inputs) and `outputCount` (number of outputs).
- **Response**: `data` contains the estimated fee in satoshi.

**Request Example**:
```json
{
  "api": "base.estimateFee",
  "params": {
    "inputCount": 2,
    "outputCount": 3
  }
}
```

**Response Example**:
```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "estimatedFee": 452,
    "feeRate": 1
  }
}
```

## 8. Security Considerations

1. **Authentication**: All FAPI requests are transported over FUDP, which authenticates peers using secp256k1 public keys during the handshake. The `peerId` passed to the BASE handler is the authenticated FID. BASE MUST NOT implement its own authentication layer.

2. **Balance enforcement for broadcastTx**: The `base.broadcastTx` method is a charged operation. The server MUST verify the caller's account balance before broadcasting the transaction. If the balance is insufficient, the server MUST return code 402 (PAYMENT_REQUIRED) without performing the broadcast.

3. **Read-only indexed data**: BASE provides read-only access to indexed blockchain data stored in Elasticsearch. No BASE method permits writing to, modifying, or deleting indexed data. The Elasticsearch indices are populated exclusively by the blockchain parser (FchParser/FeipParser), and BASE queries these indices in a read-only capacity. Server implementations MUST NOT expose write access to indexed data through any BASE method.

4. **Input validation**: Servers MUST validate all input parameters. The `ids` list in `base.getByIds` and related methods MUST be limited to a maximum of 100 entries. The `rawTx` parameter in `base.broadcastTx` and `base.decodeTx` MUST be validated as a well-formed hexadecimal string before processing.

5. **Rate limiting**: Servers SHOULD implement rate limiting for resource-intensive operations, particularly `base.search` with broad filters and `base.broadcastTx`. The economic model defined in FAPI4 provides a natural rate-limiting mechanism through per-request charging.

## 9. Versioning

| Version | Date | Changes |
|---|---|---|
| 1 | 2026-03-28 | Initial specification. Extracted from FAPI3V1 Section 4 (BASE Component) and promoted to standalone document with 17 methods in 5 categories. |

## 10. References

- **FAPI1**: Core Protocol. Defines the wire format (UnifiedCodec), request/response structures, status codes, and API routing mechanism. All BASE requests and responses conform to the structures defined therein.
- **FAPI2**: FCDSL (Freecash Domain Specific Language). Defines the query syntax used in the `fcdsl` field for all query-category methods in BASE.
- **FAPI3**: Components. Defines the component model, lifecycle states, registration mechanism, and the original specification of BASE alongside other built-in components. This document (FAPI11) supersedes the BASE section of FAPI3.
- **FAPI4**: Economics. Defines the pricing model, balance management, and charging rules that apply to charged BASE operations such as `base.broadcastTx`.
- **FUDP**: Freecash UDP Protocol. Provides the transport layer, including peer identity, authentication, encryption, reliable delivery, and stream multiplexing.
- **RFC 2119**: Key words for use in RFCs to indicate requirement levels.
