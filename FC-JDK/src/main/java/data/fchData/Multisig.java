package data.fchData;

import core.crypto.KeyTools;
import data.fcData.FcObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.BytesUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static constants.FieldNames.*;

public class Multisig extends FcObject {
	private static final Logger log = LoggerFactory.getLogger(Multisig.class);

	private String redeemScript;
	private Integer m;
	private Integer n;
	private List<String> pubkeys;
	private List<String> fids;

	private Long birthHeight;
	private Long birthTime;
	private String birthTxId;

	public Multisig(){}

	/**
	 * Create Multisig from P2SH object
	 * Only works for P2SH objects with type MULTISIG or MULTISIG_CLTV
	 *
	 * @param p2sh P2SH object to convert
	 * @throws IllegalArgumentException if P2SH is not a multisig type or lacks required data
	 */
	public Multisig(P2SH p2sh) {
		if (p2sh == null) {
			throw new IllegalArgumentException("P2SH object cannot be null");
		}

		// Check if P2SH type is multisig (MULTISIG or MULTISIG_CLTV)
		if (p2sh.getType() != P2SH.P2shType.MULTISIG &&
		    p2sh.getType() != P2SH.P2shType.MULTISIG_CLTV) {
			throw new IllegalArgumentException("P2SH type must be MULTISIG or MULTISIG_CLTV, got: " + p2sh.getType());
		}

		// Validate required multisig fields
		if (p2sh.getM() == null || p2sh.getN() == null ||
		    p2sh.getPubkeys() == null || p2sh.getPubkeys().isEmpty()) {
			throw new IllegalArgumentException("P2SH object lacks required multisig data (m, n, or pubkeys)");
		}

		// Extract multisig-specific fields
		this.m = p2sh.getM();
		this.n = p2sh.getN();
		this.pubkeys = new ArrayList<>(p2sh.getPubkeys());

		// For MULTISIG type: use the full redeemScript as-is
		// For MULTISIG_CLTV type: extract the multisig portion (without CLTV prefix)
		if (p2sh.getType() == P2SH.P2shType.MULTISIG) {
			this.redeemScript = p2sh.getRedeemScript();
		} else {
			// MULTISIG_CLTV: rebuild the multisig-only redeemScript
			org.bitcoinj.script.Script multisigScript = P2SH.makeMultisigRedeemScript(this.pubkeys, this.m, this.n);
			this.redeemScript = utils.Hex.toHex(multisigScript.getProgram());
		}

		// Generate FIDs from public keys
		this.fids = new ArrayList<>();
		for (String pubkey : this.pubkeys) {
			String fid = KeyTools.pubkeyToFchAddr(pubkey);
			this.fids.add(fid);
		}

		// Set ID to the P2SH FID (multisig address)
		this.setId(p2sh.getFid());

		// Copy birth information if available
		this.birthHeight = p2sh.getBirthHeight();
		this.birthTime = p2sh.getBirthTime();
		this.birthTxId = p2sh.getBirthTxId();
	}

	public static LinkedHashMap<String,Integer> getFieldWidthMap(){
		LinkedHashMap<String,Integer> map = new LinkedHashMap<>();
		map.put(ID, DEFAULT_ID_LENGTH);
		map.put("m", DEFAULT_BOOLEAN_LENGTH);
		map.put("n", DEFAULT_BOOLEAN_LENGTH);
		map.put(BIRTH_TIME, DEFAULT_TIME_LENGTH);
		return map;
	}
	public static List<String> getTimestampFieldList(){
		return List.of(BIRTH_TIME);
	}

	public static List<String> getSatoshiFieldList(){
		return List.of(BALANCE);
	}
	public static Map<String, String> getHeightToTimeFieldMap() {
		Map<String, String> map = new HashMap<>();
		map.put(BIRTH_HEIGHT,BIRTH_TIME);
		return map;
	}

	public static Map<String, String> getShowFieldNameAsMap() {
		Map<String,String> map = new HashMap<>();
		map.put(ID,FID);
		map.put("m","Required");
		map.put("n","Members");
		return map;
	}

	public static Map<String, Object> getInputFieldDefaultValueMap() {
		return new HashMap<>();
	}

//
//	/**
//	 * Parses a P2SH multisignature script from a blockchain input.
//	 *
//	 * This method distinguishes between different P2SH types:
//	 * - Pure multisig: OP_m <pubkey1> ... <pubkeyN> OP_n OP_CHECKMULTISIG
//	 * - Multisig with CLTV: <locktime> OP_CHECKLOCKTIMEVERIFY OP_DROP OP_m <pubkeys> OP_n OP_CHECKMULTISIG
//	 * - CLTV-only or other P2SH types: Ignored (not parsed)
//	 *
//	 * The method validates the script structure and only parses valid multisig patterns.
//	 *
//	 * @param esClient Elasticsearch client for storing the parsed multisig data
//	 * @param input The Cash input containing the unlocking script to parse
//	 * @throws ElasticsearchException if there's an error accessing Elasticsearch
//	 * @throws IOException if there's an error reading the script data
//	 */
//	public void parseMultisig(ElasticsearchClient esClient, Cash input) throws ElasticsearchException, IOException {
//        /* Example of multiSig input unlocking unlockScript:
//				"00" +
//				"41" +
//				"8ec1f75f4368e650f6cf0c8a80c009094748845c9d354f593359bd971370d94b" +
//				"a48db3b925dd0869d1610b1c0a4d27f7ac25f35d46b034dbcbd30a6e78110764" +
//				"41" +
//				"41" +
//				"e226dbc949b2f2bfb2fd8cfb7a4851c43700e9febf16873b679e412714ad3235" +
//				"b528053dc46990e3bdcc40656764694f403fbce5f59a4e5424140e09e09e87b4" +
//				"41" +
//				"4c" +
//				"69" +
//				"52" +
//				"21" +
//				"030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a" +
//				"21" +
//				"02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67" +
//				"21" +
//				"03f0145ddf5debc7169952b17b5c6a8a566b38742b6aa7b33b667c0a7fa73762e2" +
//				"53" +
//				"ae";
//		*/
//		String unlockScript = input.getUnlockScript();
//
//		// Check if already exists in database
//		GetResponse<Multisig> resultGetMultisign = esClient.get(g->g.index(IndicesNames.MULTISIG).id(input.getOwner()), Multisig.class);
//		if(resultGetMultisign.found()) return;
//
//		// Basic validation: unlockScript must end with OP_CHECKMULTISIG (0xae)
//		if(unlockScript == null || unlockScript.length() < 2 || !unlockScript.substring(unlockScript.length()-2).equals("ae")) {
//			return;
//		}
//
//		InputStream scriptIs = new ByteArrayInputStream(BytesUtils.hexToByteArray(unlockScript));
//		byte[] b = new byte[1];
//
//		try {
//			scriptIs.read(b);
//
//			// Skip leading OP_0 (0x00) for unlocking unlockScript
//			if(b[0] == 0x00) {
//				scriptIs.read(b);
//			}
//
//			// Skip all signatures (65 bytes each with length prefix 0x41)
//			while(b[0] == 65) {
//				scriptIs.skipNBytes(65);
//				if(scriptIs.available() == 0) return; // No redeem unlockScript found
//				scriptIs.read(b);
//			}
//
//			// Handle OP_PUSHDATA opcodes (0x4c=OP_PUSHDATA1, 0x4d=OP_PUSHDATA2, 0x4e=OP_PUSHDATA4)
//			if(b[0] == 0x4c) {
//				scriptIs.read(b); // Read 1-byte length
//			} else if(b[0] == 0x4d) {
//				scriptIs.skipNBytes(2); // Skip 2-byte length
//				scriptIs.read(b);
//			} else if(b[0] == 0x4e) {
//				scriptIs.skipNBytes(4); // Skip 4-byte length
//				scriptIs.read(b);
//			} else if(b[0] > 75 && b[0] <= 96) {
//				// OP_PUSHDATA for lengths 76-96, read the length byte
//				scriptIs.read(b);
//			}
//
//			// Now we should be at the start of the redeem unlockScript
//			// Check for CLTV pattern: <locktime> OP_CHECKLOCKTIMEVERIFY(0xb1) OP_DROP(0x75)
//			boolean hasCltv = false;
//
//			// Peek ahead to check for CLTV pattern
//			// CLTV locktime can be: 1-5 byte pushdata OR OP_1 to OP_16 (0x51-0x60)
//			if((b[0] >= 0x01 && b[0] <= 0x05) || (b[0] >= 0x51 && b[0] <= 0x60)) {
//				// Save current position
//				scriptIs.mark(10);
//
//				// Try to parse locktime
//				if(b[0] >= 0x01 && b[0] <= 0x05) {
//					int locktimeLen = b[0];
//					scriptIs.skipNBytes(locktimeLen);
//				}
//
//				// Check for OP_CHECKLOCKTIMEVERIFY (0xb1) followed by OP_DROP (0x75)
//				byte[] nextBytes = new byte[2];
//				if(scriptIs.read(nextBytes) == 2) {
//					if(nextBytes[0] == (byte)0xb1 && nextBytes[1] == 0x75) {
//						hasCltv = true;
//						scriptIs.read(b); // Read next byte (should be OP_m)
//					} else {
//						// Not a CLTV pattern, reset
//						scriptIs.reset();
//					}
//				} else {
//					scriptIs.reset();
//				}
//			}
//
//			// If not CLTV, b[0] is already at the right position
//			// If CLTV, we've already read the next byte into b[0]
//
//			// Now b[0] should contain OP_m (0x51-0x60 for m=1-16)
//			ArrayList<byte[]> redeemScriptBytesList = new ArrayList<>();
//			redeemScriptBytesList.add(b.clone());
//			int m = b[0] - 80; // OP_1=0x51=81, so 81-80=1
//
//			// Validate m is in valid range (1-16)
//			if(m > 16 || m < 1) {
//				return; // Not a valid multisig
//			}
//
//			// Parse public keys
//			ArrayList<String> pukList = new ArrayList<>();
//			ArrayList<String> addrList = new ArrayList<>();
//
//			while(true) {
//				if(scriptIs.available() == 0) return; // Unexpected end
//				scriptIs.read(b);
//				redeemScriptBytesList.add(b.clone());
//				int pkLen = b[0] & 0xFF;
//
//				// Public keys are either 33 (compressed) or 65 (uncompressed) bytes
//				if(pkLen != 33 && pkLen != 65) {
//					// This should be OP_n
//					break;
//				}
//
//				byte[] pkBytes = new byte[pkLen];
//				int bytesRead = scriptIs.read(pkBytes);
//				if(bytesRead != pkLen) return; // Incomplete public key
//
//				redeemScriptBytesList.add(pkBytes.clone());
//				String pubKey = BytesUtils.bytesToHexStringBE(pkBytes);
//				String addr = KeyTools.pubkeyToFchAddr(pubKey);
//				pukList.add(pubKey);
//				addrList.add(addr);
//			}
//
//			// Validate we found at least one public key
//			if(pukList.isEmpty()) return;
//
//			// b[0] now contains OP_n
//			int n = b[0] - 80; // OP_1=0x51=81, so 81-80=1
//
//			// Validate n is in valid range and n >= m
//			if(n > 16 || n < 1 || n < m || pukList.size() != n) {
//				return; // Not a valid multisig
//			}
//
//			// Next byte should be OP_CHECKMULTISIG (0xae)
//			if(scriptIs.available() == 0) return;
//			scriptIs.read(b);
//			redeemScriptBytesList.add(b.clone());
//
//			if(b[0] != (byte)0xae) {
//				return; // Not ending with OP_CHECKMULTISIG
//			}
//
//			// Validate this is the end of the redeem unlockScript (for pure multisig or multisig+CLTV)
//			if(scriptIs.available() > 0) {
//				// There might be additional opcodes - this could be a more complex unlockScript
//				// For safety, we only parse pure multisig or multisig+CLTV
//				return;
//			}
//
//			// Successfully parsed multisig
//			this.setRedeemScript(BytesUtils.bytesToHexStringBE(BytesUtils.bytesMerger(redeemScriptBytesList)));
//			input.setRedeemScript(this.getRedeemScript());
//			this.setM(m);
//			this.setN(n);
//			this.setPubkeys(pukList);
//			this.setFids(addrList);
//			this.setId(input.getOwner());
//			this.setBirthHeight(input.getSpendHeight());
//			this.setBirthTime(input.getSpendTime());
//			this.setBirthTxId(input.getBirthTxId());
//
//			esClient.index(i->i.index(IndicesNames.MULTISIG).id(this.getId()).document(this));
//
//		} catch (IOException e) {
//			// Failed to parse - silently ignore as this is not a valid multisig unlockScript
//			return;
//		}
//	}

	public static Multisig parseMultisigRedeemScript(String script)  {
		Multisig multisig = new Multisig();

		multisig.setId(KeyTools.scriptToMultiAddr(script));
		InputStream scriptIs = new ByteArrayInputStream(BytesUtils.hexToByteArray(script));

		byte[] b = new byte[1];
		try{
			ArrayList<byte[]> redeemScriptBytesList = new ArrayList<>();
			scriptIs.read(b);
			redeemScriptBytesList.add(b.clone());
			int m = b[0]-80;

			if(m>16 || m<0) return null;

			ArrayList<String> pukList = new ArrayList<>();
			ArrayList<String> addrList = new ArrayList<>();

			while(true) {
				scriptIs.read(b);
				redeemScriptBytesList.add(b.clone());
				int pkLen = b[0];
				if(pkLen!=33 && pkLen!=65)break;

				byte[] pkBytes = new byte[pkLen];
				scriptIs.read(pkBytes);
				redeemScriptBytesList.add(pkBytes.clone());
				String pubKey = BytesUtils.bytesToHexStringBE(pkBytes);
				String addr = KeyTools.pubkeyToFchAddr(pubKey);
				pukList.add(pubKey);
				addrList.add(addr);
				if(scriptIs.available()==0)break;
			}

			if(pukList.size()==0) return null;

			int n = b[0]-80;

			scriptIs.read(b);

			redeemScriptBytesList.add(b.clone());
			multisig.setRedeemScript(BytesUtils.bytesToHexStringBE(BytesUtils.bytesMerger(redeemScriptBytesList)));
			multisig.setM(m);
			multisig.setN(n);

			multisig.setPubkeys(pukList);
			multisig.setFids(addrList);
			return multisig;
		} catch (IOException e) {

			return null;
		}
	}

	public String getRedeemScript() {
		return redeemScript;
	}

	public void setRedeemScript(String redeemScript) {
		this.redeemScript = redeemScript;
	}

	public Integer getM() {
		return m;
	}

	public void setM(Integer m) {
		this.m = m;
	}

	public Integer getN() {
		return n;
	}

	public void setN(Integer n) {
		this.n = n;
	}

	public List<String> getPubkeys() {
		return pubkeys;
	}

	public void setPubkeys(List<String> pubkeys) {
		this.pubkeys = pubkeys;
	}

	public Long getBirthHeight() {
		return birthHeight;
	}

	public void setBirthHeight(Long birthHeight) {
		this.birthHeight = birthHeight;
	}

	public Long getBirthTime() {
		return birthTime;
	}

	public void setBirthTime(Long birthTime) {
		this.birthTime = birthTime;
	}

	public String getBirthTxId() {
		return birthTxId;
	}

	public void setBirthTxId(String birthTxId) {
		this.birthTxId = birthTxId;
	}

	public List<String> getFids() {
		return fids;
	}

	public void setFids(List<String> fids) {
		this.fids = fids;
	}
}
