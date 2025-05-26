package data.feipData.serviceParams;

import java.io.BufferedReader;

public class TalkParams extends Params {

    
    public void inputParams(BufferedReader br, byte[]symKey){
        inputParams(br,symKey,this.apipClient);
    }
}
