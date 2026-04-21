# FCH API Reference

The FCH API module provides a set of blockchain data access endpoints tailored for FCH wallet integration. These APIs are served under the APIP server and cover block queries, transaction operations, UTXO management, and identity lookup.

**Base URL**: `/{sn}/v1/{endpoint}`

---

## Authentication

Each endpoint supports one or more authentication types:

| Auth Type | Description |
|---|---|
| `FREE` | No authentication required (used by GET requests on some endpoints) |
| `FC_SIGN_BODY` | FC signature over the request body (used by POST requests) |
| `FC_SIGN_URL` | FC signature over the URL (used by GET requests requiring auth) |
| `ENCRYPTED` | Encrypted request body |

---

## API Endpoints

### 1. BestBlock

Get the current best (latest) block on the Freecash blockchain.

- **URL**: `/sn2/v1/bestBlock`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FREE`, POST = `FC_SIGN_BODY`

#### GET Request

No parameters required.

```
GET /sn2/v1/bestBlock
```

#### POST Request

Standard APIP signed request body.

#### Response

Returns a `Block` object with the latest block data.

```json
{
  "code": 0,
  "message": "Success",
  "bestHeight": 850000,
  "bestBlockId": "000000000000000000...",
  "data": {
    "id": "000000000000000000...",
    "height": 850000,
    ...
  }
}
```

---

### 2. BlockByHeights

Query block information by block heights.

- **URL**: `/sn2/v1/blockByHeights`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FC_SIGN_URL`, POST = `ENCRYPTED`

#### POST Request

Uses FCDSL query with block heights. The lookup field is `height`.

```json
{
  "fcdsl": {
    "ids": ["850000", "850001", "850002"]
  }
}
```

#### GET Request

Signed URL with height parameters.

#### Response

Returns a list of `Block` objects matching the requested heights.

---

### 3. BroadcastTx

Broadcast a raw transaction to the Freecash network.

- **URL**: `/sn18/v1/broadcastTx`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FREE`, POST = `FC_SIGN_BODY`

#### Request Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `rawTx` | String | Yes | The raw transaction hex string to broadcast |

#### POST Request Body

```json
{
  "fcdsl": {
    "other": {
      "rawTx": "0200000001..."
    }
  }
}
```

#### GET Request

```
GET /sn18/v1/broadcastTx?rawTx=0200000001...
```

#### Response

On success, returns the transaction ID (hex string):

```json
{
  "code": 0,
  "message": "Success",
  "data": "a1b2c3d4e5f6..."
}
```

On failure, returns an error message from the node.

---

### 4. CashValid

Query valid (unspent) UTXOs for a given address, with optional filtering by amount, coindays, message size, output size, and since-height.

- **URL**: `/sn18/v1/cashValid`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FREE`, POST = `FC_SIGN_BODY`

#### Mode 1: Simple Query (via `other` parameters or GET query)

| Parameter | Type | Required | Description |
|---|---|---|---|
| `fid` | String | No | The Freecash address (FID) to query UTXOs for |
| `amount` | String | No | Minimum amount in FCH (converted to satoshi internally) |
| `cd` | String | No | Minimum coindays required |
| `msgSize` | String | No | Estimated OP_RETURN message size in bytes |
| `outputSize` | String | No | Estimated number of outputs |
| `sinceHeight` | String | No | Only include UTXOs confirmed at or after this block height |

##### GET Request

```
GET /sn18/v1/cashValid?fid=FAddress...&amount=1.5&cd=100
```

##### POST Request Body (Simple)

```json
{
  "fcdsl": {
    "other": {
      "fid": "FAddress...",
      "amount": "1.5",
      "cd": "100",
      "msgSize": "200",
      "outputSize": "2",
      "sinceHeight": "800000"
    }
  }
}
```

#### Mode 2: FCDSL Query (advanced)

When `fcdsl.other` is null, the endpoint performs an FCDSL-based search on the `cash` index with `valid=true` automatically applied. Supports full FCDSL filter, query, and sort capabilities.

```json
{
  "fcdsl": {
    "filter": {
      "terms": {
        "fields": ["owner"],
        "values": ["FAddress..."]
      }
    },
    "sort": [
      {"field": "lastTime", "order": "desc"}
    ],
    "size": 20
  }
}
```

#### Response

Returns a list of `Cash` (UTXO) objects:

```json
{
  "code": 0,
  "message": "Success",
  "total": 50,
  "got": 20,
  "data": [
    {
      "id": "txid:vout",
      "owner": "FAddress...",
      "value": 150000000,
      "valid": true,
      ...
    }
  ]
}
```

If no valid cash is found, returns error code `2007` (CashNoFound).

---

### 5. GetFidCid

Look up a Freecash identity (Freer/CID) by FID or CID.

- **URL**: `/sn3/v1/getFidCid`
- **Methods**: `GET` only (POST returns `1017 MethodNotAvailable`)
- **Auth**: `FREE`

#### Request Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `id` | String | Yes | The FID (address) or CID (crypto identity) to look up |

#### GET Request

```
GET /sn3/v1/getFidCid?id=FAddress_or_CID
```

#### Response

Returns a single `Freer` object matching the given FID or CID:

```json
{
  "code": 0,
  "message": "Success",
  "data": {
    "id": "FAddress...",
    "usedCids": ["CID1", "CID2"],
    ...
  }
}
```

**Error cases**:
- `1011` DataNotFound - No matching identity found
- Other error - More than 1 result found (provide the full FID or CID)

---

### 6. TxByFid

Query transactions associated with a specific FID (address).

- **URL**: `/sn2/v1/txByFid`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FC_SIGN_URL`, POST = `FC_SIGN_BODY`

#### Request

Uses the standard FCDSL request body. The handler performs a FID-based transaction mask query, returning transactions where the given FID appears as sender or receiver.

#### POST Request Body

```json
{
  "fcdsl": {
    "query": {
      "terms": {
        "fields": ["inMarks.fid"],
        "values": ["FAddress..."]
      }
    },
    "size": 20
  }
}
```

#### Response

Returns a list of transaction mask objects (TxMask) associated with the FID.

---

### 7. TxByIds

Query transaction details by transaction IDs.

- **URL**: `/sn2/v1/txByIds`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FC_SIGN_URL`, POST = `FC_SIGN_BODY`

#### Request

Uses the standard FCDSL request body with transaction IDs. The lookup field is `id`.

#### POST Request Body

```json
{
  "fcdsl": {
    "ids": ["txid1", "txid2", "txid3"]
  }
}
```

#### Response

Returns a list of transaction info objects matching the requested IDs.

---

## Error Codes

| Code | Name | Description |
|---|---|---|
| 0 | Success | Request completed successfully |
| 1011 | DataNotFound | Requested data does not exist |
| 1017 | MethodNotAvailable | HTTP method not supported for this endpoint |
| 2007 | CashNoFound | No valid UTXO found matching the criteria |

---

## Summary Table

| # | Endpoint | URL | Methods | Auth (GET / POST) | Description |
|---|---|---|---|---|---|
| 1 | BestBlock | `/sn2/v1/bestBlock` | GET, POST | FREE / FC_SIGN_BODY | Get the latest block |
| 2 | BlockByHeights | `/sn2/v1/blockByHeights` | GET, POST | FC_SIGN_URL / ENCRYPTED | Query blocks by height |
| 3 | BroadcastTx | `/sn18/v1/broadcastTx` | GET, POST | FREE / FC_SIGN_BODY | Broadcast a raw transaction |
| 4 | CashValid | `/sn18/v1/cashValid` | GET, POST | FREE / FC_SIGN_BODY | Query valid UTXOs |
| 5 | GetFidCid | `/sn3/v1/getFidCid` | GET | FREE / N/A | Look up FID/CID identity |
| 6 | TxByFid | `/sn2/v1/txByFid` | GET, POST | FC_SIGN_URL / FC_SIGN_BODY | Query transactions by FID |
| 7 | TxByIds | `/sn2/v1/txByIds` | GET, POST | FC_SIGN_URL / FC_SIGN_BODY | Query transactions by ID |
