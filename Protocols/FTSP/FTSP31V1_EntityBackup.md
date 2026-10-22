# FTSP31V1_EntityBackup

## Contents

[Summary](#summary)

[Abstract](#abstract)

[Motivation](#motivation)

[Specification](#specification)

[Test Vectors](#test-vectors)

[Security Considerations](#security-considerations)

[Versioning](#versioning)

[Related Protocols](#related-protocols)

[Reference Implementation](#reference-implementation)

---

## Summary

|Field|Content|
|---|---|
|Title|EntityBackup|
|Type|FTSP|
|SN|31|
|Ver|1|
|Category|Encoding|
|Status|Draft|
|Author|No1_NrC7|
|Created|2026-10-02|
|PID||

Parent rules: [FTSP0V1_FTSP](FTSP0V1_FTSP.md)

## Abstract

An **entity backup** is the text a wallet exports so that keys (`KeyInfo`) or other entities, such as `Secret`, can be moved to another app or device: an optional `BackupKey`, an optional `BackupHeader`, then the items, each a JSON object. This document fixes the layout, the three protection modes (plain, app password, random password), how a reader tells the objects apart, and what a reader must do with a key before saving it.

## Motivation

Safe and Freer exchange keys and secrets through this format, and FreerForMac and SafeForMac are expected to. It was never written down, so each reader grew its own guesses. In 2026-10 a plain key list exported from Safe was imported into Freer with each key still held as a plain `prikey` and no `prikeyCipher`: the private key sat in Freer's configuration in the clear, showed on the key detail page, and the identity could not sign, so it never connected to a server. A password-encrypted list lost every key's public key, and a wrong password saved keys that the device could never open. The rules below are the ones that make the three modes import correctly.

## Specification

### Objects and order

A backup is a sequence of JSON objects (RFC 8259). Writers separate consecutive objects with one blank line (`"\n\n"`); readers MUST accept any whitespace between objects and MUST NOT depend on the order of fields inside an object.

|Position|Object|Present when|
|---|---|---|
|1|`BackupKey`|the items are encrypted|
|2|`BackupHeader`|the items are encrypted; for entities other than keys, always|
|3…|items|always, one object per entity|

A backup carries one kind of entity. Its simple class name is `tClass` in the header (`KeyInfo`, `Secret`); without a header the reader knows the kind from where the user imported it.

### Modes

|Mode|`BackupKey`|Key items|Other items|
|---|---|---|---|
|`plain`|none|`prikey` in the clear|the entity's JSON in the clear|
|`appPassword`|`hint`, no password|`prikeyCipher` under the user's wallet password|a Password cipher of the entity's JSON|
|`randomPassword`|`hint`, no password|`prikeyCipher` under a random password|a Password cipher of the entity's JSON|

In both encrypted modes the password is not in the backup, and the reader asks the user for it. In `randomPassword` mode the writer hands the password to the user apart from the backup (see [Writing](#writing)).

Backups from earlier writers may still carry the random password in `BackupKey.password`. Readers MUST accept them and take the password from there.

### BackupKey

|Field|Type|Rule|
|---|---|---|
|`password`|string|Earlier writers only, in `randomPassword` mode: the password every cipher in the backup was made with. Writers MUST NOT set it; readers MUST use it when present.|
|`symkey`|hex|Reserved: a symkey that opens Symkey item ciphers. No current writer sets it; a reader MAY support it.|
|`time`|string|Same as the header's `time`.|
|`keyName`|hex, 12 chars|Earlier writers only. Writers MUST NOT set it; readers MUST ignore it.|
|`hint`|string|A note that the password is not in the backup, e.g. `App password can not be shown. Please keep it carefully.` in `appPassword` mode, or `The password was shown once at export and is kept apart from this data.` in `randomPassword` mode.|

### BackupHeader

|Field|Type|Rule|
|---|---|---|
|`time`|string|Export time, local, `yyyy-MM-dd HH:mm:ss`.|
|`items`|integer|Number of items.|
|`keyName`|hex, 12 chars|Earlier writers only: the first 6 bytes of SHA-256 of the password. Writers MUST NOT set it; readers MUST ignore it. See [Security Considerations](#security-considerations).|
|`alg`|string|Encrypted modes only: `AesGcm256@No1_NrC7`.|
|`qrCodes`|integer|Optional: how many QR codes the backup was split into for display.|
|`fid`|string|Optional: the FID whose private key opens the items' own ciphers, such as a `Secret`'s `contentCipher`, when the items stay encrypted to an identity (Freer's secret backup). Readers MAY use it to tell the user which identity the backup needs.|
|`tClass`|string|The simple class name of the items.|

### Key items

A key item is a `KeyInfo` ([FVEP1](../FVEP/FVEP1V1_Entity.md)) reduced to what moves:

|Field|Rule|
|---|---|
|`id`|The FID. REQUIRED.|
|`prikey`|`plain` only: the 32-byte private key as 64 hex characters. Readers MUST also accept compressed WIF (52 characters) and `0x`-prefixed hex.|
|`prikeyCipher`|Encrypted modes only: the 32-byte private key as a Password cipher ([FVEP8](../FVEP/FVEP8V1_Encryption.md)), `alg` `AesGcm256@No1_NrC7`, KDF Argon2id ([FTSP29](FTSP29V1_Argon2idPasswordToSymkey.md)), written as the Base64 text form of its bundle ([FTSP30](FTSP30V1_CryptoBundle.md) §Text form).|
|`label`, `saveTime`|Optional, copied as they are.|
|`pubkey`|Writers omit it. If present, the reader MUST check it against the private key.|

A key with no private key is not exported.

### Other items

In `plain` mode an item is the entity's JSON. In an encrypted mode it is a Password cipher of that JSON's UTF-8 bytes, written as the cipher's JSON form (FVEP8: `type`, `alg`, `kdf`, `cipher`, `iv`, …). A `Secret` item exports its content decrypted, in `content`, with no `contentCipher`; a `Secret` with neither is skipped.

### Reading

1. **Split** the text into JSON objects.
2. **Classify** each object, in this order:
   1. it has both `cipher` and `iv` → an item cipher;
   2. it has `password`, or `symkey`, or `hint` → the `BackupKey`;
   3. it has both `items` and `time` → the `BackupHeader`;
   4. otherwise → an item.

   An entity may have a field named `cipher`, `alg` or `type` of its own (`Secret` has all three), so a reader MUST NOT treat an object as a cipher on any one field alone.
3. **The password** is `BackupKey.password` if present, otherwise the one the user enters.
4. **Open each item cipher** with the password and parse the plaintext as the item.
5. **For each key item**: take the private key from `prikey`, or open `prikeyCipher` with the password; derive its FID and public key; if the FID is not the item's `id`, or `pubkey` is present and differs, **refuse** the backup. Then:
   - store the private key the way the reader stores its own keys, encrypted for this device (in Freer and Safe, a Symkey cipher under the vault's data key). The reader MUST NOT store the backup's `prikey` field, nor a `prikeyCipher` it cannot open itself;
   - store the derived public key and the addresses made from it.
6. **A wrong password refuses the backup.** If the password does not open a cipher, the reader MUST report it and save nothing from the backup: not the items it did open, and never an item still holding a cipher it could not open.
7. Each item's own rules apply once it is open, e.g. the user confirms before a `KeyInfo` whose private key is published (a nobody, as the apps' `NOBODY_SPEC.md` defines it) is saved.

A reader MAY accept a whole-item cipher for keys too: its plaintext is a key item with `prikey`.

### Writing

1. Choose the mode. In `randomPassword` mode the password is the unpadded RFC 4648 Base32 of 16 random bytes (26 characters).
2. In an encrypted mode write the `BackupKey`, with `time` and `hint` only, then the header. For `Secret` and other entities the header is also written in `plain` mode, with no `alg`; for keys it is not. Neither object carries `password`, `keyName` or anything else derived from the password.
3. Write each item. Every cipher MUST have its own random IV, and Password ciphers carry no `keyName` ([FVEP8](../FVEP/FVEP8V1_Encryption.md)).
4. In `randomPassword` mode, hand the password to the user apart from the backup: show it once, as text and as a QR code of the password alone, with a warning to keep it apart from the backup, and close only when the user confirms it has been saved. It MUST NOT be written into the backup's file, QR codes or copied text.
5. A writer MUST warn the user before `plain` mode.

### Error handling

|Condition|Reader action|
|---|---|
|The password opens no cipher, or any one cipher|Refuse; save nothing.|
|A key's private key is not 32 bytes, or not the item's FID|Refuse; save nothing.|
|A key item's `id` is not a valid FID|Skip the item.|
|An item has neither a private key nor content|Skip the item.|
|Header `tClass` is not the kind being imported|SHOULD refuse.|

## Test Vectors

The complete set is [vectors/backup.json](vectors/backup.json): the six lists below as Safe writes them, both items opened; a list from an earlier writer with the random password in its `BackupKey` (`BACKUP-KEYS-LEGACY-RANDOM-PASSWORD`, password `AAAQEAYEAUDAO`), which must still import; and three that must be refused (wrong password for keys and for secrets, a key that is not its FID). Inputs are the [FTSP0 §2.1](FTSP0V1_FTSP.md#21-shared-example-keys-developer-json-samples) keys fidA and fidB and the password `MyPassword`; the random password is `AAAQEAYEAUDAOCAJBIFQYDIOB4`, the Base32 of `000102…0f`, given in each vector's `password` field because the list does not hold it. So that the file is stable, each cipher's IV is `000102030405060708090a` followed by a counter byte from `80`; real writers use random IVs.

|Vector|Mode|Items|
|---|---|---|
|`BACKUP-KEYS-PLAIN`|plain|fidA, fidB|
|`BACKUP-KEYS-APP-PASSWORD`|appPassword (`MyPassword`)|fidA, fidB|
|`BACKUP-KEYS-RANDOM-PASSWORD`|randomPassword (`AAAQEAYEAUDAOCAJBIFQYDIOB4`)|fidA, fidB|
|`BACKUP-SECRETS-PLAIN`|plain|2 secrets|
|`BACKUP-SECRETS-APP-PASSWORD`|appPassword (`MyPassword`)|2 secrets|
|`BACKUP-SECRETS-RANDOM-PASSWORD`|randomPassword (`AAAQEAYEAUDAOCAJBIFQYDIOB4`)|2 secrets|

### TV-BACKUP-1 — keys, plain

```json
{
  "label": "key A",
  "saveTime": "2026-10-02 12:00:00",
  "prikey": "a048f6c843f92bfe036057f7fc2bf2c27353c624cf7ad97e98ed41432f700575",
  "id": "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK"
}

{
  "label": "key B",
  "saveTime": "2026-10-02 12:00:00",
  "prikey": "ee72e6dd4047ef7f4c9886059cbab42eaab08afe7799cbc0539269ee7e2ec30c",
  "id": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW"
}
```

A reader saves fidA and fidB with public keys `030be1d7…8312a` and `02536e4f…b2f67` and the private keys encrypted for the device, never these `prikey` fields.

### TV-BACKUP-2 — keys, app password

```json
{
  "time": "2026-10-02 12:00:00",
  "hint": "App password can not be shown. Please keep it carefully."
}

{
  "time": "2026-10-02 12:00:00",
  "items": 2,
  "alg": "AesGcm256@No1_NrC7",
  "tClass": "KeyInfo"
}

{
  "prikeyCipher": "dveyJqizAwABAgMEBQYHCAkKgFjVMA5My28bGXPb/e0uqGI/+/4Hd3sDB7mh7Q+0FC8K+wt22XyxKb9w4H4i2qPWKA==",
  "label": "key A",
  "saveTime": "2026-10-02 12:00:00",
  "id": "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK"
}

{
  "prikeyCipher": "dveyJqizAwABAgMEBQYHCAkKgRIoSHKqBP/MPkWSF9CaYgYWSTI/AHFfU9U4cyHXOHHaDbcAo7L1urYxHyoSRU3/mA==",
  "label": "key B",
  "saveTime": "2026-10-02 12:00:00",
  "id": "F86zoAvNaQxEuYyvQssV5WxEzapNaiDtTW"
}
```

Each `prikeyCipher` is a type-3 bundle (FTSP30): prefix `76f7b226a8b3`, type `03`, IV, then the AES-256-GCM ciphertext of the 32-byte key; the KDF is not recorded, so the reader derives with Argon2id and falls back as FTSP30 §Decryption says. With `NotMyPassword` the reader must refuse the list (`BACKUP-KEYS-WRONG-PASSWORD`).

### TV-BACKUP-3 — secrets, random password

The `BackupKey` holds only a hint; the password `AAAQEAYEAUDAOCAJBIFQYDIOB4` was handed to the user apart. Each item is the JSON form of a Password cipher:

```json
{
  "time": "2026-10-02 12:00:00",
  "hint": "The password was shown once at export and is kept apart from this data."
}

{
  "time": "2026-10-02 12:00:00",
  "items": 2,
  "alg": "AesGcm256@No1_NrC7",
  "tClass": "Secret"
}

{
  "type": "Password",
  "alg": "AesGcm256@No1_NrC7",
  "kdf": "Argon2id@No1_NrC7",
  "cipher": "mWoFCzZrO59/Qb6hAl8DarNzb0tiLePRBEHUdxeDm1P2jEsxJOXQuk4rQnsuoSbY0nIMkccMS6oRoyA8x6tw0cYgruo30/lmWH9jThZiN7XFVacAE6kZaK4wR2lwP2qihTdbwsbSxAKhDjP7eAurRc4zBdUUow==",
  "iv": "000102030405060708090a86"
}
```

(The second item is in the vector file.) The first opens to `{"type":"Password","title":"Bank","content":"PIN 2468","memo":"the card ending 1234"}`. The secret's own `type` is `Password` too; it is the `cipher` and `iv` pair that marks the line as a cipher.

## Security Considerations

- **Plain mode exposes every private key.** [FVEP1](../FVEP/FVEP1V1_Entity.md) allows a private key in a stored or transmitted entity only encrypted. Plain backups are the one exception, kept because users already hold such files: readers MUST accept them and MUST seal the keys at once; writers MUST warn before making one.
- **The random password never travels with the backup.** Earlier writers put it in the `BackupKey`, so a file, QR set or copied text holding everything was as exposed as a plain backup. Writers now hand it over once, apart; a backup from an earlier writer is still only as private as its `BackupKey`.
- **The random password is 128 bits.** Earlier writers used 64 bits. Every guess costs an Argon2id run, but nothing is gained by keeping the margin small.
- **`keyName` was a fast password check, and is gone.** In earlier backups it was 48 bits of an unsalted SHA-256 of the password, so an attacker holding the backup could test candidate passwords at SHA-256 speed without running Argon2id once, and in `appPassword` mode the password is the user's wallet password. Writers no longer emit it and readers ignore it; a reader that wants an early wrong-password check can open the first cipher instead.
- **Metadata is not encrypted.** Key items keep `id`, `label` and `saveTime` in the clear in every mode, so a backup reveals which FIDs it holds. The `BackupKey`, header and these fields are not authenticated; changing them cannot change an opened private key without the FID check failing, but `items`, `time` and labels can be altered unnoticed.
- **A partial import is worse than none.** Saving the keys a wrong password did not open leaves identities that cannot sign or connect, which looks like a network fault rather than a bad backup. Hence rule 6 of [Reading](#reading).

## Versioning

|Ver|Date|Author|Summary|
|---|---|---|---|
|1|2026-10-02|No1_NrC7|The backup layout as Safe and Freer write it; three modes; classification order; key items re-sealed and checked against their FID; wrong password refuses; vectors in `backup.json`.|
|1|2026-10-22|No1_NrC7|Draft revised: the random password is 128 bits and handed to the user apart, never written into the backup; `keyName` dropped from the `BackupKey`, the header and Password ciphers, and the reader's keyName check removed; a `hint` alone marks the `BackupKey`; an optional header `fid` names the identity that opens items left encrypted to it; a legacy vector keeps earlier backups importable.|

## Related Protocols

|Protocol|Relationship|
|---|---|
|[FVEP1](../FVEP/FVEP1V1_Entity.md)|`KeyInfo`, `Secret`; the rule that private keys are stored only encrypted, to which plain backups are the exception.|
|[FVEP8](../FVEP/FVEP8V1_Encryption.md)|Password ciphers and their JSON form.|
|[FTSP12](FTSP12V1_AesGcm256.md)|The cipher.|
|[FTSP29](FTSP29V1_Argon2idPasswordToSymkey.md)|The password KDF.|
|[FTSP30](FTSP30V1_CryptoBundle.md)|The Base64 bundle form of `prikeyCipher`.|

## Reference Implementation

FC-JDK has no backup reader or writer. The vector generator and a reader that applies the rules above are there; the apps hold the production code.

|Component|Location|
|---|---|
|Vector generator, `backupVectors`|[FC-JDK/src/test/java/core/crypto/CryptoVectorsGenerator.java](../../FC-JDK/src/test/java/core/crypto/CryptoVectorsGenerator.java)|
|Reference reader, `readBackup`; vector test `backup`|[FC-JDK/src/test/java/core/crypto/CryptoVectorsTest.java](../../FC-JDK/src/test/java/core/crypto/CryptoVectorsTest.java)|
|Safe writers|`Safe/app/.../myKeys/ExportKeysActivity.java`, `BackupKeysActivity.addKeyInfoJson`, `secret/ExportSecretActivity.java`, `secret/BackupSecretsActivity.java`; the password dialog `ui/RandomPasswordDialog.java`|
|Freer writer|`Freer/app/.../secret/ExportSecretActivity.java`|
|Freer reader|`Freer/app/.../utils/BackupUtils.readBackup`, `FcEntityImporter`; vector test `SafeBackupCompatTest`|
