# FC-JDK Crypto Package Security Audit Report

**Package**: `FC-JDK/src/main/java/core/crypto/`  
**Date**: 2026-04-09  
**Files Analyzed**: 44 Java source files across 4 directories  

---

## Executive Summary

The crypto package provides a comprehensive set of cryptographic operations including ECC (secp256k1), AES-CBC/GCM, ChaCha20, X25519, HKDF, BIP39/BIP44 key derivation, and multi-chain address encoding. While the algorithm choices are generally modern and sound, **several critical security issues** were identified that could lead to key material leakage, masked decryption failures, and timing attacks.

| Severity | Count |
|----------|-------|
| Critical | 4 |
| High | 7 |
| Medium | 8 |
| Low | 5 |

---

## Critical Issues

### 1. Silent Cryptographic Failure (Null Returns)

**Files**: `Decryptor.java`, `AesGcm256.java`, `Bitcore.java`

Multiple decryption methods return `null` on failure instead of throwing exceptions. If callers don't rigorously null-check, a decryption failure is silently treated as success.

```java
// Decryptor.java — silent failure
catch (Exception e) {
    return null; // caller may not notice decryption failed
}
```

**Risk**: Applications process unauthenticated or corrupted data as valid.  
**Fix**: Replace null returns with `CryptoException` throws. Force callers to handle failures explicitly.

---

### 2. Sensitive Data Leaked to stdout

**Files**: `Hash.java`, `KeyTools.java`, `Bitcore.java`, and others

Approximately 20+ `System.out.println()` calls print cryptographic data (hashes, hex-encoded material, IVs) and 3+ `printStackTrace()` calls expose internal state.

```java
// Hash.java lines 105-109
System.out.println(HexFormat.of().formatHex(data));
```

**Risk**: Key material and algorithm internals leak to logs in production.  
**Fix**: Remove all `System.out.println` from production code. Use SLF4J/Log4j at `DEBUG` level behind a flag.

---

### 3. Key Material Not Zeroed After Use

**Files**: `KeyTools.java`, `X25519AesGcm256.java`, `Ecc256K1AesGcm256.java`, `CryptoDataStr.java`

While some cleanup exists (`clearByteArray`, `clearCharArray`), intermediate values in BIP39/BIP44 derivation and ECDH shared secrets are not consistently cleared.

```java
// KeyTools.java — intermediate 'i' not cleared
byte[] i = hmacSHA512(key, data);
byte[] iL = Arrays.copyOfRange(i, 0, 32);
byte[] iR = Arrays.copyOfRange(i, 32, 64);
Arrays.fill(iL, (byte) 0); // cleared
Arrays.fill(iR, (byte) 0); // cleared
// 'i' NOT cleared — 64 bytes of key material left in heap
```

**Risk**: Heap dumps or GC scanning recovers key material.  
**Fix**: `Arrays.fill(i, (byte) 0)` immediately after splitting. Apply this pattern to all intermediate values.

---

### 4. Constant-Time Comparison Is Not Actually Constant-Time

**File**: `Bitcore.java` lines 194-204

```java
private static boolean constantTimeEquals(byte[] a, byte[] b) {
    if (a.length != b.length) {
        return false;  // <-- leaks length via timing
    }
    // ... XOR loop (correct part)
}
```

**Risk**: Timing side-channel reveals whether HMAC tag lengths match, aiding forgery attacks.  
**Fix**: Use `MessageDigest.isEqual()` (constant-time in JDK) or pad to fixed length before comparison.

---

## High Severity Issues

### 5. Weak SecureRandom Instantiation

**Files**: `KeyTools.java`, `Bitcore.java`, multiple Algorithm/ files

Repeated `new SecureRandom()` calls instead of sharing an instance or using `SecureRandom.getInstanceStrong()`.

**Fix**: Use a shared `static final SecureRandom SRNG = new SecureRandom()` or `SecureRandom.getInstanceStrong()` for key generation.

---

### 6. Non-Standard Message Authentication in Hash.getSign()

**File**: `Hash.java` lines 90-93

Uses `SHA256x2(text || key)` instead of HMAC-SHA256. This construction is vulnerable to length-extension attacks.

**Fix**: Replace with `javax.crypto.Mac` using `HmacSHA256`.

---

### 7. Silent IV Truncation

**File**: `Encryptor.java` lines 644-656

When AES-GCM or ChaCha20 receives a 16-byte IV (intended for CBC), it silently truncates to 12 bytes instead of rejecting the input.

**Risk**: Nonce reuse if truncation maps different 16-byte IVs to the same 12-byte prefix.  
**Fix**: Reject invalid IV sizes with an explicit error. Check all the usages to ensure that the 12-byte IV is passed in. 

---

### 8. Missing Input Validation on Public Methods

**Files**: `KeyTools.java`, `Base58.java`, `BtcAddrConverter.java`

- `mnemonicToBytes()` doesn't validate word list membership before processing
- `BtcAddrConverter` assumes decoded byte length without bounds checks
- Public key format detection relies only on first byte

**Fix**: Add explicit validation at every public API boundary.

---

### 9. Null Pointer Dereference Paths

**Files**: `CryptoDataByte.java`, `Decryptor.java`, `Hash.java`

Several methods return null on error; subsequent methods in the call chain dereference without null checks.

**Fix**: Adopt a fail-fast pattern — throw early rather than propagate nulls.

---

### 10. ChaCha20 Used Without Authentication

**File**: `ChaCha20.java`

ChaCha20 is a stream cipher without built-in authentication. The code relies on a non-standard `makeSum4()` using `SHA256(key || iv || did)` for integrity.

**Fix**: Use ChaCha20-Poly1305 (RFC 8439) instead.

---

### 11. Bitcore Dead Code and Hardcoded Flags

**File**: `Bitcore.java` lines 39-40

```java
static boolean SHORT_TAG = false;
static boolean NO_KEY = false;
```

These flags are never modified, creating dead code branches that add confusion and potential for latent bugs.

**Fix**: Remove unused code paths or make them configurable.

---

## Medium Severity Issues

### 12. Legacy Algorithms Still Present

**Directory**: `old/` — `EccAes256K1P7.java`, `Aes256CbcP7.java`, etc.

Old implementations remain in the codebase without deprecation annotations or migration guidance.

**Suggestion**: Mark `@Deprecated` with removal timeline. Provide migration path to current Algorithm/ implementations.

---

### 13. Hardcoded BIP44 Path

**File**: `KeyTools.java` lines 1608-1615

Always derives `m/44'/0'/0'/0/i` (Bitcoin mainnet). No support for other coin types or account indices.

**Suggestion**: Parameterize coin type and account index for multi-chain support.

---

### 14. No Brute-Force Delay on Decryption Failure

**File**: `Decryptor.java`

No artificial delay on password decryption failure, enabling fast offline brute-force attacks.

**Suggestion**: Add a deliberate delay (100-500ms) on authentication failure, or increase PBKDF2 iteration count beyond 2048.

---

### ~~15. Serialization of Sensitive Fields~~ (Resolved — Not an Issue)

**Files**: `CryptoDataStr.java`, `CryptoDataByte.java`

The `transient` usage is correct by design. Non-transient fields (`data`, `cipher`, `iv`, `pubkeyA`, `pubkeyB`, `sum`, `keyName`) form the encrypted envelope or decryption result. Secrets (`password`, `symkey`, `prikeyA`, `prikeyB`) are properly transient. The `data` field is intentionally non-transient as it carries the decryption result; during encryption it is set to null before serialization, preventing plaintext leakage.

---

### 16. Incomplete Signing Implementations

**Files**: `TxSignerSchnorr.java` (returns empty array), `TxSingerEC.java` (minimal)

**Suggestion**: Remove. Empty stubs in a crypto package are dangerous if accidentally called.

---

### ~~17. Algorithm Combination Complexity~~ (Resolved — By Design)

Multiple algorithm combinations (AES-CBC, AES-GCM, ChaCha20 x ECC, X25519, legacy) exist to serve different use cases with existing deployments depending on them. All must be maintained.

**Suggestion**: Ensure each combination has dedicated unit tests to prevent regressions.

---

### 18. Error Messages Expose Implementation Details

Exception messages contain algorithm names, key sizes, and operation context.

**Suggestion**: Use generic error messages externally; log details at DEBUG level only.

---

### 19. Test Code in Production

**File**: `Tester.java` is in `main/java/` instead of `test/java/`.

**Suggestion**: Move to `src/test/java/core/crypto/`.

---

## Efficiency Issues

### 20. Redundant Object Creation

- `Hash.java`: Creates two `MessageDigest` instances per SHA256x2 call instead of reusing one with `reset()`
- `Base58.java`: Multiple intermediate `char[]` and `byte[]` allocations during encoding
- `KeyTools.java`: Manual string padding instead of `String.format()`

### 21. Repeated SecureRandom Instantiation

Each encryption call creates a new `SecureRandom` instance. Instance creation can be expensive (entropy gathering).

**Fix**: Share a `static final` instance.

### 22. Magic Numbers Throughout

Algorithm IDs, key sizes, IV lengths, and hash constants appear as raw integers without named constants.

**Fix**: Define as `static final` named constants for readability and maintainability.

---

## Strengths

| Aspect | Assessment |
|--------|------------|
| Algorithm selection | Modern and appropriate (AES-GCM, X25519, HKDF, ChaCha20, secp256k1) |
| IV generation | Uses `SecureRandom` for all IV/nonce generation |
| Resource management | Proper try-with-resources for file operations |
| BIP39/BIP44 support | Correct implementation of mnemonic key derivation |
| Partial key cleanup | `CryptoDataByte`/`CryptoDataStr` attempt sensitive data clearing |
| Authenticated encryption | AES-GCM and X25519+HKDF provide proper AEAD |
| Multi-chain address support | Bitcoin, BCH, Bech32, CashAddr correctly implemented |

---

## Recommended Action Plan

### Phase 1 — Immediate (Before Next Release)

#### 1.1 Remove `System.out.println` and `printStackTrace` from production code (~90 calls)

Focus on production files only (skip `old/` and `Tester.java`):

| File | Calls | Key Lines | Notes |
|------|-------|-----------|-------|
| `KeyTools.java` | 58 | Throughout | Most frequent offender in production |
| `Algorithm/Bitcore.java` | 17 println + 3 printStackTrace | 303,306,309,312 (println), 401,471,561 (stack traces) | Stack traces expose internals |
| `Hash.java` | 5 | 105-108 (getSign prints hex of signing content), 196 | **Critical**: prints data being signed |
| `BtcAddrConverter.java` | 5 | Throughout | Address conversion debug |
| `CashAddress.java` | 3 | Throughout | |
| `BchCashAddr.java` | 3 | Throughout | |
| `Encryptor.java` | 2 | Throughout | |
| `CryptoDataByte.java` | 1 println + 1 printStackTrace | 192 (printStackTrace) | |
| `Algorithm/HKDF.java` | 2 | In main() only | Low priority — test code |

**Action**: Delete all `System.out.println`/`printStackTrace` in the files above. Do NOT add a logging framework — just remove. If any call is genuinely needed for diagnostics, replace with `log.debug()` (SLF4J is already used in `Decryptor.java`).

#### 1.2 Zero intermediate key material

| File | Method | Line | What to clear |
|------|--------|------|---------------|
| `KeyTools.java` | `deriveMasterKey()` | 1627 | `byte[] i` after splitting into iL/iR |
| `KeyTools.java` | `deriveChildKey()` | 1672 | `byte[] i` after splitting into iL/iR |
| `KeyTools.java` | `pbkdf2HmacSha512()` | 1546-1554 | `byte[] u` and `byte[] t` after each iteration |
| `X25519AesGcm256.java` | `encryptStream()` | 176 | `byte[] symkey` after encryption |
| `X25519AesGcm256.java` | `decryptStream()` | 207 | `byte[] symkey` after decryption |
| `Ecc256K1AesGcm256.java` | `encryptStream()` | 173 | `byte[] symkey` after encryption |
| `Ecc256K1AesGcm256.java` | `decryptStream()` | 204 | `byte[] symkey` after decryption |

**Action**: Add `Arrays.fill(array, (byte) 0)` in a `finally` block for each variable listed. The `asyKeyToSymkey()` methods in both files already clean up shared secrets correctly (line 88 and 85 respectively) — no change needed there.

#### 1.3 Fix `constantTimeEquals` in Bitcore.java (line 194)

**Current risk**: Low in practice — both inputs are always 32 bytes (or 4 with SHORT_TAG), so the length branch never leaks useful info. Still worth fixing for correctness.

**Action**: Replace with `MessageDigest.isEqual(a, b)` which is constant-time and handles length internally.

#### 1.4 Fix ChaCha20 missing from `generateRandomIv()`

**File**: `Encryptor.java` lines 89-94

ChaCha20 algorithms require 12-byte IVs but are not listed in the `generateRandomIv()` switch case, so they fall through to the `default -> 16` branch, generating 16-byte IVs that then get silently truncated by `adjustIvLength()`.

**Action**: Add `FC_ChaCha20_No1_NrC7` and `FC_EccK1ChaCha20_No1_NrC7` to the 12-byte case in `generateRandomIv()`. After this fix, `adjustIvLength()` truncation becomes dead code for normal paths — keep it as defensive code but add a log warning when truncation actually triggers.

---

### Phase 2 — Short-Term (1-2 Sprints)

#### 2.1 Replace `Hash.getSign()` with HMAC-SHA256

**File**: `Hash.java` lines 90-93

**Current**: `SHA256x2(text || key)` — no formal security proof, vulnerable to length-extension on inner hash, key/message boundary ambiguity.

**Action**:
1. Implement new method using `javax.crypto.Mac` with `HmacSHA256`
2. Search all callers of `Hash.getSign()` across the entire codebase
3. Assess backward compatibility — if existing signed data is stored/verified, a migration strategy is needed (e.g., try HMAC first, fall back to old method during transition)

**Risk**: This changes the output of `getSign()`. Any stored signatures or API authentication tokens using the old method will break. **Must audit all callers before changing.**

#### 2.2 Switch ChaCha20 to ChaCha20-Poly1305

**File**: `Algorithm/ChaCha20.java`

**Current**: ChaCha20 stream cipher + custom `makeSum4()` integrity check (non-standard).

**Action**: Replace with `ChaCha20-Poly1305` (RFC 8439) using `javax.crypto.Cipher` with `"ChaCha20-Poly1305"` (available since Java 11). This provides authenticated encryption natively.

**Risk**: Changes ciphertext format. Any data encrypted with the old ChaCha20 + makeSum4 scheme will need a migration/fallback path for decryption.

#### 2.3 Fix null returns in `Decryptor.java` (2 methods only)

| Method | Line | Current behavior |
|--------|------|------------------|
| `decryptPrikey()` | 117 | Returns `null` when inner decryption fails |
| `decryptFile()` | 128, 134 | Returns `null` when prikey or file decryption fails |

**Action**: Return a `CryptoDataByte` with error code set (matching the `setCodeMessage()` pattern used everywhere else in the file), instead of `null`. This makes error handling consistent.

**Note**: `AesGcm256.java` already uses the `setCodeMessage()` pattern correctly — no change needed there (corrected from initial audit).

#### 2.4 Remove signing stubs

| File | Status |
|------|--------|
| `TxSignerSchnorr.java` | Returns `new byte[0]` — dangerous if called |
| `TxSingerEC.java` | Completely empty class |

**Action**: Delete both files. If Schnorr signing is needed later, implement it properly.

#### 2.5 Move `Tester.java` to test directory

**Action**: Move from `src/main/java/core/crypto/Tester.java` to `src/test/java/core/crypto/CryptoTest.java`. Update package declaration.

---

### Phase 3 — Medium-Term (1-2 Months)

#### 3.1 Deprecate `old/` implementations

**Files**: `old/EccAes256K1P7.java` (143 println calls), `old/Aes256CbcP7.java`, `old/StartTools.java`, etc.

**Action**:
1. Add `@Deprecated` annotation to all classes in `old/`
2. Search callers — `CryptoDataByte.java` and `CryptoDataStr.java` import `EccAes256K1P7`
3. Create migration path from old algorithms to current `Algorithm/` implementations
4. Remove once no callers remain

#### 3.2 Add unit tests for each algorithm combination

Cover at minimum:
- Encrypt → Decrypt roundtrip for each algorithm
- Wrong key / corrupted ciphertext → proper error (not null, not silent success)
- IV uniqueness per encryption
- Key material not present in output

#### 3.3 Add brute-force delay on password decryption failure

**File**: `Decryptor.java`

**Action**: Add `Thread.sleep(200)` on password authentication failure. Or increase PBKDF2 iterations beyond 2048 (current BIP39 standard minimum).

#### 3.4 Replace magic numbers with named constants

Define `static final` constants for:
- IV sizes (12, 16)
- Key sizes (32, 64)
- Algorithm IDs used in switch statements
- HMAC tag lengths (4, 32)

#### 3.5 Parameterize BIP44 derivation path

**File**: `KeyTools.java` lines 1608-1615

**Current**: Hardcoded `m/44'/0'/0'/0/i` (Bitcoin mainnet only).

**Action**: Accept coin type and account index as parameters for multi-chain support.

---

## Items Removed from Plan (After Verification)

| Original # | Reason Removed |
|-------------|----------------|
| #7 (SecureRandom everywhere) | Only 2 production instances in `Bitcore.java` — not worth a cross-cutting change |
| #8 (Input validation all public methods) | Too broad — better addressed per-method as part of other fixes |
| #13 (Reduce algorithm combinations) | By design — each serves a real use case with existing deployments |
| #15 (Serialization) | Correct by design — `data` is nulled before encryption serialization |

---

## Conclusion

The crypto package has sound algorithm choices and correct high-level design. The verified issues are primarily operational: debug output leaking sensitive data (~90 calls in production code), incomplete key material cleanup (7 specific locations), and 2 methods with inconsistent error handling. Phase 1 (4 items) can be done with low risk and no API changes. Phase 2 items (especially 2.1 and 2.2) require caller analysis and migration planning since they change output formats.
