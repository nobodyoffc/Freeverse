# Response to the FCH / Freeverse Security & Code Audit (Continuation Report)

**Date:** 2026-09-07
**Tree audited:** `93475c7` (branch `main`)
**Method:** every one of the 22 findings was traced to source. Where a claim named an
identifier that does not exist at HEAD, git history was searched to determine whether the
finding was once true and has since been fixed, or was never true.

---

## Summary

| Verdict | Count | Findings |
|---|---|---|
| Confirmed | 14 | 1, 3, 4, 5, 6, 8, 10, 11, 13, 14, 15, 17, 20, 22 |
| Partially confirmed | 2 | 2, 19 |
| Not reproducible at HEAD | 6 | 7, 9, 12, 16, 18, 21 |

Thank you for the continued work — findings 1, 10, 11, 14 and 15 are real and matter, and
finding 1 turns out to be materially worse than described. Three points of process feedback
before the detail:

1. **Six findings describe code that is not in the current tree.** Two were fixed in earlier
   commits, three refer to classes deleted months ago, and one names a method
   (`signRawTxFch`) that has never existed in any commit. One of the six cites
   `FchParser/src/main/java/writeEs/BlockMaker.java.backup` — a `.backup` file that is not on
   the compile path and is not part of the build. Could you confirm which revision you audited?
   If it predates `3f4124c` (2026-03-25) that would explain most of the divergence, and we can
   avoid re-treading it next round.

2. **Maven is available**, contrary to the note in your report — 3.9.14 on JDK 18 builds this
   tree. `mvn clean test` at the root works, and there are 79 existing test files in FC-JDK.
   Runtime verification is possible for the next pass.

3. **Severity on finding 3 needs adjusting** — see below. It is real but is not currently an
   exploitable bypass on our deployment configuration.

Verification also surfaced **eight defects not in your report**, listed in Section 4. One of
them — protocol rollback never executing at all — is arguably more serious than anything in the
submitted list.

---

## Section 1 — Confirmed findings

### Finding 1: FEIP rollback affected-history discovery is limited — **CONFIRMED, and understated**

The mechanism is worse than "a limited search window". The affected-history discovery queries
do not merely lack pagination — they specify **no `size` at all**, so Elasticsearch applies its
default of **10 hits**.

```java
// FEIP/FeipParser/src/main/java/personal/PersonalRollbacker.java:58
SearchResponse<BoxHistory> resultSearch = esClient.search(s->s
        .index(IndicesNames.BOX_HISTORY)
        .query(q->q
                .range(r->r
                        .field("height")
                        .gt(JsonData.of(lastHeight)))),BoxHistory.class);
```

The same shape appears in 16 discovery queries across all six rollbackers:

- `identity/IdentityRollbacker.java:75` (cid), `:163` (reputation)
- `personal/PersonalRollbacker.java:58` (box)
- `organize/OrganizationRollbacker.java:58` (square), `:145` (team)
- `construct/ConstructRollbacker.java:59` (protocol), `:157` (service), `:241` (app), `:326` (code)
- `publish/PublishRollbacker.java:56` (text), `:161` (remark), `:244` (sound), `:327` (image), `:410` (video)
- `finance/FinanceRollbacker.java:56` (proof), `:142` (token)

The only `size` anywhere in the FEIP rollback tree is `.size(0)` for an aggregation
(`IdentityRollbacker.java:233`).

Two additions to your write-up:

- **`FinanceRollbacker` has the identical defect and is not in your list.** That is the
  token and proof rollback path, i.e. the one with financial consequence.
- **The second stage is fine, but has a different bug.** `EsUtils.getHistsForReparse()`
  (`FC-JDK/src/main/java/utils/EsUtils.java:529`) *does* paginate correctly, with `search_after`
  and a stable two-field sort (`height` asc, `index` asc, `READ_MAX = 1000`). However the query
  changes between pages. Page 1 is a boolean OR over two fields:

  ```java
  // EsUtils.java:551-558
  searchBuilder.query(q -> q.bool(b -> {
      b.should(s1 -> s1.terms(t -> t.field(termsField1).terms(t1 -> t1.value(itemValueList))));
      if (termField2 != null && !termField2.isEmpty()) {
          b.should(s2 -> s2.terms(t -> t.field(termField2).terms(t1 -> t1.value(itemValueList))));
      }
      return b;
  }));
  ```

  Pages 2+ silently drop the second clause:

  ```java
  // EsUtils.java:576-580
  result = esClient.search(s -> s.index(index)
          .query(q -> q.terms(t -> t.field(termsField1).terms(t1 -> t1.value(itemValueList))))
          .size(EsUtils.READ_MAX)
          .sort(soList)
          .searchAfter(lastSort1), clazz);
  ```

  Fourteen of the fifteen call sites pass a non-null second field (`PIDS`, `TOKEN_IDS`, `BIDS`,
  `PROOF_IDS`, `SIDS`, `AIDS`, `CODE_IDS`, `TEXT_IDS`, `REMARK_IDS`, `SOUND_IDS`, `IMAGE_IDS`,
  `SQUARE_IDS`, `TIDS`); only `IdentityRollbacker.java:63` passes `null`. So histories matched
  only via the second field are lost past the first 1000. The method also returns `null` rather
  than an empty list on an empty first page (`:561`), which every caller must handle.

Your recommended fix (deterministic pagination with verification) is correct. Note it must be
applied at both stages.

---

### Finding 3: Authentication failure ignored in state-changing paths — **CONFIRMED, severity overstated**

The unchecked calls are real. `checkRequestHttp` returns `boolean`
(`FC-JDK/src/main/java/server/HttpRequestChecker.java:92`) and on failure writes an error body
but does not terminate the caller, so an ignored result means the handler proceeds and typically
writes a second response body.

State-changing sites that ignore the result:

```java
// DISK/DiskServer/src/main/java/api/Put.java:51-61
HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
httpRequestChecker.checkRequestHttp(request, response, authType);   // result discarded
DiskParams diskParams = DiskParams.fromObject(settings.getService().getParams());
if(diskParams==null)return;
dataLifeDays = Long.parseLong(diskParams.getDataLifeDays());
InputStream inputStream = request.getInputStream();
DiskManager diskHandler = (DiskManager)settings.getManager(Manager.ManagerType.DISK);
Hat hat = diskHandler.put(inputStream);                             // file written
```

`DISK/DiskServer/src/main/java/api/Carve.java:46` is the same pattern. Read-only unchecked sites
exist in DISK (`Get.java:44` and `:65`, `Check.java:37` and `:50`, `List.java:46`) and in roughly
14 APIP read endpoints.

**Two corrections:**

1. `hashAndSaveFile()` does not exist on the live path. It survives only as commented-out dead
   code at `Carve.java:65-101`. The actual write is `DiskManager.put(InputStream)`
   (`FC-JDK/src/main/java/managers/DiskManager.java:112-154`).

2. **This is not currently an exploitable bypass on our configuration.** For `FC_SIGN_URL`, an
   unsigned request already returns `true` when free APIs are permitted:

   ```java
   // HttpRequestChecker.java:140-145
   if (signInfo == null) {
       if (isForbidFreeApi) {
           replyBody.replyOtherErrorHttp("Failed to check sign.", response);
           return false;
       } else return true;
   }
   ```

   `isForbidFreeApi` comes from `Settings.FORBID_FREE_API` (`HttpRequestChecker.java:561`), and
   both settings files in `config/` have `"forbidFreeApi": false`. So on this deployment those
   endpoints accept unsigned requests **by design**, and checking the return value would change
   nothing today. It becomes a genuine bypass only where that flag is enabled.

The fix is still worth making, because the correct pattern is used consistently elsewhere —
which shows the DISK omissions are accidental rather than intentional:

```java
// APIP/ApipServer/src/main/java/SwapHall/SwapRegister.java:58-59
boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
if (!isOk) return;
```

Same in `SwapUpdate.java:63`, all 12 sites in `FcHttpRequestHandler.java`, the APIP
`Disk/{Put,Get,List,Check}.java` servlets, and DISK `Paste.java` / `Copy.java`.

---

### Finding 4: Token transfer does not enforce `transferable` — **CONFIRMED** (wrong file named)

The logic is in `finance/FinanceParser.java`, not `PublishParser.java` — the latter contains no
token or transfer code at all. The transfer branch (`FinanceParser.java:467-556`) gates only on
null and closed:

```java
case OpNames.TRANSFER:
    token = EsUtils.getById(esClient, IndicesNames.TOKEN, tokenHist.getTokenId(), Token.class);
    if(token==null || Boolean.TRUE.equals(token.getClosed())){
        log.info("Token is null or closed");
        return false;
    }
```

Every occurrence of `transferable` in the repository is a write, never a read used as a guard:
`FinanceParser.java:90-91` and `:306-307` (token, set on deploy), `:233` and `:695` (proof). The
field is persisted and then never consulted.

**The same hole exists for Proof.** `FinanceParser.java:776-793` checks destroyed, active, owner
and recipient, but never `proof.isTransferable()`.

---

### Finding 5: Duplicate recipients corrupt balances — **CONFIRMED**

Issue path:

```java
// finance/FinanceParser.java:362-380
for (TokenHistory.FidAmount issueTo : tokenHist.getIssueTo()) {
    ...
    amount += issueTo.getAmount();
    receiverAmountMapIssue.put(issueTo.getFid(), issueTo.getAmount());
    ...
}
```

Transfer path:

```java
// finance/FinanceParser.java:490-508
for (TokenHistory.FidAmount sendTo : tokenHist.getTransferTo()) {
    ...
    sum += sendTo.getAmount();
    receiverAmountMap.put(sendTo.getFid(), sendTo.getAmount());
    ...
}
```

`amount` / `sum` accumulate every entry while the map is keyed by fid, so a repeated fid retains
only the last amount. The sender is debited the full `sum` at `:515` while the credit applied at
`:456` and `:541` reflects only the final entry — the difference is destroyed. The id lists also
accumulate duplicates, which then feed `getMultiByIdList` and `bulkWriteList` at `:430`, `:461`,
`:518` and `:549`.

---

### Finding 6: Negative amounts not rejected — **CONFIRMED in substance** (wrong class named)

`SendTo` is not the class used by the FEIP token parser; that path uses
`TokenHistory.FidAmount` (`FC-JDK/src/main/java/data/feipData/TokenHistory.java:33-52`), which
also declares `amount` as `Double`.

The validation gap is real. The only checks in the transfer loop are non-null and decimal places:

```java
if(sendTo.getAmount()==null){ log.info("Transfer amount is null"); return false; }
if(isBadDecimal(token, sendTo)){ log.info("Send to amount is bad"); return false; }
```

`isBadDecimal` (`FinanceParser.java:652-660`) only compares decimal places against
`token.getDecimal()`. A negative amount therefore passes: `sum` shrinks, the balance check
`sum > senderOldBalance` at `:510` passes, and `:515` computes `senderOldBalance - sum`, which
**increases** the sender's balance while crediting the recipient a negative value.

Worth noting: `FC-JDK/src/main/java/data/fchData/SendTo.java` declares
`public static final Double MIN_AMOUNT = 0.0001;`, and that constant is referenced nowhere in
the codebase.

Your suggestion to move to integer smallest-unit accounting is sound but is a substantially
larger change; the positivity check should not wait for it.

---

### Finding 8: Rollback is not atomic and errors are not propagated — **CONFIRMED**

```java
// finance/FinanceRollbacker.java:26-32
public boolean rollback(ElasticsearchClient esClient, long lastHeight) throws Exception {
    boolean error = false;
    error = rollbackProof(esClient,lastHeight);
    error = rollbackToken(esClient,lastHeight);
    return error;
}
```

`rollbackProof`'s result is discarded. `organize/OrganizationRollbacker.java:27-34` is identical.
`identity/IdentityRollbacker.java:28-38` starts with the same overwrite and then switches idiom
mid-method to `error = error || ...`.

The problem extends past the variable handling:

- The top-level caller ignores every return value:
  ```java
  // startFEIP/FileParser.java:112-119
  cidRollbacker.rollback(esClient, lastHeight);
  constructRollbacker.rollback(esClient, lastHeight);
  personalRollbacker.rollback(esClient, lastHeight);
  publishRollbacker.rollback(esClient,lastHeight);
  organizationRollbacker.rollback(esClient, lastHeight);
  financeRollbacker.rollback(esClient, lastHeight);
  ```
- Several rollback methods return a hardcoded `false` regardless of outcome
  (`PersonalRollbacker.java:54`, `IdentityRollbacker.java:48`, `PublishRollbacker.java:125`).
- `ConstructRollbacker.rollback` short-circuits on `||` — see Section 4, item 2.

---

### Finding 10: Rollback address income uses the wrong source map — **CONFIRMED**

```java
// FchParser/src/main/java/writeEs/RollBacker.java:180-182
if(txoSumMap.get(addr)!=null) {
    updateMap.put(INCOME, stxoSumMap.get(addr));
}
...
// RollBacker.java:200-202
if(stxoSumMap.get(addr)!=null) {
    updateMap.put(EXPEND, stxoSumMap.get(addr));
}
```

Two consequences: INCOME is set to the spent total rather than the received total, so after a
rollback INCOME equals EXPEND for every affected address; and because the guard reads
`txoSumMap` while the value comes from `stxoSumMap`, an address with received-but-unspent
outputs is written a literal `null` INCOME.

Map semantics confirmed at `FC-JDK/src/main/java/utils/FchUtils.java:442-448` — `TXO_SUM` is the
sum of all TXOs, `STXO_SUM` is filtered to spent outputs (`FchUtils.java:359-367`).

**The same bug is duplicated** at `FchUtils.java:468-470`:

```java
if(txoSumMap.get(addr)!=null) {
    cid.setIncome(stxoSumMap.get(addr));
}else cid.setIncome(0L);
```

The correct form exists in the same file at `:326-327`. Both sites need the fix.

---

### Finding 11: Pagination uses an incompatible sort / `searchAfter` sequence — **CONFIRMED**

`RollBacker.readEffectedAddresses` (`RollBacker.java:120-161`), called from `rollback` at `:49`:

```java
// page 1 — sorts by OWNER
.sort(s1 -> s1.field(f -> f.field(FieldNames.OWNER).order(SortOrder.Asc)))
...
last = response.hits().hits().get(hitSize - 1).sort();   // token is an owner address

while(hitSize>=size){
    // pages 2+ — sorts by ID, but reuses the OWNER token
    .sort(s1 -> s1.field(f -> f.field(ID).order(SortOrder.Asc)))
    .searchAfter(finalLast)
```

Elasticsearch compares an owner address against the `id` field, so results are skipped or
duplicated. The consequence is that `addrList` — the set of addresses whose balances are
recomputed at `RollBacker.java:111-112` — can be incomplete, leaving stale balances after a
rollback.

For completeness: the other paginated query, `getHistsForReparse`, keeps a consistent sort and
is **not** affected by this particular defect (it has the different problem described under
Finding 1). `RollBacker.findMinMarkPositionAbove` (`:302-320`) uses a consistent two-field sort
with `size(1)` and is fine.

---

### Finding 13: Wallet transaction creation checks the wrong amount — **CONFIRMED**

```java
// FC-JDK/src/main/java/core/fch/Wallet.java:122-126
long amount = 0;
double sum = 0;
if (sendToList == null) sendToList = new ArrayList<>();
else {
    for(Cash sendTo :sendToList) sum += sendTo.getAmount();
}
...
// :175, :178
if (valueSum >= (amount + fee) && cdSum >= cd) break;
if (!(valueSum >= (amount + fee) && cdSum >= cd)) return null;
```

`amount` is assigned `0` at `:122` and never written again anywhere in the method, so both
conditions reduce to `valueSum >= fee`. The requested total is held in `sum`, which is used only
as an argument to `apipClient.cashValid` at `:139`.

The two variables are not even in the same unit — `Cash.getAmount()` returns coins
(`FC-JDK/src/main/java/data/fchData/Cash.java:565-568`) while `valueSum` and `fee` are satoshi.

Practical impact is muted on the APIP path because `cashValid(fid, sum, ...)` asks the remote to
pre-select sufficient cash. On the ES path (`:142`) and the NaSa path (`:147`) there is no such
filter and the selection loop can terminate having covered only the fee.

Reachability: local/CLI wallet only (`Wallet.sendTxByApip:90`, `Wallet.makeTxForCs:109`,
`clients/FeipClient.java:75,85,166,194`). Not a server request handler.

---

### Finding 14: `mergeCashList()` batching is incorrect — **CONFIRMED, both halves**

```java
// FC-JDK/src/main/java/core/fch/Wallet.java:291-305
public static void mergeCashList(List<Cash> cashList, byte[] priKey, ApipClient apipClient, NaSaRpcClient nasaClient) {
    Iterator<Cash> iter = cashList.iterator();
    for (int i = 0; i <= cashList.size() % 100; i++) {
        List<Cash> subCashList = new ArrayList<>();
        int j = 0;
        while (iter.hasNext()) {
            Cash cash = iter.next();
            subCashList.add(cash);
            iter.remove();
            j++;
            if (j == 100) break;
        }
        mergeCashList(subCashList, 0, priKey, apipClient, nasaClient);
    }
}
```

- `i <= cashList.size() % 100` is the wrong batch count. For `size == 200` the modulo is `0`, so
  the body runs once and 100 cashes are silently dropped. For `size == 250` it runs 51 times
  while only 3 iterations have work. The bound is also re-evaluated each iteration while
  `iter.remove()` shrinks the list.
- `:303` hard-codes `issueNum = 0`, which reaches:

```java
// Wallet.java:319
long valueForOne = sumValue / issueNum;   // ArithmeticException when issueNum == 0
```

This is an unguarded divide-by-zero for any non-empty batch with positive net value.

**This will fire in production.** `FC-JDK/src/main/java/server/reward/Rewarder.java:107-108`:

```java
if (cashList.size() > 200) {
    Wallet.mergeCashList(cashList, priKey, apipClient, naSaRpcClient);
```

The reward distribution job throws the first time a dealer FID accumulates more than 200 UTXOs.
Your suggested `for (int start = 0; start < size; start += BATCH_SIZE)` rewrite is right, and the
`issueNum` argument needs a real value.

---

### Finding 15: User balance update is not atomic — **CONFIRMED** (class renamed)

`AccountHandler` was renamed; the live code is
`FC-JDK/src/main/java/managers/AccountManager.java:949-961`:

```java
private long updateUserBalanceInRedis(String userFid, Long value, Jedis jedis) {
    String valueStr = jedis.hget(redisKeyUserBalance, userFid);
    long currentBalance = valueStr != null ? Long.parseLong(valueStr) : 0L;
    long newBalance = currentBalance + value;
    if (newBalance < -minCredit) {
        updateIncome();
        valueStr = jedis.hget(redisKeyUserBalance, userFid);
        currentBalance = valueStr != null ? Long.parseLong(valueStr) : 0L;
        newBalance = currentBalance + value;
    }
    jedis.hset(redisKeyUserBalance, userFid, String.valueOf(newBalance));
    return newBalance;
}
```

Read-modify-write across two round trips with no `WATCH`/`MULTI`, no Lua, and no `HINCRBY` —
none of those appear anywhere in `FC-JDK/src/main`. The `updateIncome()` re-read widens the race
window rather than narrowing it. The non-Redis path (`:961-971`) has the same shape and the
method is not `synchronized`.

**This is the only confirmed finding that is directly network-reachable.**
`FC-JDK/src/main/java/data/fcData/ReplyBody.java:128` calls `userSpend`, which calls
`updateUserBalance(userFid, -cost)`, on every billed API reply. Other callers:
`APIP/ApipServer/src/main/java/Mycoins/CommonApiBase.java:148`,
`managers/WebhookManager.java:250`, `managers/NewWebhookManager.java:196`,
`APIP/ApipManager/src/main/java/webhook/Pusher.java:204`. Concurrent requests from one FID lose
debits. `HINCRBY` is the right fix and is a small change.

---

### Finding 17: `readBytes()` single read without length enforcement — **CONFIRMED, but the code is dead**

Renamed to `TcpUtils`. `FC-JDK/src/main/java/utils/TcpUtils.java:8-18`:

```java
public static byte[] readBytes(DataInputStream dis) throws IOException {
    byte[] receivedBytes;
    int length = dis.readInt();
    if(length==-1){
        return null;
    }
    receivedBytes = new byte[length];
    int read = dis.read(receivedBytes);
    if(read==0)return null;
    return receivedBytes;
}
```

Every element of the claim holds: a single `read()`, a return value compared only against `0`
(so a 1-byte read returns the full allocated buffer zero-filled), no maximum length, and
`new byte[length]` allocating up to `Integer.MAX_VALUE` from a 4-byte prefix.

However, `TcpUtils` has **zero call sites** anywhere in the repository. It is orphaned code. The
appropriate action is deletion rather than hardening. The live framing code is
`fapi/message/UnifiedCodec.java` and `fapi/components/disk/DiskProtocol.java`, which were not in
scope for this response and are worth a look in the next pass.

---

### Finding 20: Personal rollback does not revert post-creation state — **CONFIRMED for 3 of 4 indices, and worse than described**

```java
// personal/PersonalRollbacker.java:24-34
rollbackBox(esClient,lastHeight);

List<String> indexList = new ArrayList<String>();
indexList.add(IndicesNames.CONTACT);
indexList.add(IndicesNames.MAIL);
indexList.add(IndicesNames.SECRET);
esClient.deleteByQuery(d->d.index(indexList)
        .conflicts(Conflicts.Proceed)
        .query(q->q.range(r->r.field("birthHeight").gt(JsonData.of(lastHeight)))));
```

For CONTACT / MAIL / SECRET this is exactly as you describe — discovery is purely
"delete what was born after". Meanwhile `PersonalParser` mutates those documents after creation
without touching `birthHeight`, e.g. contact delete/recover at `PersonalParser.java:78-127`
(same shape for mail at `:220-262`, secret at `:363-450`). A `delete` issued at a rolled-back
height against a contact created long before survives the rollback with `active=false`.

**Beyond your finding:** there is no history index to reparse from.
`startFEIP/IndicesFEIP.java:71-75` creates `CONTACT`, `MAIL` and `SECRET` with no `*_HISTORY`
counterparts, unlike `BOX`. So this state is **unrecoverable**, not merely un-reverted — the
information needed to reconstruct it was never persisted. That makes your recommended fix
("roll back all histories affecting the object") impossible for these three indices without
first adding history indices.

The claim does **not** hold for BOX: `getEffectedBoxes` (`PersonalRollbacker.java:57-110`)
queries `BOX_HISTORY` by op height and handles `CREATE` / `RECOVER` / default separately, then
reparses. That path does track post-creation ops, subject to the 10-hit limit in Finding 1.

The same birth-height-only pattern also appears at `identity/IdentityRollbacker.java:40-49`
(`rollbackNid`) and `publish/PublishRollbacker.java:119-126` (`rollbackStatement`).

---

### Finding 22: Multisignature assembly does not bind signatures to one context — **CONFIRMED**

```java
// FC-JDK/src/main/java/core/fch/TxCreator.java:1824-1856
for (String dataJson : signedData) {
    try {
        RawTxInfo multiSignData = RawTxInfo.fromJson(dataJson, RawTxInfo.class);

        if (multisig == null && multiSignData.getSenderMultisig() != null) {
            multisig = multiSignData.getSenderMultisig();
        }
        if (rawTx == null && multiSignData.getRawTx() != null && multiSignData.getRawTx().length > 0) {
            rawTx = multiSignData.getRawTx();
        }
        fidSigListMap.putAll(multiSignData.getFidSigMap());
    } catch (Exception ignored) {
    }
}
```

All four elements of the claim hold:

- `rawTx` and `multisig` are first-wins. Later blobs' values are discarded with no equality check,
  so a signer who signed a *different* transaction still contributes signatures that get assembled
  onto the first blob's raw transaction.
- `:1846` merges by FID with last-write-wins and no check that the FID is in `multisig.getFids()`.
- Input-value context is lost: `RawTxInfo.getInputs()` (carrying per-input `value` and
  `redeemScript`) is never read. The overload invoked at `:1855` is
  `buildSchnorrMultiSigTx(byte[] rawTx, ...)` at `:1274`, not the `RawTxInfo` overload at `:1307`
  which does honour per-input `redeemScript` and `lockTime`.
- `:1848` silently swallows every parse failure.

Assembly is best-effort throughout — `:1345` `String sig = sigListMap.get(fid).get(i);` sits
inside a `try { } catch (Exception ignore) { }`, so a short or missing signature list yields a
script with fewer signatures rather than an error.

Threat model: both callers read from a `BufferedReader` (`app/HomeApp.java:1141-1147`,
`app/CryptoSign.java:1420-1423`), so this is an operator pasting blobs collected out of band.
The risk is a malicious or careless co-signer, not a remote attacker.

---

## Section 2 — Partially confirmed

### Finding 2: Parsing is not crash-safe around ParseMark — **half correct**

The **history-before-mark** half is incorrect. History document and ParseMark are written in a
single bulk request:

```java
// startFEIP/FileParser.java:389-403
BulkRequest.Builder br = new BulkRequest.Builder();
if (historyIndex != null && historyId != null && historyDoc != null) {
    ... br.operations(op -> op.index(i -> i.index(idx).id(id).document(doc)));
}
br.operations(op -> op.index(i -> i.index(IndicesNames.FEIP_MARK).id(parseMark.getLastId()).document(parseMark)));
BulkResponse bulkResponse = EsRetry.bulkWithRetry(esClient, bulkRequest);
```

The **state-before-mark** half is correct. State is mutated inside the switch at
`FileParser.java:213-366`, well before `writeHistoryAndMark` is called at `:366`:

```java
case TOKEN -> {
    TokenHistory tokenHist = financeParser.makeToken(opre, feip);
    if (tokenHist == null) break;
    isValid = financeParser.parseToken(esClient, tokenHist);   // ES state mutated here
    if (isValid) { historyIndex = ...; historyDoc = tokenHist; }
}
```

Resume restores the mark and advances past one record (`startFEIP/StartFEIP.java:186-191`, then
`FileParser.java:121` `pointer += length`), so an operation whose state was written but whose
mark was not committed is reparsed. Note also that operations where `isValid == false` write no
mark at all, so a crash rewinds past every subsequent invalid operation too.

Idempotency guards are partial, which is the important part of your finding:

- **Idempotent:** history and ParseMark document ids are the txid; creation paths guard on
  existence (`FinanceParser.java:291-295` for token, `:670-674` for proof).
- **Not idempotent:** the token arithmetic, exactly as you flagged —
  `FinanceParser.java:417-425` (`circulating = token.getCirculating() + amount`),
  `:453-459` (`oldBalance + receiverAmountMapIssue.get(fid)`),
  `:515` (`senderOldBalance - sum`),
  `:538-544` (`receiverAmountMap.get(toFid) + oldBalance`).

A replay double-counts all four.

### Finding 19: `readOpReFromFile()` trusts lengths and assumes full reads — **one half already fixed**

Renamed to `OpReFileUtils`. The **full-read** half is fixed:

```java
// FC-JDK/src/main/java/core/fch/OpReFileUtils.java:17-27
private static int readFully(RandomAccessFile raf, byte[] buf, int len) throws IOException {
    int totalRead = 0;
    while (totalRead < len) {
        int bytesRead = raf.read(buf, totalRead, len - totalRead);
        if (bytesRead == -1) {
            return totalRead == 0 ? -1 : totalRead;
        }
        totalRead += bytesRead;
    }
    return totalRead;
}
```

with rewind-on-short-read at `:46-52` and `:58-64`. For the record, that hardening came from
commit `4db01c9` (2026-04-21), not from the recent torn-header fix `7063ec9`, which touched only
`BlockFileReader` and `ChainParser`.

The **length-trust** half still stands:

```java
// OpReFileUtils.java:54-56
int opLength = BytesUtils.bytesToIntBE(length);
byte[] opbytes = new byte[opLength];
```

No upper bound and no negative check. The record body is then decoded at fixed offsets with no
length validation (`:70-108`) — an `opLength` of 41 takes the `> 40` branch and throws
`ArrayIndexOutOfBoundsException` at `:88` (`copyOfRange(opbytes, 52, 86)`), and an `opLength`
under 32 throws at `:108`. The 128-byte minimum implied by the first branch is never checked.

Reachability is local file parsing only — `FEIP/FeipParser/src/main/java/startFEIP/FileParser.java:141`
and the FchParser writer path read `opreturn*.byte` files this same process wrote. Worth fixing
for robustness against a corrupt or truncated file, but it is not an untrusted-input surface.

---

## Section 3 — Not reproducible at HEAD

### Finding 7: Proof destroy writes to `IndicesNames.PROTOCOL` — **fixed previously**

`parseProof` exists only at `finance/FinanceParser.java:662`; there is no such method in
`PublishParser`. Neither `FinanceParser.java` nor `PublishParser.java` contains a single
reference to `IndicesNames.PROTOCOL`. The destroy branch writes to `PROOF`:

```java
// FinanceParser.java:845-851
br.operations(op -> op
    .index(idx -> idx
        .index(IndicesNames.PROOF)
        .id(proofItem.getId())
        .document(proofItem)
    )
);
```

The bug was real. It was introduced in `20b6497` and corrected in `513a994`:

```
-                            .index(IndicesNames.PROTOCOL)
+                            .index(IndicesNames.PROOF)
```

Your recommendation to add an integration test covering proof destruction and rollback still
stands — the fix currently has no test protecting it.

### Finding 9: BlockWriter advances chain state after partial auxiliary failure — **not reproducible**

Every auxiliary bulk is checked and throws before the main commit.
`FchParser/src/main/java/writeEs/BlockWriter.java:33-78` builds one bulk, checks it, and throws:

```java
if (response.errors()) {
    log.error("bulkWriteToEs error");
    ...
    throw new Exception("bulkWriteToEs error");
}

opReFile.writeOpReturnListIntoFile(new ArrayList<>(opReturnMap.values()));

state.addToMain(blockMask);
...
state.setBestHeight(blockMask.getHeight());
```

The per-index helpers call `checkBulkWriteErrors`, which also throws
(`BlockWriter.java:235-256`), invoked at `:100`, `:145`, `:163`, `:178`, `:199`, `:217`. The
chunking helper stops at the first failing chunk and propagates the failing response
(`EsUtils.java:479-481`). `setBestHeight` is therefore unreachable after an auxiliary failure.

One nuance in the opposite direction to your claim: auxiliary bulks execute *before* the main
bulk, so Elasticsearch can retain partially-written auxiliary data if the main bulk fails — but
chain state is not advanced in that case, so the indices do not diverge in the way described.

### Finding 12: `PersistentSequenceMap` ordering corruption — **class no longer exists**

`PersistentSequenceMap` is not in the working tree or in `git ls-files`. It lived at
`FC-SDK/src/main/java/fcData/PersistentSequenceMap.java` and was deleted in `c2e9de4`
("Account handler", 2025-04-10) along with the entire `FC-SDK` module.

Your analysis was correct for that code — it did `currentIndex = orderMap.size()` on init and
`orderMap.remove(i)` without compaction. The successor does not have the defect:

```java
// FC-JDK/src/main/java/db/LevelDB.java:1901-1904
long lastIndex = indexIdMap.isEmpty() ? 0 : indexIdMap.lastKey();
long newIndex = lastIndex + 1;
```

and `LevelDB.java:283-289` rebuilds indices by contiguous re-scan.

### Finding 16: Talk TCP framing — **module deleted**

The entire `Talk/` module, plus `server/TalkServer.java`, `TalkServerHandler.java`,
`clients/TalkClient.java`, `TalkClientHandler.java` and `utils/TalkUnitExecutor.java`, were
removed in `3f4124c` (2026-03-25). Only `server/serviceManagers/TalkManager.java` and
`data/feipData/serviceParams/TalkParams.java` remain, and both are pricing/config only —
no sockets. A repository-wide grep for `ServerSocket`, `LengthFieldBasedFrameDecoder` and
`SocketChannel` returns nothing.

The finding was accurate for the deleted code: it was Netty-based but registered no frame
decoder, and framing was hand-rolled in `TalkUnitExecutor.readAndCheck`, which silently dropped
split reads. One correction — allocation was bounded by a `readableBytes() < length` guard, so
the resource-exhaustion variant of the claim would not have held even then.

### Finding 18: `parsePkFromUnlockScript()` bounds validation — **hardened previously**

The method is now a deprecated one-line delegate:

```java
// FC-JDK/src/main/java/core/crypto/KeyTools.java:566-571
@Deprecated
public static String parsePkFromUnlockScript(String hexScript) {
    return parsePkFromScript(hexScript);
}
```

`parsePkFromScript` (`KeyTools.java:518-563`) validates: it rejects null/empty at `:519`, checks
`bScript.length >= 35` and the trailing `0xac` at `:528`, constrains `pubkeyLen` to 33 or 65 with
an exact-length check at `:531`, guards `bScript.length > sigLen + 2` at `:544` and
`bScript.length >= sigLen + 2 + pubkeyLen` at `:549`, and wraps the whole body in a catch-all at
`:555`. `Byte.toUnsignedInt` caps both lengths at 255, so the arithmetic cannot overflow.

The unguarded version you describe did exist, at `049a4e0`
(`FC-SDK/src/main/java/crypto/KeyTools.java`), and was replaced.

**Process note:** the call site cited in the report is
`FchParser/src/main/java/writeEs/BlockMaker.java.backup:759`. Files ending in `.backup` are not
on the Maven compile path and are not part of any build artifact. If the audit tooling was
grepping the working directory rather than the source set, that would also explain several of
the other stale findings — worth excluding `*.backup`, `target/` and `out/` next time.

### Finding 21: `TxCreator.signRawTxFch()` — **method does not exist**

A repository-wide grep for `signRawTxFch` returns nothing, and `git log -S"signRawTxFch" --all`
finds no commit that ever introduced it. The signing methods in `TxCreator.java` are
`signOffLineTx` (`:755`), `signTx` (`:816`, `:893`), `signRawTx` (`:910`) and
`signSchnorrMultiSigTx` (`:1181`).

`signRawTx` is the closest match and does not do what the finding describes:

```java
// TxCreator.java:910-914
public static String signRawTx(String valuesAndRawTx, byte[] priKey, MainNetParams mainnetwork) {
    Transaction transaction = parseOldCsRawTxToTx(valuesAndRawTx, mainnetwork);
    if (transaction == null) return null;
    return signTx(priKey, transaction);
}
```

`parseOldCsRawTxToTx` (`:1138-1172`) is a Gson-driven switch that appends to lists; there is no
count validation and no positional indexing, so the described `IndexOutOfBoundsException` has no
code path. Conversely, `signTx(byte[], Transaction, List<Cash>)` at `:830` handles a size
mismatch correctly, which is the opposite of the claim:

```java
Cash cashInput = (inputs != null && i < inputs.size()) ? inputs.get(i) : null;
```

---

## Section 4 — Additional defects found during verification

These were not in the submitted report. Ordered by severity.

**1. Protocol rollback has never executed.** `construct/ConstructRollbacker.java:39` reads the
key `"itemPidList"`:

```java
Map<String, ArrayList<String>> resultMap = getEffectedProtocols(esClient,lastHeight);
ArrayList<String> itemPidList = resultMap.get("itemPidList");
...
if(itemPidList==null||itemPidList.isEmpty())return error;
```

but `getEffectedProtocols` stores it under `"itemIdList"` (`:108`):

```java
resultMap.put("itemIdList", itemList);
resultMap.put("histIdList", histList);
```

The lookup always returns `null`, so the method always returns at `:42` without doing anything.
Protocol state is never rolled back on a reorganisation.

**2. `ConstructRollbacker.rollback` short-circuits.** `ConstructRollbacker.java:29-34`:

```java
return rollbackProtocol(esClient,lastHeight)
        || rollbackService(esClient,lastHeight)
        || rollbackApp(esClient,lastHeight)
        || rollbackCode(esClient,lastHeight);
```

A `true` from any stage skips every later one. Currently masked by defect 1, which guarantees
`rollbackProtocol` returns `false` — so this is latent rather than active, and fixing 1 without
fixing 2 would activate it.

**3. `RollBacker.bulkUpdateAddr` discards its zeroing update.** `RollBacker.java:183-189`:

```java
}else {
    updateMap.put(INCOME, 0);
    updateMap.put(BALANCE, 0);
    updateMap.put(EXPEND, 0);
    updateMap.put(CASH, 0);
    updateMap.put(CD, 0);
    updateMap.put(CDD, 0);
    continue;
}
```

The map is fully populated and then `continue` skips the `br.operations(...)` call at `:214-219`
that would have written it. Addresses that should be reset on rollback silently retain their
stale pre-rollback values. The bulk result at `:223` is also unchecked.

**4. `RollBacker.readEffectedAddresses` can throw on an empty result.** `:139` and `:158` call
`.get(hitSize - 1)` with no empty-hits guard, so a rollback whose range matches zero cashes
raises `IndexOutOfBoundsException`.

**5.** `getHistsForReparse` drops its second terms clause after page 1 — detailed under Finding 1.

**6.** The Finding 10 INCOME bug is duplicated at `FchUtils.java:468-470`.

**7.** No `*_HISTORY` indices exist for contact, mail or secret — detailed under Finding 20.

**8. Unreachable branch in `TxCreator.decodeTxFch`.** `:1667` does
`int flag = byteArrayInputStream.read();` and compares against `OFF_LINE_TX_START_FLAG`, declared
at `:55` as `public static final byte OFF_LINE_TX_START_FLAG = (byte) 0xFF;`. The `byte` widens
to `-1` while `read()` returns `255`, so the branch never matches real data and instead fires on
an empty array, where `read()` returns `-1`.

---

## Section 5 — Suggested remediation order

Nothing has been changed yet. Proposed sequencing, with regression tests written before the
changes in group A, since these paths mutate indexed chain state and a bad fix is worse than the
bug. There is currently no rollback test coverage at all —
`FEIP/FeipParser/src/test` contains only `HomeOpDataParsingTest`.

**Group A — chain and rollback correctness (highest value, highest risk)**
Defect 1 (`ConstructRollbacker` key mismatch) → Finding 1 (`size` and pagination across all 13
discovery queries, including `FinanceRollbacker`) → Finding 10 and defect 3 (`RollBacker` INCOME
map and discarded update) → Finding 11 and defect 4 (sort/`searchAfter` and the empty guard) →
defect 5 (`getHistsForReparse` page-2 clause) → defect 2 (short-circuit).

**Group B — token accounting**
Finding 4 (`transferable` enforcement, for both token and proof), Finding 5 (duplicate-recipient
aggregation), Finding 6 (positive-amount validation), Finding 2 (replay idempotency for the four
non-idempotent arithmetic sites). The move to integer smallest-unit accounting should be scoped
as its own piece of work rather than folded in here.

**Group C — cheap and isolated**
Finding 14 (`mergeCashList` divide-by-zero; this one is live and will throw),
Finding 15 (`HINCRBY` for the balance update — small change, and the only network-reachable
finding), Finding 13 (`Wallet.makeTx` amount), Finding 17 (delete the orphaned `TcpUtils`),
Finding 3 (`if (!isOk) return;` on the two DISK write endpoints).

**Group D — deferred / needs discussion**
Finding 20 requires adding history indices for contact, mail and secret before the rollback can
be made correct. Finding 22 and Finding 19 are local/CLI surfaces with a narrower threat model.

---

## Questions back

1. Which revision was audited? Six findings point at code removed or fixed before `93475c7`.
2. Was the audit run over the working directory rather than the Maven source set? The
   `BlockMaker.java.backup` citation suggests so; excluding `*.backup`, `target/` and `out/`
   would remove a class of false positives.
3. `signRawTxFch` (Finding 21) does not appear in any commit — was that name reconstructed from
   memory or from a different codebase?

The next pass can include runtime verification: `mvn clean test` builds this tree successfully
on Maven 3.9.14 / JDK 18.
