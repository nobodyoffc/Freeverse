package test;

import crypto.Encryptor;
import fcData.AlgorithmId;
import javaTools.BytesTools;
import org.bitcoinj.core.ECKey;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CryptoTest {

    public static void main(String[] args) throws InterruptedException {
        byte[] key = BytesTools.getRandomBytes(32);
        byte[] msg = "hello world!".getBytes();
        ECKey ecKey = new ECKey();
        byte[] pubKey = ecKey.getPubKey();
        // byte[] priKey = ecKey.getPrivKeyBytes();
        Encryptor encryptor;

        List<Long> symList = new ArrayList<>();
        List<Long> oneList = new ArrayList<>();
        List<Long> twoList = new ArrayList<>();

        int n=20;
        long start;
        start = System.currentTimeMillis();
        encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);

        encryptor.encryptBySymKey(msg, key);
        for(int i=0;i<n;i++) {

//            System.out.println("SymKey time:" + (System.currentTimeMillis() - start));

            TimeUnit.SECONDS.sleep(1);
            start = System.currentTimeMillis();
            encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            encryptor.encryptByAsyOneWay(msg, pubKey);
            oneList.add((System.currentTimeMillis() - start));

//            System.out.println("AsyOneWay time:" + (System.currentTimeMillis() - start));

            TimeUnit.SECONDS.sleep(1);
            start = System.currentTimeMillis();
            encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            encryptor.encryptByAsyTwoWay(msg, key, pubKey);
            twoList.add((System.currentTimeMillis() - start));

//            System.out.println("AsyTwoWay time:" + (System.currentTimeMillis() - start));

            TimeUnit.SECONDS.sleep(1);
            start = System.currentTimeMillis();
            encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);

            encryptor.encryptBySymKey(msg, key);
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
