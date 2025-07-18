package data.fchData;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import constants.Constants;
import constants.IndicesNames;
import constants.Strings;

import data.fcData.FcObject;
import utils.DateUtils;
import utils.FchUtils;
import utils.JsonUtils;
import utils.NumberUtils;
import clients.NaSaClient.NaSaRpcClient;

import java.io.IOException;
import java.util.*;

@SuppressWarnings("unused")
public class FchChainInfo extends FcObject {
    public static final long MAX_REQUEST_COUNT = 1000;
    public static final long DEFAULT_COUNT = 100;
    private String time;
    private String height;
    private String blockId;
    private String totalSupply;
    private String circulating;
    private String difficulty;
    private String hashRate;
    private String chainSize;
    private String coinbaseMine;
    private String coinbaseFund;
    private final String initialCoinbaseMine= Constants.INITIAL_COINBASE_MINE;
    private final String initialCoinbaseFund= Constants.INITIAL_COINBASE_FUND;
    private final String mineReductionRatio = Constants.MINE_REDUCTION_RATIO;
    private final String fundReductionRatio = Constants.FUND_REDUCTION_RATIO;
    private final String reducePerBlocks = Constants.REDUCE_PER_BLOCKS;
    private final String reductionStopsAtHeight = Constants.REDUCTION_STOPS_AT_HEIGHT;
    private final String stableAnnualIssuance = Constants.STABLE_ANNUAL_ISSUANCE;
    private final String mineMatureDays = Constants.MINE_MATURE_DAYS;
    private final String fundMatureDays = Constants.FUND_MATURE_DAYS;
    private final String daysPerYear = Constants.DAYS_PER_YEAR_STR;
    private final String blockTimeMinute = Constants.BLOCK_TIME_MINUTE;
    private final String genesisBlockId = Constants.GENESIS_BLOCK_ID;
    private final String startTime = DateUtils.longToTime((Constants.START_TIME) *1000,DateUtils.LONG_FORMAT);
    private String year;
    private String daysToNextYear;
    private String heightOfNextYear;
//
//    public static void main(String[] args) throws IOException {
//
//        long height1 = 2000000;
//        ChainInfo freecashInfo = new ChainInfo();
//        freecashInfo.infoBest("http://localhost:8332","username","password");
//        System.out.println(freecashInfo.toNiceJson());
//
//        ChainInfo freecashInfo1 = new ChainInfo();
//        NewEsClient newEsClient = new NewEsClient();
//        ElasticsearchClient esClient = newEsClient.getSimpleEsClient();
//        freecashInfo1.infoByHeight(height1,esClient);
//        System.out.println(freecashInfo1.toNiceJson());
//
//        Map<Long, String> timeDiffMap = difficultyHistory(0, 1704321137,100 ,esClient);
//        JsonTools.gsonPrint(timeDiffMap);
//
//        Map<Long, String> timeHashRateMap = hashRateHistory(0, 1704321137,18 ,esClient);
//        JsonTools.gsonPrint(timeHashRateMap);
//
//        Map<Long, Long> blockTimefMap = blockTimeHistory(0, 1704321137,1000 ,esClient);
//
//        System.out.println(timeDiffMap.size());
//        System.out.println(timeHashRateMap.size());
//        System.out.println(blockTimefMap.size());
//
//        newEsClient.shutdownClient();
//    }

    public String toNiceJson(){
        return JsonUtils.toNiceJson(this);
    }

    public static Map<Long,Long> blockTimeHistory(long startTime, long endTime, long count, ElasticsearchClient esClient){
        if(count>0)count += 1;
        else count = DEFAULT_COUNT+1;
        if(count>MAX_REQUEST_COUNT)count=MAX_REQUEST_COUNT;
        SearchResponse<Block> result = getBlockListHistory(startTime, endTime, count,esClient);
        if (result == null) return null;

        Map<Long,Long> timeTimeMap = new HashMap<>();
        long lastBlockTime = 0;
        long lastHeight = 0;

        for(Hit<Block> hit:result.hits().hits()){
            Block block = hit.source();
            if(block==null)continue;
            if(lastBlockTime!=0){
                timeTimeMap.put(block.getTime(),(block.getTime()-lastBlockTime)/(block.getHeight()-lastHeight));
            }
            lastBlockTime = block.getTime();
            lastHeight = block.getHeight();
        }
        return timeTimeMap;
    }


    public static Map<Long,String> difficultyHistory(long startTime, long endTime, long count, ElasticsearchClient esClient){

        SearchResponse<Block> result = getBlockListHistory(startTime, endTime, count,esClient);
        if (result == null) return null;

        Map<Long,String> timeDiffMap = new LinkedHashMap<>();

        for(Hit<Block> hit:result.hits().hits()){
            Block block = hit.source();
            if(block==null)continue;
            String diff = NumberUtils.numberToPlainString(String.valueOf(utils.FchUtils.bitsToDifficulty(block.getBits())),"3");
            timeDiffMap.put(block.getTime(),diff);
        }
        return timeDiffMap;
    }

    public static Map<Long,String> hashRateHistory(long startTime, long endTime, long count, ElasticsearchClient esClient){
        SearchResponse<Block> result = getBlockListHistory(startTime, endTime, count,esClient);
        if (result == null) return null;

        Map<Long,String> timeHashRateMap = new LinkedHashMap<>();

        for(Hit<Block> hit:result.hits().hits()){
            Block block = hit.source();
            if(block==null)continue;
            String hashRate = NumberUtils.numberToPlainString(String.valueOf(FchUtils.bitsToHashRate(block.getBits())),"0");
            timeHashRateMap.put(block.getTime(),hashRate);
        }
        return timeHashRateMap;
    }

    private static SearchResponse<Block> getBlockListHistory(long startTime, long endTime, long count, ElasticsearchClient esClient) {
        if(count>MAX_REQUEST_COUNT)count=MAX_REQUEST_COUNT;
        if(count <=0)count=DEFAULT_COUNT;
        if(startTime==0)startTime=Constants.START_TIME;
        if(endTime==0)endTime=System.currentTimeMillis()/1000;

        List<FieldValue> heightList = new ArrayList<>();
        if(startTime< Constants.START_TIME)startTime= Constants.START_TIME;
        long startHeight = estimateHeight(startTime);
        long endHeight = estimateHeight(endTime);
        long step = (endHeight-startHeight) / count;

        long height = startHeight;

        while (height<endHeight) {
            heightList.add(FieldValue.of(height));
            height +=step;
        }
        heightList.add(FieldValue.of(endHeight));
        SearchResponse<Block> result;

        try {
            result = esClient.search(s->s.index(IndicesNames.BLOCK)
                            .size(heightList.size())
                            .sort(s1->s1.field(f->f.field(Strings.HEIGHT)
                                    .order(SortOrder.Asc)))
                            .query(q->q.terms(t->t
                                    .field(Strings.HEIGHT)
                                    .terms(t1->t1.value(heightList))))
                    ,Block.class);
        } catch (IOException e) {
            return null;
        }
        return result;
    }

    private static long estimateHeight(long startTime) {
        if(startTime< Constants.START_TIME)return -1;
        return (startTime - Constants.START_TIME) / 60;
    }

    public void infoBest(NaSaRpcClient naSaRpcClient){
        NaSaRpcClient.BlockchainInfo blockchainInfo = naSaRpcClient.getBlockchainInfo();
        this.height= String.valueOf(blockchainInfo.getBlocks());
        this.blockId=blockchainInfo.getBestblockhash();
        this.time = DateUtils.longToTime(((long)blockchainInfo.getMediantime())*1000,DateUtils.LONG_FORMAT);

        this.difficulty= NumberUtils.numberToPlainString(String.valueOf(blockchainInfo.getDifficulty()),"0");
        double hashRate = utils.FchUtils.difficultyToHashRate(blockchainInfo.getDifficulty());
        this.hashRate= NumberUtils.numberToPlainString(String.valueOf(hashRate),"0");
        this.chainSize= NumberUtils.numberToPlainString(String.valueOf(blockchainInfo.getSize_on_disk()),null);

        infoByHeight(Long.parseLong(this.height));

    }
    public void infoByHeight(long height, ElasticsearchClient esClient){
        this.height= String.valueOf(height);
        Block block;
        try {
            SearchResponse<Block> result = esClient.search(s -> s.index(IndicesNames.BLOCK).query(q -> q.term(t -> t.field(Strings.HEIGHT).value(height))), Block.class);
            if(!result.hits().hits().isEmpty()){
                block = result.hits().hits().get(0).source();
                if(block==null){
                    System.out.println("Failed to get block information from ES.");
                    return;
                }
                double difficulty = utils.FchUtils.bitsToDifficulty(block.getBits());
                double hashRate = utils.FchUtils.difficultyToHashRate(difficulty);
                this.difficulty= NumberUtils.numberToPlainString(String.valueOf(difficulty),"0");
                this.hashRate= NumberUtils.numberToPlainString(String.valueOf(hashRate),"0");
                this.blockId=block.getId();
                this.time =DateUtils.longToTime(((long)block.getTime()) *1000,DateUtils.LONG_FORMAT);
            }
        } catch (IOException e) {
            System.out.println("Failed to get block information from ES.");
        }
        infoByHeight(height);
    }
    public void infoByHeight(long height){

        double totalSupply = 0;
        double circulating = 0;
        double coinbaseMine = 25;
        double coinbaseFund = 25;
        long blockPerYear = Long.parseLong(Constants.DAYS_PER_YEAR_STR)*24*60;
        height = height+1;

        long years = height / blockPerYear;

        for(int i=0;i<years;i++){
            totalSupply += blockPerYear * (coinbaseMine+coinbaseFund);
            if(years<40) {
                coinbaseMine *= 0.8;
                coinbaseFund *= 0.5;
            }
        }
        totalSupply += height % blockPerYear * (coinbaseMine+coinbaseFund);
        this.totalSupply = NumberUtils.numberToPlainString(String.valueOf(totalSupply),"0");
        this.year= String.valueOf(years+1);
        this.coinbaseMine= NumberUtils.numberToPlainString(String.valueOf(coinbaseMine),"8");//String.valueOf(NumberTools.roundDouble8(coinbaseMine));
        this.coinbaseFund= NumberUtils.numberToPlainString(String.valueOf(coinbaseFund),"8");
        long blocksRemainingThisYear = blockPerYear - height % blockPerYear;
        long daysToNextYear = blocksRemainingThisYear / (24 * 60);
        this.daysToNextYear=String.valueOf(daysToNextYear);
        heightOfNextYear=String.valueOf(blocksRemainingThisYear+height);

        long daysImmatureThisYear = 400-daysToNextYear;
        if(daysImmatureThisYear > 100)daysImmatureThisYear=100;
        long daysImmatureLastYear = 100-daysImmatureThisYear;

        circulating = totalSupply
                -(daysImmatureThisYear*1440*(coinbaseMine+coinbaseFund))
                -(daysImmatureLastYear*1440*(coinbaseMine/0.8+coinbaseFund/0.5));
        this.circulating = NumberUtils.numberToPlainString(String.valueOf(circulating),"0");
    }

    public String getTotalSupply() {
        return totalSupply;
    }

    public void setTotalSupply(String totalSupply) {
        this.totalSupply = totalSupply;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getHashRate() {
        return hashRate;
    }

    public void setHashRate(String hashRate) {
        this.hashRate = hashRate;
    }

    public long estimateHeight() {
        return Long.parseLong(height);
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public String getChainSize() {
        return chainSize;
    }

    public void setChainSize(String chainSize) {
        this.chainSize = chainSize;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getCoinbaseMine() {
        return coinbaseMine;
    }

    public void setCoinbaseMine(String coinbaseMine) {
        this.coinbaseMine = coinbaseMine;
    }

    public String getCoinbaseFund() {
        return coinbaseFund;
    }

    public void setCoinbaseFund(String coinbaseFund) {
        this.coinbaseFund = coinbaseFund;
    }

    public String getDaysToNextYear() {
        return daysToNextYear;
    }

    public void setDaysToNextYear(String daysToNextYear) {
        this.daysToNextYear = daysToNextYear;
    }

    public String getHeightOfNextYear() {
        return heightOfNextYear;
    }

    public void setHeightOfNextYear(String heightOfNextYear) {
        this.heightOfNextYear = heightOfNextYear;
    }

    public String getDaysPerYear() {
        return Constants.DAYS_PER_YEAR_STR;
    }

    public String getMineMutualDays() {
        return Constants.MINE_MATURE_DAYS;
    }

    public String getFundMutualDays() {
        return Constants.FUND_MATURE_DAYS;
    }

    public String getBlockTimeMinute() {
        return Constants.BLOCK_TIME_MINUTE;
    }

    public String getInitialCoinbaseMine() {
        return Constants.INITIAL_COINBASE_MINE;
    }
    public String getInitialCoinbaseFund() {
        return Constants.INITIAL_COINBASE_FUND;
    }

    public String getMineReductionRatio() {
        return Constants.MINE_REDUCTION_RATIO;
    }

    public long getStartTime() {
        return Constants.START_TIME;
    }

    public String getGenesisBlockId() {
        return genesisBlockId;
    }

    public String getCirculating() {
        return circulating;
    }

    public void setCirculating(String circulating) {
        this.circulating = circulating;
    }

    public String getMineMatureDays() {
        return Constants.MINE_MATURE_DAYS;
    }

    public String getFundMatureDays() {
        return Constants.FUND_MATURE_DAYS;
    }

    public String getFundReductionRatio() {
        return Constants.FUND_REDUCTION_RATIO;
    }

    public String getReducePerBlocks() {
        return Constants.REDUCE_PER_BLOCKS;
    }

    public String getReductionStopsAtHeight() {
        return Constants.REDUCTION_STOPS_AT_HEIGHT;
    }

    public String getStableAnnualIssuance() {
        return Constants.STABLE_ANNUAL_ISSUANCE;
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
}
