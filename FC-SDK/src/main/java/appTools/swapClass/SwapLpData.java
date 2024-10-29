package appTools.swapClass;

import java.util.HashMap;
import java.util.Map;

public class SwapLpData {
    private String sid;
    private Map<String,Double> gLpRawMap;
    private Map<String,Double> gLpNetMap;
    private Map<String,Double> gLpShareMap;
    private Map<String,Double> mLpRawMap;
    private Map<String,Double> mLpNetMap;
    private Map<String,Double> mLpShareMap;
    private double gLpRawSum;
    private double mLpRawSum;
    private double gServiceFee;
    private double mServiceFee;

    public SwapLpData() {
        this.gLpRawMap = new HashMap<>();
        this.gLpNetMap = new HashMap<>();
        this.gLpShareMap = new HashMap<>();
        this.mLpRawMap = new HashMap<>();
        this.mLpNetMap = new HashMap<>();
        this.mLpShareMap = new HashMap<>();
    }

    public void calcGLpRawSum() {
        double gLpSum=0;
        for(String addr: gLpRawMap.keySet()) gLpSum += gLpRawMap.get(addr);
        this.gLpRawSum =gLpSum;
    }
    public void calcMLpRawSum() {
        double mLpSum=0;
        for(String addr: mLpRawMap.keySet()) mLpSum += mLpRawMap.get(addr);
        this.mLpRawSum =mLpSum;
    }

    public Map<String, Double> getgLpNetMap() {
        return gLpNetMap;
    }

    public void setgLpNetMap(Map<String, Double> gLpNetMap) {
        this.gLpNetMap = gLpNetMap;
    }

    public Map<String, Double> getgLpShareMap() {
        return gLpShareMap;
    }

    public void setgLpShareMap(Map<String, Double> gLpShareMap) {
        this.gLpShareMap = gLpShareMap;
    }

    public Map<String, Double> getmLpNetMap() {
        return mLpNetMap;
    }

    public void setmLpNetMap(Map<String, Double> mLpNetMap) {
        this.mLpNetMap = mLpNetMap;
    }

    public Map<String, Double> getmLpShareMap() {
        return mLpShareMap;
    }

    public void setmLpShareMap(Map<String, Double> mLpShareMap) {
        this.mLpShareMap = mLpShareMap;
    }

    public double getgLpRawSum() {
        return gLpRawSum;
    }

    public void setgLpRawSum(double gLpRawSum) {
        this.gLpRawSum = gLpRawSum;
    }

    public double getmLpRawSum() {
        return mLpRawSum;
    }

    public void setmLpRawSum(double mLpRawSum) {
        this.mLpRawSum = mLpRawSum;
    }

    public Map<String, Double> getgLpRawMap() {
        return gLpRawMap;
    }

    public void setgLpRawMap(Map<String, Double> gLpRawMap) {
        this.gLpRawMap = gLpRawMap;
    }

    public Map<String, Double> getmLpRawMap() {
        return mLpRawMap;
    }

    public void setmLpRawMap(Map<String, Double> mLpRawMap) {
        this.mLpRawMap = mLpRawMap;
    }

    public double getgServiceFee() {
        return gServiceFee;
    }

    public void setgServiceFee(double gServiceFee) {
        this.gServiceFee = gServiceFee;
    }

    public double getmServiceFee() {
        return mServiceFee;
    }

    public void setmServiceFee(double mServiceFee) {
        this.mServiceFee = mServiceFee;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }
}
