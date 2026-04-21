# Mycoins API Reference

The Mycoins API module provides a set of blockchain data access endpoints for FCH wallet integration, covering service information, block queries, transaction operations, UTXO management, and identity lookup.

**Base URL**: `/mycoins/v1/{endpoint}`

---

## Authentication

Each endpoint supports one or more authentication types depending on the HTTP method:

| Auth Type | Description |
|---|---|
| `FREE` | No authentication required |
| `ENCRYPTED` | Encrypted request body (AsyTwoWay ECDH encryption) |

---

## Encrypted POST Requests (AsyTwoWay)

All POST endpoints use the `ENCRYPTED` auth type. The request body is encrypted using the **AsyTwoWay** mode of the **EccK1AesGcm256** algorithm (secp256k1 ECDH + HKDF + AES-256-GCM). The response is returned as **plain JSON** (not encrypted).

### Overview

In **AsyTwoWay** mode, the client (sender) encrypts the `RequestBody` JSON using their own **private key** and the server's **public key** (the dealer's pubkey from `GetService`). The server decrypts using its own private key and the client's public key (extracted from `pubkeyA` in the ciphertext). Both sides derive the same symmetric key via ECDH.

### Encryption Pipeline

```
1. ECDH:     Z = ECDH(clientPrikey, serverPubkey)                   -> 32-byte shared secret
2. HKDF:     symkey = HKDF(ikm=Z, salt=nonce, info="hkdf", L=32)   -> 32-byte AES key
3. AES-GCM:  cipher = AES-256-GCM(key=symkey, iv=nonce, plaintext)  -> ciphertext + 16-byte tag
```

- **Curve**: secp256k1, compressed 33-byte public keys
- **HKDF**: HMAC-SHA512 extract/expand (details in the HKDF Specification section below)
- **Cipher**: AES-256-GCM, 12-byte IV/nonce, 128-bit authentication tag (no separate `sum` field)

### Step-by-Step Guide

#### Step 1: Get the server's public key

Call `GET /mycoins/v1/getService` to obtain the service info. The `dealer` field is the server's FID, and `dealerPubkey` is the server's compressed secp256k1 public key (33 bytes, hex).

#### Step 2: Build the plaintext RequestBody

The plaintext is the JSON serialization of a `RequestBody` object:

```json
{
  "url": "/mycoins/v1/cashByIds",
  "time": 1712900000000,
  "nonce": 123456789,
  "via": "FClientAddress...",
  "fcdsl": {
    "ids": ["cashId1", "cashId2"]
  }
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `url` | String | Yes | The API endpoint path being called |
| `time` | Long | Yes | Current timestamp in milliseconds |
| `nonce` | Integer | Yes | Random integer to prevent replay attacks |
| `via` | String | No | Client's FID (for referral tracking) |
| `fcdsl` | Object | Yes | The FCDSL query (same structure as shown in each endpoint) |

#### Step 3: Encrypt the RequestBody

1. Generate a **random 12-byte nonce** (CSPRNG).
2. Compute the ECDH shared secret: `Z = ECDH(clientPrikey, serverPubkey)` (32 bytes, see the ECDH Shared Secret Encoding section below).
3. Derive symmetric key: `symkey = HKDF(ikm=Z, salt=nonce, info=ASCII("hkdf"), L=32)`.
4. Encrypt: `cipherBytes = AES-256-GCM-Encrypt(key=symkey, iv=nonce, plaintext=requestBodyJsonBytes)`. The output is `body || 16-byte-tag`.
5. Build the encrypted JSON envelope (see below).

#### Step 4: Send the encrypted envelope as POST body

The HTTP POST body is the JSON-serialized encrypted envelope:

```json
{
  "type": "AsyTwoWay",
  "alg": "EccK1AesGcm256@No1_NrC7",
  "cipher": "<Base64-encoded ciphertext + tag>",
  "iv": "<12-byte nonce, hex-encoded>",
  "pubkeyA": "<client's compressed pubkey, 33 bytes hex>"
}
```

| Field | Type | Description |
|---|---|---|
| `type` | String | Always `"AsyTwoWay"` |
| `alg` | String | Always `"EccK1AesGcm256@No1_NrC7"` |
| `cipher` | String | Base64-encoded ciphertext (plaintext encrypted + 16-byte GCM auth tag) |
| `iv` | String | Hex-encoded 12-byte random nonce |
| `pubkeyA` | String | Hex-encoded 33-byte compressed secp256k1 public key of the client (sender) |

**Note**: Do NOT include `sum`, `symkey`, `prikeyA`, `prikeyB`, or `password` in the JSON.

#### Step 5: Parse the response

The server responds with **plain JSON** (not encrypted):

```json
{
  "code": 0,
  "message": "Success",
  "nonce": 123456789,
  "total": 2,
  "got": 2,
  "bestHeight": 850000,
  "data": [...]
}
```

### Decryption (server-side, for reference)

The server:
1. Parses the POST body as encrypted JSON envelope.
2. Extracts `pubkeyA` (client's public key) from the envelope.
3. Derives the same shared secret: `Z = ECDH(serverPrikey, pubkeyA)`.
4. Derives the same symmetric key: `symkey = HKDF(Z, nonce, "hkdf", 32)`.
5. Decrypts: `plaintext = AES-256-GCM-Decrypt(symkey, nonce, cipher)`.
6. Parses the decrypted bytes as `RequestBody` JSON and processes the request.

---

## Cryptographic Specifications

### ECDH Shared Secret Encoding (secp256k1)

The shared secret `Z` is a 32-byte value derived from the ECDH key agreement on the secp256k1 curve.

**Inputs**

- **`priKeyBytes`:** **32** bytes, big-endian unsigned integer **d** (private scalar), in range **[1, n-1]** for subgroup order **n**.
- **`pubKeyBytes`:** **33** bytes, **SEC1 compressed** public key: **`0x02` or `0x03`** prefix + **32-byte** **X** coordinate.

**Steps**

1. Decode **`priKeyBytes`** and **`pubKeyBytes`** into EC domain parameters for **secp256k1**.
2. Compute **`agreement = ECDHBasicAgreement.calculateAgreement(peerPublic)`** -> **`BigInteger Z`** (the **x**-coordinate field element of **`d * Q`** after normalization).
3. **`zBytes = Z.toByteArray()`** (signed big-endian; may be **33** bytes with leading **`0x00`** if the top bit would otherwise imply a negative number).
4. **Right-align** into a fixed **32-byte** array **`secret`**:
   - `srcPos = 1` if **`zBytes.length == 33`** and **`zBytes[0] == 0`**, else **`srcPos = 0`**.
   - `length = min(zBytes.length - srcPos, 32)`.
   - `destPos = 32 - length`.
   - Copy `zBytes[srcPos .. srcPos+length]` into `secret[destPos .. destPos+length]`; **`secret`** was zero-initialized so unused leading bytes remain **0x00**.

**Output:** **`secret`**, always **32** bytes.

**Interoperability:** Any library that produces a **different** 32-byte encoding for the same **`(d, Q)`** will derive a **different** HKDF key and **will not** decrypt the ciphertext.

### HKDF Specification (HMAC-SHA512)

This is **RFC 5869** (Extract then Expand) implemented with **HMAC-SHA512** (not SHA256). The pseudorandom key **PRK** is **64** bytes.

#### HKDF-Extract

**Inputs:** `salt` (optional), `ikm` (input keying material).

1. If `salt` is **null** or **length 0**, set `effectiveSalt` to a **64-byte** array of **0x00**.
2. `PRK = HMAC-SHA512(key = effectiveSalt, data = ikm)`.

**Output:** `PRK`, **64** bytes.

#### HKDF-Expand

**Inputs:** `prk` (**64** bytes), `info` (optional byte string, may be **null**), `L` (desired output length in bytes).

**Constraints:** `L` must satisfy **1 <= L <= 255 * 64** (**16320**).

1. `N = ceil(L / 64)`.
2. Initialize `previousT` as empty byte array.
3. For `i = 1` to `N`:
   - `T(i) = HMAC-SHA512(key = prk, data = previousT || info || byte(i))`
     where **`byte(i)`** is the single byte with value **`i`** (0x01, 0x02, ...), and **`||`** is concatenation. If `info` is **null**, do not append any info bytes (equivalent to empty).
   - Copy the first **`min(64, L - copied)`** bytes of `T(i)` into the output buffer.
   - Set `previousT = T(i)` (full **64** bytes) for the next iteration.
4. Return the first **`L`** bytes as **OKM** (output keying material).

#### HKDF (one-shot)

```
function hkdf(ikm, salt, info, L):
    PRK = Extract(salt, ikm)
    OKM = Expand(PRK, info, L)
    return OKM
```

#### Pseudocode

```
function hmac_sha512(key, data):
    return HMAC-SHA512(key, data)

function extract(salt, ikm):
    if salt is null or length(salt) == 0:
        salt = 64 bytes of 0x00
    return hmac_sha512(salt, ikm)   // 64-byte PRK

function expand(prk, info, L):
    if L < 1 or L > 255 * 64: error
    N = ceil(L / 64)
    OKM = empty
    previousT = empty byte sequence
    for i from 1 to N inclusive:
        buf = previousT
        if info is not null:
            buf = buf || info
        buf = buf || (single byte with value i)
        T = hmac_sha512(prk, buf)     // always 64 bytes
        append first min(64, L - length(OKM)) bytes of T to OKM
        previousT = T                 // full 64 bytes for next i
    return OKM

function hkdf(ikm, salt, info, L):
    return expand(extract(salt, ikm), info, L)
```

#### HKDF Test Vectors

All values are hex (lowercase). Implementations must reproduce `OKM` exactly.

**TV1** -- `hkdf`, zero IKM, 12-byte zero salt, `info = ASCII "hkdf"`, L = 32

| Field | Value |
|---|---|
| `ikm` | `0000000000000000000000000000000000000000000000000000000000000000` (32 bytes) |
| `salt` | `000000000000000000000000` (12 bytes) |
| `info` | ASCII bytes `68 6b 64 66` (`"hkdf"`) |
| `L` | 32 |
| **`OKM` (32 bytes)** | `79d55d067d55fd67266b49e13949f6ea3fec4e752bbaabe0c52ddc7ac7c02a64` |

**TV2** -- `extract(null, ikm)` with zero 32-byte ikm

| **`PRK` (64 bytes)** | `bae46cebebbb90409abc5acf7ac21fdb339c01ce15192c52fb9e8aa11a8de9a4ea15a045f2be245fbb98916a9ae81b353e33b9c42a55380c5158241daeb3c6dd` |

**TV3** -- `hkdf`, `ikm[i] = i` for `i = 1..32`, 12-byte zero salt, `info = "hkdf"`, L = 32

| `ikm` | `0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20` |
| **`OKM` (32 bytes)** | `a90aad642250bb8562417ac75dc4ca02d7b1f0d9533d14ab5a5a122939a69421` |

### AES-256-GCM Specification

| Parameter | Value |
|---|---|
| Key | **32** bytes (AES-256) |
| IV / nonce | **12** bytes (96-bit GCM nonce) |
| Cipher | AES, **GCM**, no padding |
| Transform | `AES/GCM/NoPadding` |
| Auth tag | **128** bits (16 bytes), appended to ciphertext |
| AAD | Empty (no additional authenticated data) |

#### Encrypt

1. `key.length` must be **32**.
2. Build `GCMParameterSpec(128, iv)` and initialize cipher in `ENCRYPT_MODE` with `SecretKeySpec(key, "AES")`.
3. No AAD is used (empty).
4. Encrypt the plaintext; output is **encrypted body || 16-byte tag**.

#### Decrypt

1. `key` must be non-null, length **32**; `iv` must be non-null, length **12**.
2. Initialize cipher in `DECRYPT_MODE` with the same key and `GCMParameterSpec(128, iv)`; no AAD.
3. Decrypt. On GCM tag mismatch, decryption fails (authentication error).

#### Pseudocode

```
function aes_gcm256_encrypt(key32, iv12, plaintext):
    assert length(key32) == 32 and length(iv12) == 12
    // AES/GCM/NoPadding, tag length 128 bits, AAD empty
    (ciphertext, tag16) = AES-GCM-Enc(key32, iv12, plaintext, aad = empty)
    return ciphertext || tag16

function aes_gcm256_decrypt(key32, iv12, cipher_with_tag):
    assert length(key32) == 32 and length(iv12) == 12
    assert length(cipher_with_tag) >= 16
    return AES-GCM-Dec(key32, iv12, cipher_with_tag, aad = empty)
```

### Full Composition (normative)

```
symkey  = hkdf( ecdh(priKey, pubKey), salt = nonce12, info = "hkdf", L = 32 )
cipher  = aes_gcm256_encrypt(symkey, iv = nonce12, plaintext)
plain   = aes_gcm256_decrypt(symkey, iv = nonce12, cipher)
```

---

## Worked Example with Test Keys

Use these shared test keys to verify your implementation.

### Test Keys

| Role | Item | Value |
|---|---|---|
| Client (fidA) | FID | `FEk41Kqjar45fLDriztUDTUkdki7mmcjWK` |
| Client (fidA) | Compressed pubkey (hex) | `030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a` |
| Client (fidA) | Private key (WIF) | `L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8` |
| Client (fidA) | Private key (hex) | `a048f6c843f92bfe036057f7fc2bf2c27353c624cf7ad97e98ed41432f700575` |
| Server (fidB) | FID | `F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW` |
| Server (fidB) | Compressed pubkey (hex) | `02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67` |
| Server (fidB) | Private key (WIF) | `L5DDxf3PkFwi1jArqYokpTsntthLvhDYg44FXyTSgdTx3XEFR1iB` |
| Server (fidB) | Private key (hex) | `ee72e6dd4047ef7f4c9886059cbab42eaab08afe7799cbc0539269ee7e2ec30c` |

### ECDH Test Vector

| Field | Hex |
|---|---|
| `priKey` (32 bytes) | `0000000000000000000000000000000000000000000000000000000000000001` |
| `pubKey` (33 bytes, compressed generator G) | `0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798` |
| **`Z` (32 bytes)** | `79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798` |

### Full Pipeline Test Vector

Use `Z` from the ECDH test vector above. Nonce `N` (used as both HKDF salt and AES-GCM IV):

| `N` (12 bytes) | `101112131415161718191a1b` |

**HKDF**: `ikm = Z`, `salt = N`, `info = ASCII "hkdf"`, `L = 32`

| **`symkey` (32 bytes)** | `2a2768b8c286dbed4a5c7299d49b9a8aaaedbd7c250862fa8dc6f1b4b56ceb8c` |

**AES-GCM**: `key = symkey`, `iv = N`, plaintext = UTF-8 `a` (single byte `61`), empty AAD

| **`cipher` (17 bytes = 1 + 16 tag)** | `fad929f0256cda6e921589b0b2c542a3fb` |

**Decrypt check:** From `symkey`, `N`, and `cipher`, plaintext must be `61` (the byte for ASCII `a`).

### AsyTwoWay Test Vector (Hello world!)

Using the test keys above with fixed IV `000102030405060708090a0b` (12 bytes):

**Encryption** (client fidA encrypts to server fidB):

- Plaintext: UTF-8 `Hello world!` (12 bytes)
- `Z = ECDH(fidA_prikey, fidB_pubkey)` -> 32-byte shared secret
- `symkey = HKDF(Z, salt=000102030405060708090a0b, info="hkdf", L=32)` -> 32-byte key
- `cipher = AES-256-GCM(symkey, iv=000102030405060708090a0b, plaintext)` -> ciphertext + tag

**Expected ciphertext** (Base64): `15g2ijHqF+CWJfWXOYLlmn+AjHnT7mkVMVcWTg==`

**The encrypted POST body:**

```json
{
  "type": "AsyTwoWay",
  "alg": "EccK1AesGcm256@No1_NrC7",
  "cipher": "15g2ijHqF+CWJfWXOYLlmn+AjHnT7mkVMVcWTg==",
  "iv": "000102030405060708090a0b",
  "pubkeyA": "030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a"
}
```

**Verification** (server fidB decrypts):

1. `Z' = ECDH(fidB_prikey, fidA_pubkey)` -> same 32-byte shared secret (because `ECDH(a, B) == ECDH(b, A)` on secp256k1)
2. `symkey' = HKDF(Z', same salt, same info, 32)` -> same key
3. `plaintext = AES-256-GCM-Decrypt(symkey', same iv, cipher)` -> `Hello world!`

### How to verify your implementation

1. Use the test keys above with the fixed IV `000102030405060708090a0b`.
2. Encrypt `Hello world!` (UTF-8) with `fidA_prikey` and `fidB_pubkey`.
3. Your ciphertext (Base64) should be: `15g2ijHqF+CWJfWXOYLlmn+AjHnT7mkVMVcWTg==`
4. Decrypt with `fidB_prikey` and `fidA_pubkey` (from `pubkeyA`). Result should be `Hello world!`.
5. Once encryption/decryption matches the test vector, replace the plaintext with your actual `RequestBody` JSON and use a random nonce instead of the fixed one.

### Security Notes

- **Always use a fresh random 12-byte nonce** for each request. Nonce reuse with the same key pair breaks GCM confidentiality and integrity.
- **Never include private keys** (`prikeyA`, `prikeyB`, `symkey`) in the JSON envelope.
- The GCM authentication tag (128-bit) provides both integrity and authenticity -- no separate `sum` field is needed.
- The HKDF implementation uses **HMAC-SHA512** (not SHA256). Using SHA256 will produce different keys and fail.
- Use the reference's public-key parsing to avoid invalid-point issues on secp256k1.
- HKDF **salt** is tied to **IV** -- the same nonce must be stored/transmitted with the ciphertext; do not substitute a different salt for HKDF while reusing another IV for GCM.

---

## API Endpoints

### 1. GetService

Get the current service information of the Mycoins API provider.

- **URL**: `/mycoins/v1/getService`
- **Methods**: `GET` only (POST returns `1017 MethodNotAvailable`)
- **Auth**: `FREE`

#### GET Request

No parameters required.

```
GET /mycoins/v1/getService
```

#### Response

Returns a `Service` object describing the current API service:

```json
{
  "code": 0,
  "message": "Success",
  "data": {
    "id": "service_id",
    "stdName": "Mycoins",
    "type": "APIP",
    "owner": "FAddress...",
    "desc": "...",
    "ver": "1",
    "active": true,
    "home": {
      "api": "https://..."
    },
    "birthHeight": 800000,
    "lastHeight": 850000,
    "tRate": 0.95,
    ...
  }
}
```

---

### 2. BestBlock

Get the current best (latest) block on the Freecash blockchain.

- **URL**: `/mycoins/v1/bestBlock`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FREE`, POST = `ENCRYPTED`

#### GET Request

No parameters required.

```
GET /mycoins/v1/bestBlock
```

#### POST Request

Standard APIP encrypted request body.

#### Response

Returns a `Block` object with the latest block data:

```json
{
  "code": 0,
  "message": "Success",
  "bestHeight": 850000,
  "bestBlockId": "000000000000000000...",
  "data": {
    "id": "000000000000000000...",
    "height": 850000,
    "version": "...",
    "preId": "...",
    "merkleRoot": "...",
    "time": 1700000000,
    "bits": 123456,
    "nonce": 789012,
    "txCount": 5,
    "inValueT": 500000000,
    "outValueT": 500000000,
    "fee": 10000,
    "cdd": 1000
  }
}
```

---

### 3. BlockByHeights

Query block information by block heights.

- **URL**: `/mycoins/v1/blockByHeights`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FREE`, POST = `ENCRYPTED`

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

No authentication required.

```
GET /mycoins/v1/blockByHeights?ids=850000,850001
```

#### Response

Returns a list of `Block` objects matching the requested heights.

```json
{
  "code": 0,
  "message": "Success",
  "data": [
    {
      "id": "000000000000000000...",
      "height": 850000,
      "time": 1700000000,
      "txCount": 5,
      ...
    }
  ]
}
```

---

### 4. TxByIds

Query transaction details by transaction IDs.

- **URL**: `/mycoins/v1/txByIds`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FREE`, POST = `ENCRYPTED`

#### POST Request

Uses the standard FCDSL request body with transaction IDs. The lookup field is `id`.

```json
{
  "fcdsl": {
    "ids": ["txid1", "txid2", "txid3"]
  }
}
```

#### GET Request

No authentication required.

```
GET /mycoins/v1/txByIds?ids=txid1,txid2
```

#### Response

Returns a list of `Tx` objects matching the requested IDs:

```json
{
  "code": 0,
  "message": "Success",
  "data": [
    {
      "id": "txid...",
      "version": 2,
      "height": 850000,
      "blockId": "...",
      "blockTime": 1700000000,
      "txIndex": 1,
      "inCount": 1,
      "outCount": 2,
      "inValueT": 100000000,
      "outValueT": 99990000,
      "fee": 10000,
      "cdd": 500,
      "opReBrief": "...",
      "spentCashes": [...],
      "issuedCashes": [...]
    }
  ]
}
```

---

### 5. TxSearch

Search transactions using FCDSL query language.

- **URL**: `/mycoins/v1/txSearch`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FREE`, POST = `ENCRYPTED`

#### POST Request

Uses FCDSL query for flexible transaction searching. Supports filter, query, sort, and pagination.

```json
{
  "fcdsl": {
    "query": {
      "terms": {
        "fields": ["height"],
        "values": ["850000"]
      }
    },
    "sort": [
      {"field": "txIndex", "order": "asc"}
    ],
    "size": 20
  }
}
```

#### Response

Returns a list of `Tx` objects matching the search criteria.

---

### 6. FreerByIds

Query Freer (Freecash identity / CID) information by FIDs.

- **URL**: `/mycoins/v1/freerByIds`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FREE`, POST = `ENCRYPTED`

#### POST Request

```json
{
  "fcdsl": {
    "ids": ["FAddress1", "FAddress2"]
  }
}
```

#### GET Request

```
GET /mycoins/v1/freerByIds?ids=FAddress1,FAddress2
```

#### Response

Returns a list of `Freer` objects:

```json
{
  "code": 0,
  "message": "Success",
  "data": [
    {
      "id": "FAddress...",
      "cid": "user_cid",
      "balance": 100000000,
      "cash": 5,
      "income": 500000000,
      "expend": 400000000,
      "cd": 1000,
      "cdd": 500,
      "reputation": 100,
      "hot": 50,
      "weight": 200,
      "master": "...",
      "guide": "...",
      "btcAddr": "...",
      "ethAddr": "...",
      "birthHeight": 100000,
      "lastHeight": 850000
    }
  ]
}
```

---

### 7. FreerSearch

Search Freer (identity) information using FCDSL query language.

- **URL**: `/mycoins/v1/freerSearch`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FREE`, POST = `ENCRYPTED`
- **Default Sort**: `lastHeight` descending, `id` ascending

#### POST Request

```json
{
  "fcdsl": {
    "query": {
      "match": {
        "fields": ["cid"],
        "value": "username"
      }
    },
    "size": 20
  }
}
```

#### Response

Returns a list of `Freer` objects matching the search criteria.

---

### 8. CashByIds

Query UTXO (Cash) details by cash IDs.

- **URL**: `/mycoins/v1/cashByIds`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FREE`, POST = `ENCRYPTED`

#### POST Request

```json
{
  "fcdsl": {
    "ids": ["cashId1", "cashId2"]
  }
}
```

#### GET Request

```
GET /mycoins/v1/cashByIds?ids=cashId1,cashId2
```

#### Response

Returns a list of `Cash` objects:

```json
{
  "code": 0,
  "message": "Success",
  "data": [
    {
      "id": "cashId...",
      "issuer": "FAddress...",
      "birthIndex": 0,
      "type": "P2PKH",
      "owner": "FAddress...",
      "value": 100000000,
      "lockScript": "...",
      "birthTxId": "txid...",
      "birthTxIndex": 0,
      "birthBlockId": "...",
      "birthTime": 1700000000,
      "birthHeight": 850000,
      "valid": true,
      "cd": 500,
      "cdd": 0,
      "lastTime": 1700000000,
      "lastHeight": 850000
    }
  ]
}
```

---

### 9. CashValid

Query valid (unspent) UTXOs for a given address, with optional filtering by amount, coindays, message size, output size, and since-height.

- **URL**: `/mycoins/v1/cashValid`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FREE`, POST = `ENCRYPTED`

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
GET /mycoins/v1/cashValid?fid=FAddress...&amount=1.5&cd=100
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
      "id": "cashId...",
      "owner": "FAddress...",
      "value": 150000000,
      "valid": true,
      "birthTxId": "...",
      "birthHeight": 800000,
      ...
    }
  ]
}
```

If no valid cash is found, returns error code `2007` (CashNoFound).

---

### 10. BroadcastTx

Broadcast a raw transaction to the Freecash network.

- **URL**: `/mycoins/v1/broadcastTx`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FREE`, POST = `ENCRYPTED`

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
GET /mycoins/v1/broadcastTx?rawTx=0200000001...
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

### 11. ServiceByIds

Query service details by service IDs.

- **URL**: `/mycoins/v1/serviceByIds`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FREE`, POST = `ENCRYPTED`

#### POST Request

```json
{
  "fcdsl": {
    "ids": ["serviceId1", "serviceId2"]
  }
}
```

#### GET Request

```
GET /mycoins/v1/serviceByIds?ids=serviceId1,serviceId2
```

#### Response

Returns a list of `Service` objects:

```json
{
  "code": 0,
  "message": "Success",
  "data": [
    {
      "id": "serviceId...",
      "stdName": "ServiceName",
      "type": "APIP",
      "owner": "FAddress...",
      "desc": "...",
      "ver": "1",
      "active": true,
      "closed": false,
      "home": {"api": "https://..."},
      "dealer": "...",
      "waiters": ["..."],
      "protocols": ["..."],
      "birthHeight": 800000,
      "lastHeight": 850000,
      "tCdd": 5000,
      "tRate": 0.95
    }
  ]
}
```

---

### 12. ServiceSearch

Search services using FCDSL query language.

- **URL**: `/mycoins/v1/serviceSearch`
- **Methods**: `GET`, `POST`
- **Auth**: GET = `FREE`, POST = `ENCRYPTED`
- **Default Sort**: `active` descending, `tRate` descending, `id` ascending

#### POST Request

```json
{
  "fcdsl": {
    "query": {
      "match": {
        "fields": ["stdName"],
        "value": "swap"
      }
    },
    "size": 20
  }
}
```

#### GET Request

No authentication required.

#### Response

Returns a list of `Service` objects matching the search criteria.

---

## Data Models

### Block

| Field | Type | Description |
|---|---|---|
| `id` | String | Block hash |
| `size` | Long | Block size in bytes |
| `height` | Long | Block height |
| `version` | String | Block version |
| `preId` | String | Previous block hash |
| `merkleRoot` | String | Merkle tree root |
| `time` | Long | Block timestamp (Unix) |
| `bits` | Long | Difficulty target |
| `nonce` | Long | Nonce |
| `txCount` | Integer | Number of transactions |
| `inValueT` | Long | Total input value (satoshi) |
| `outValueT` | Long | Total output value (satoshi) |
| `fee` | Long | Total fees (satoshi) |
| `cdd` | Long | CoinDays destroyed |

### Tx

| Field | Type | Description |
|---|---|---|
| `id` | String | Transaction hash |
| `version` | Integer | Transaction version |
| `lockTime` | Long | Lock time |
| `blockTime` | Long | Block timestamp |
| `blockId` | String | Block hash |
| `txIndex` | Integer | Index in the block |
| `height` | Long | Block height |
| `inCount` | Integer | Number of inputs |
| `outCount` | Integer | Number of outputs |
| `inValueT` | Long | Total input value (satoshi) |
| `outValueT` | Long | Total output value (satoshi) |
| `fee` | Long | Transaction fee (satoshi) |
| `cdd` | Long | CoinDays destroyed |
| `opReBrief` | String | OP_RETURN data brief (first 30 bytes) |
| `coinbase` | String | Coinbase script (if coinbase tx) |

### Cash (UTXO)

| Field | Type | Description |
|---|---|---|
| `id` | String | Cash ID (derived from txId:vout) |
| `issuer` | String | First input FID when this cash was born |
| `birthIndex` | Integer | Output index in the birth transaction |
| `type` | String | Script type: P2PKH, P2SH, OP_RETURN, etc. |
| `owner` | String | Owner address (FID) |
| `value` | Long | Value in satoshi |
| `lockScript` | String | Lock script hex |
| `birthTxId` | String | Birth transaction hash |
| `birthTxIndex` | Integer | Birth tx index in block |
| `birthBlockId` | String | Birth block hash |
| `birthTime` | Long | Birth block timestamp |
| `birthHeight` | Long | Birth block height |
| `spendTime` | Long | Spend block timestamp (null if unspent) |
| `spendTxId` | String | Spend transaction hash (null if unspent) |
| `spendHeight` | Long | Spend block height (null if unspent) |
| `valid` | Boolean | true = unspent (UTXO), false = spent (STXO) |
| `cd` | Long | CoinDays |
| `cdd` | Long | CoinDays destroyed (when spent) |
| `lastTime` | Long | Last update timestamp |
| `lastHeight` | Long | Last update height |

### Freer (Identity)

| Field | Type | Description |
|---|---|---|
| `id` | String | FID (Freecash address) |
| `cid` | String | Crypto ID (human-readable name) |
| `balance` | Long | Balance in satoshi |
| `cash` | Long | Number of UTXOs |
| `income` | Long | Total received (satoshi) |
| `expend` | Long | Total spent (satoshi) |
| `cd` | Long | CoinDays |
| `cdd` | Long | CoinDays destroyed |
| `reputation` | Long | Reputation score |
| `hot` | Long | Hot score |
| `weight` | Long | Weight score |
| `master` | String | Master address |
| `guide` | String | Guide address (first sender) |
| `btcAddr` | String | BTC address |
| `ethAddr` | String | ETH address |
| `ltcAddr` | String | LTC address |
| `dogeAddr` | String | DOGE address |
| `trxAddr` | String | TRX address |
| `birthHeight` | Long | First activity height |
| `lastHeight` | Long | Last activity height |

### Service

| Field | Type | Description |
|---|---|---|
| `id` | String | Service ID |
| `stdName` | String | Standard name |
| `localNames` | Map | Localized names |
| `desc` | String | Description |
| `type` | String | Service type (e.g., APIP, DISK, TALK) |
| `ver` | String | Version |
| `owner` | String | Owner FID |
| `dealer` | String | Dealer FID |
| `home` | Map | URLs (api, org, doc) |
| `waiters` | List | Waiter FIDs |
| `protocols` | List | Protocol IDs |
| `codes` | List | Code IDs |
| `active` | Boolean | Is active |
| `closed` | Boolean | Is closed |
| `birthHeight` | Long | Birth block height |
| `lastHeight` | Long | Last update height |
| `tCdd` | Long | Total CoinDays destroyed |
| `tRate` | Float | Trust rating |

---

## Error Codes

| Code | Name | Description |
|---|---|---|
| 0 | Success | Request completed successfully |
| 1003 | BodyMissed | Request body is missing |
| 1009 | SessionTimeExpired | Session has expired |
| 1011 | DataNotFound | Requested data does not exist |
| 1013 | BadRequest | Failed to parse request body |
| 1017 | MethodNotAvailable | HTTP method not supported for this endpoint |
| 1029 | FailedToDecrypt | Decryption of the request body failed |
| 2007 | CashNoFound | No valid UTXO found matching the criteria |

---

## Summary Table

| # | Endpoint | URL | Methods | Auth (GET / POST) | Description |
|---|---|---|---|---|---|
| 1 | GetService | `/mycoins/v1/getService` | GET | FREE / N/A | Get service information |
| 2 | BestBlock | `/mycoins/v1/bestBlock` | GET, POST | FREE / ENCRYPTED | Get the latest block |
| 3 | BlockByHeights | `/mycoins/v1/blockByHeights` | GET, POST | FREE / ENCRYPTED | Query blocks by height |
| 4 | TxByIds | `/mycoins/v1/txByIds` | GET, POST | FREE / ENCRYPTED | Query transactions by ID |
| 5 | TxSearch | `/mycoins/v1/txSearch` | GET, POST | FREE / ENCRYPTED | Search transactions |
| 6 | FreerByIds | `/mycoins/v1/freerByIds` | GET, POST | FREE / ENCRYPTED | Query identities by FID |
| 7 | FreerSearch | `/mycoins/v1/freerSearch` | GET, POST | FREE / ENCRYPTED | Search identities |
| 8 | CashByIds | `/mycoins/v1/cashByIds` | GET, POST | FREE / ENCRYPTED | Query UTXOs by ID |
| 9 | CashValid | `/mycoins/v1/cashValid` | GET, POST | FREE / ENCRYPTED | Query valid UTXOs |
| 10 | BroadcastTx | `/mycoins/v1/broadcastTx` | GET, POST | FREE / ENCRYPTED | Broadcast a raw transaction |
| 11 | ServiceByIds | `/mycoins/v1/serviceByIds` | GET, POST | FREE / ENCRYPTED | Query services by ID |
| 12 | ServiceSearch | `/mycoins/v1/serviceSearch` | GET, POST | FREE / ENCRYPTED | Search services |
