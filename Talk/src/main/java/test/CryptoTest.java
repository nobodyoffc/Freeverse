package test;

import core.crypto.Encryptor;
import data.fcData.AlgorithmId;
import utils.BytesUtils;
import org.bitcoinj.core.ECKey;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CryptoTest {

    public static void main(String[] args) throws InterruptedException {
        byte[] key = BytesUtils.getRandomBytes(32);
        byte[] msg = "hello world!".getBytes();
        ECKey ecKey = new ECKey();
        byte[] pubkey = ecKey.getPubKey();
        // byte[] prikey = ecKey.getPrivKeyBytes();
        Encryptor encryptor;

        List<Long> symList = new ArrayList<>();
        List<Long> oneList = new ArrayList<>();
        List<Long> twoList = new ArrayList<>();

        int n=20;
        long start;
        start = System.currentTimeMillis();
        encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);

        encryptor.encryptBySymkey(msg, key);
        for(int i=0;i<n;i++) {

//            System.out.println("Symkey time:" + (System.currentTimeMillis() - start));

            TimeUnit.SECONDS.sleep(1);
            start = System.currentTimeMillis();
            encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            encryptor.encryptByAsyOneWay(msg, pubkey);
            oneList.add((System.currentTimeMillis() - start));

//            System.out.println("AsyOneWay time:" + (System.currentTimeMillis() - start));

            TimeUnit.SECONDS.sleep(1);
            start = System.currentTimeMillis();
            encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            encryptor.encryptByAsyTwoWay(msg, key, pubkey);
            twoList.add((System.currentTimeMillis() - start));

//            System.out.println("AsyTwoWay time:" + (System.currentTimeMillis() - start));

            TimeUnit.SECONDS.sleep(1);
            start = System.currentTimeMillis();
            encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);

            encryptor.encryptBySymkey(msg, key);
            symList.add((System.currentTimeMillis() - start));
        }
        long sum=0;
        for(Long v :symList ){
            sum+=v;
        }
        System.out.println(sum/n);

        long sum1=0;
        for(Long v :oneList ){
            sum1+=v;
        }
        System.out.println(sum1/n);

        long sum2=0;
        for(Long v :twoList ){
            sum2+=v;
        }
        System.out.println(sum2/n);
    }
}
