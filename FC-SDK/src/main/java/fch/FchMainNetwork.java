package fch;

import org.bitcoinj.params.MainNetParams;

public class FchMainNetwork extends MainNetParams {

    public static FchMainNetwork MAINNETWORK = new FchMainNetwork();

    public FchMainNetwork() {
        addressHeader = 35;
    }

    public static FchMainNetwork get() {
        return new FchMainNetwork();
    }
}
