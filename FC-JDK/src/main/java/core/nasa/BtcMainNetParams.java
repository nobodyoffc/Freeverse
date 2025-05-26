package core.nasa;

import org.bitcoinj.params.MainNetParams;

public class BtcMainNetParams extends MainNetParams {

    public static org.bitcoinj.params.MainNetParams MAINNETWORK = new org.bitcoinj.params.MainNetParams();

    public  BtcMainNetParams() {
        this.addressHeader = 0;
    }
}
