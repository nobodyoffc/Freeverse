package crypto;

import com.google.common.collect.ImmutableBiMap;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.MainNetParams;

import java.math.BigInteger;

public class BchCashAddr {
    //地址类型枚举
    public enum AddressType{
        P2PKH(0),
        P2SH(1);
        private  int addressType;
        AddressType(int type){this.addressType=type;}
        public int getAddressType(){return this.addressType;}
    }
    //prefix 枚举
    public enum Prefix{
        MainNet("bitcoincash"),
        RegTest("bchreg"),
        TestNet("bchtest");

        private  String prefix;
        Prefix(String hrp){this.prefix=hrp;}
        public String getPrefix(){return this.prefix;}
    }
    //该map用于构建version
    private static final ImmutableBiMap<Integer, BigInteger> hashBitMap =
            new ImmutableBiMap.Builder<Integer, BigInteger>()
                    .put(160, BigInteger.valueOf(0))
                    .put(192, BigInteger.valueOf(1))
                    .put(224, BigInteger.valueOf(2))
                    .put(256, BigInteger.valueOf(3))
                    .put(320, BigInteger.valueOf(4))
                    .put(384, BigInteger.valueOf(5))
                    .put(448, BigInteger.valueOf(6))
                    .put(512, BigInteger.valueOf(7))
                    .build();

    //创建legacy，cashaddr
    public static void createLegacyAndCasgAddr(){
        NetworkParameters params = MainNetParams.get();
        ECKey key = new ECKey();
        String cashAddr= createCashAddr(key.getPubKey());
        System.out.println("私钥："+key.getPrivateKeyAsHex());
        System.out.println("Legacy地址："+ Address.fromKey(params,key));
        System.out.println("Cash地址："+cashAddr);
    }
    //创建cashAddr
    public static String createCashAddr(byte[] pubkey){
        byte[] pubHash = Utils.sha256hash160(pubkey);
        return hash160ToCashAddr(pubHash);
    }
    static String hash160ToCashAddr(byte[] hash160) {
        byte[] out = packAddress(AddressType.P2PKH.getAddressType(),hash160);
        String addr = Bech32Address.encode(Prefix.MainNet.getPrefix(),out);
        return addr.substring(addr.indexOf(":")+1);
    }

    /*
     * pack address
     * 主要是添加版本号，还有就是将8位一个字节转换为5位一个字节的表示形式
     * */
    private static byte[] packAddress(int addressType,byte[] pubHash){
        //获取字节的位数
        int hashLenBits = pubHash.length*8;

        BigInteger version = BigInteger.valueOf(addressType).or(hashBitMap.get(hashLenBits));
        byte[] payload = new byte[pubHash.length+1];
        payload[0] = version.byteValue();
        System.arraycopy(pubHash, 0, payload, 1, payload.length - 1);
        //进行8->5位转换
        byte[] out = Bech32Address.toWords(payload);
        return out;
    }

    public static void main(String[] args) {
        createLegacyAndCasgAddr();
    }
}
