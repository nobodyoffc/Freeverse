package data.fchData;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.GetResponse;
import constants.IndicesNames;
import core.crypto.KeyTools;
import data.fcData.FcObject;
import utils.BytesUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class P2SH extends FcObject {
	private String redeemScript;
	private Integer m;
	private Integer n;
	private List<String> pubKeys;
	private List<String> fids;

	private Long birthHeight;
	private Long birthTime;
	private String birthTxId;

	public void parseP2SH(ElasticsearchClient esClient,Cash input) throws ElasticsearchException, IOException {
        /* Example of multiSig input unlocking script:
				"00" +
				"41" +
				"8ec1f75f4368e650f6cf0c8a80c009094748845c9d354f593359bd971370d94b" +
				"a48db3b925dd0869d1610b1c0a4d27f7ac25f35d46b034dbcbd30a6e78110764" +
				"41" +
				"41" +
				"e226dbc949b2f2bfb2fd8cfb7a4851c43700e9febf16873b679e412714ad3235" +
				"b528053dc46990e3bdcc40656764694f403fbce5f59a4e5424140e09e09e87b4" +
				"41" +
				"4c" +
				"69" +
				"52" +
				"21" +
				"030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a" +
				"21" +
				"02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67" +
				"21" +
				"03f0145ddf5debc7169952b17b5c6a8a566b38742b6aa7b33b667c0a7fa73762e2" +
				"53" +
				"ae";
		*/
		String script = input.getUnlockScript();

		GetResponse<P2SH> resultGetP2SH = esClient.get(g->g.index(IndicesNames.MULTISIGN).id(input.getOwner()), P2SH.class);

		if(resultGetP2SH.found())return;

		if(! script.substring(script.length()-2).equals("ae"))return;

		try {
			TimeUnit.SECONDS.sleep(2);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}


		InputStream scriptIs = new ByteArrayInputStream(BytesUtils.hexToByteArray(script));

		byte[] b = new byte[1];
		scriptIs.read(b);

		if(b[0]!=0x00) return;
		scriptIs.read(b);

		while(b[0]==65) {
			scriptIs.skipNBytes(65);
			scriptIs.read(b);
		}

		if(b[0]>75)scriptIs.read();

		ArrayList<byte[]> redeemScriptBytesList = new ArrayList<byte[]>();
		scriptIs.read(b);
		redeemScriptBytesList.add(b.clone());
		int m = b[0]-80;

		if(m>16 || m<0) return;

		ArrayList<String> pukList = new ArrayList<String>();
		ArrayList<String> addrList = new ArrayList<String>();

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
		}

		if(pukList.size()==0) return;

		int n = b[0]-80;
		scriptIs.read(b);
		redeemScriptBytesList.add(b.clone());


		this.setRedeemScript(BytesUtils.bytesToHexStringBE(BytesUtils.bytesMerger(redeemScriptBytesList)));
		this.setM(m);
		this.setN(n);

		this.setPubKeys(pukList);
		this.setFids(addrList);

		this.setId(input.getOwner());
		this.setBirthHeight(input.getSpendHeight());
		this.setBirthTime(input.getSpendTime());
		this.setBirthTxId(input.getBirthTxId());

		esClient.index(i->i.index(IndicesNames.MULTISIGN).id(this.getId()).document(this));
	}

	public static P2SH parseP2shRedeemScript(String script)  {
		P2SH p2sh = new P2SH();

		p2sh.setId(KeyTools.scriptToMultiAddr(script));
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
			p2sh.setRedeemScript(BytesUtils.bytesToHexStringBE(BytesUtils.bytesMerger(redeemScriptBytesList)));
			p2sh.setM(m);
			p2sh.setN(n);

			p2sh.setPubKeys(pukList);
			p2sh.setFids(addrList);
			return p2sh;
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

	public List<String> getPubKeys() {
		return pubKeys;
	}

	public void setPubKeys(List<String> pubKeys) {
		this.pubKeys = pubKeys;
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
