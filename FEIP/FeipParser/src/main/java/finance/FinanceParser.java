package finance;

import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import constants.OpNames;
import data.fcData.News;
import data.feipData.*;
import utils.EsUtils;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import constants.IndicesNames;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.google.gson.Gson;

import core.crypto.KeyTools;
import data.fchData.OpReturn;
import utils.NumberUtils;
import startFEIP.StartFEIP;
import utils.StringUtils;

import java.math.RoundingMode;
import java.util.*;

import static constants.OpNames.*;
import static constants.Values.*;

public class FinanceParser {

    public TokenHistory makeToken(OpReturn opre, Feip feip) {

        Gson gson = new Gson();
        TokenOpData tokenRaw = new TokenOpData();

        try {
            tokenRaw = gson.fromJson(gson.toJson(feip.getData()), TokenOpData.class);
            if(tokenRaw==null)
            {
                System.out.println("Token raw is null");
                return null;
            }
        }catch(com.google.gson.JsonSyntaxException e) {
            System.out.println("Json syntax exception");
            return null;
        }

        TokenHistory tokenHist = new TokenHistory();

        if(tokenRaw.getOp()==null){
            System.out.println("Op is null");
            return null;
        }

        tokenHist.setOp(tokenRaw.getOp());
        tokenHist.setCdd(opre.getCdd());

        switch(tokenRaw.getOp()) {
            case OpNames.DEPLOY:
                if (opre.getHeight() > StartFEIP.CddCheckHeight) {
                    Long cdd = opre.getCdd();
                    if (cdd == null || cdd < StartFEIP.CddRequired * 100L) {
                        System.out.println("Height is greater than CddCheckHeight and Cdd is null or less than CddRequired * 100");
                        return null;
                    }
                }
                if(tokenRaw.getName()==null){
                    System.out.println("Name is null");
                    return null;
                }
                setTxInfo(opre, tokenHist);

                tokenHist.setTokenId(opre.getId());
                if(tokenRaw.getName()!=null)tokenHist.setName(tokenRaw.getName());
                if(tokenRaw.getDesc()!=null)tokenHist.setDesc(tokenRaw.getDesc());
                if(tokenRaw.getConsensusId()!=null)tokenHist.setConsensusId(tokenRaw.getConsensusId());
                if(tokenRaw.getCapacity()!=null)tokenHist.setCapacity(tokenRaw.getCapacity());

                if(tokenRaw.getDecimal()!=null){
                    if (!NumberUtils.isInt(tokenRaw.getDecimal())){
                        System.out.println("Decimal is not an integer");
                        return null;
                    }
                    tokenHist.setDecimal(tokenRaw.getDecimal());
                }

                if(tokenRaw.getTransferable()!=null){
                    tokenHist.setTransferable(tokenRaw.getTransferable());
                }

                if(tokenRaw.getClosable()!=null){
                    tokenHist.setClosable(tokenRaw.getClosable());
                }

                if(tokenRaw.getOpenIssue()!=null){
                    tokenHist.setOpenIssue(tokenRaw.getOpenIssue());
                }

                if(tokenRaw.getMaxAmtPerIssue()!=null)tokenHist.setMaxAmtPerIssue(tokenRaw.getMaxAmtPerIssue());
                if(tokenRaw.getMinCddPerIssue()!=null)tokenHist.setMinCddPerIssue(tokenRaw.getMinCddPerIssue());
                if(tokenRaw.getMaxIssuesPerAddr()!=null)tokenHist.setMaxIssuesPerAddr(tokenRaw.getMaxIssuesPerAddr());
                break;

            case OpNames.ISSUE:
                if(tokenRaw.getTokenId()==null){
                    System.out.println("Token id is null");
                    return null;
                }
                tokenHist.setTokenId(tokenRaw.getTokenId());
                setTxInfo(opre,tokenHist);
                if(tokenRaw.getIssueTo()==null){
                    System.out.println("Issue to is null");
                    return null;
                }
                tokenHist.setIssueTo(tokenRaw.getIssueTo());
                break;

            case OpNames.TRANSFER:
                if(tokenRaw.getTokenId()==null){
                    System.out.println("Token id is null");
                    return null;
                }
                tokenHist.setTokenId(tokenRaw.getTokenId());
                setTxInfo(opre, tokenHist);
                if(tokenRaw.getTransferTo()==null){
                    System.out.println("Transfer to is null");
                    return null;
                }
                tokenHist.setTransferTo(tokenRaw.getTransferTo());
                break;

            case OpNames.DESTROY:
                if(tokenRaw.getTokenIds()==null||tokenRaw.getTokenIds().isEmpty()){
                    System.out.println("Token ids is null or empty");
                    return null;
                }
                if(tokenRaw.getTokenIds().size()!=1){
                    System.out.println("Destroy requires exactly one tokenId in tokenIds");
                    return null;
                }
                tokenHist.setTokenIds(tokenRaw.getTokenIds());
                tokenHist.setTokenId(tokenRaw.getTokenIds().get(0));
                setTxInfo(opre, tokenHist);
                break;
            case OpNames.CLOSE:
                if(tokenRaw.getTokenIds()==null||tokenRaw.getTokenIds().isEmpty()){
                    System.out.println("Token ids is null or empty");
                    return null;
                }
                tokenHist.setTokenIds(tokenRaw.getTokenIds());
                setTxInfo(opre, tokenHist);
                break;

            default:
                System.out.println("Invalid operation");
                return null;
        }

        return tokenHist;
    }

    public static void setTxInfo(OpReturn opre, TokenHistory tokenHist) {
        if(tokenHist==null){
            System.out.println("Token history is null");
            return;
        }
        tokenHist.setId(opre.getId());
        tokenHist.setHeight(opre.getHeight());
        tokenHist.setIndex(opre.getTxIndex());
        tokenHist.setTime(opre.getTime());
        tokenHist.setSigner(opre.getSigner());
        tokenHist.setRecipient(opre.getRecipient());
    }

    public ProofHistory makeProof(OpReturn opre, Feip feip) {

        Gson gson = new Gson();
        ProofOpData proofRaw = new ProofOpData();

        try {
            proofRaw = gson.fromJson(gson.toJson(feip.getData()), ProofOpData.class);
            if(proofRaw==null){
                System.out.println("Proof raw is null");
                return null;
            }
        }catch(com.google.gson.JsonSyntaxException e) {
            System.out.println("Json syntax exception");
            return null;
        }

        ProofHistory proofHist = new ProofHistory();

        if(proofRaw.getOp()==null){
            System.out.println("Op is null");
            return null;
        }

        proofHist.setOp(proofRaw.getOp());

        switch(proofRaw.getOp()) {
            case OpNames.ISSUE:
                if(proofRaw.getTitle()==null){
                    System.out.println("Title is null");
                    return null;
                }
                if(proofRaw.getContent()==null){
                    System.out.println("Content is null");
                    return null;
                }
                if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100)
                {
                    System.out.println("Height is greater than CddCheckHeight and Cdd is less than CddRequired");
                    return null;
                }
                proofHist.setId(opre.getId());
                proofHist.setProofId(opre.getId());
                proofHist.setHeight(opre.getHeight());
                proofHist.setIndex(opre.getTxIndex());
                proofHist.setTime(opre.getTime());
                proofHist.setSigner(opre.getSigner());
                proofHist.setRecipient(opre.getRecipient());

                proofHist.setTitle(proofRaw.getTitle());
                proofHist.setContent(proofRaw.getContent());

                if(proofRaw.getCosigners()!=null)
                        proofHist.setCosigners(proofRaw.getCosigners());
                proofHist.setTransferable(proofRaw.isTransferable());

                proofHist.setAllSignsRequired(proofRaw.isAllSignsRequired());
                break;
            case OpNames.SIGN:
            case OpNames.DESTROY:
                if(proofRaw.getProofIds()==null){
                    System.out.println("Proof IDs is null");
                    return null;
                }
                proofHist.setProofIds(proofRaw.getProofIds());
                proofHist.setId(opre.getId());
                proofHist.setHeight(opre.getHeight());
                proofHist.setIndex(opre.getTxIndex());
                proofHist.setTime(opre.getTime());
                proofHist.setSigner(opre.getSigner());
                break;
            case OpNames.TRANSFER:
                if(proofRaw.getProofId()==null){
                    System.out.println("Proof id is null");
                    return null;
                }
                proofHist.setProofId(proofRaw.getProofId());
                proofHist.setId(opre.getId());
                proofHist.setHeight(opre.getHeight());
                proofHist.setIndex(opre.getTxIndex());
                proofHist.setTime(opre.getTime());
                proofHist.setSigner(opre.getSigner());
                proofHist.setRecipient(opre.getRecipient());
                break;
            default:
                System.out.println("Invalid operation");
                return null;
        }

        return proofHist;
    }


    public boolean parseToken(ElasticsearchClient esClient, TokenHistory tokenHist) throws Exception {
        if(tokenHist==null || tokenHist.getOp()==null){
            System.out.println("Token history is null or op is null");
            return false;
        }
        Token token;
        switch(tokenHist.getOp()) {
            case OpNames.DEPLOY:
                token = EsUtils.getById(esClient, IndicesNames.TOKEN, tokenHist.getTokenId(), Token.class);
                if(token!=null){
                    System.out.println("Token already exists");
                    return false;
                }

                token = new Token();

                token.setId(tokenHist.getId());
                if(tokenHist.getName()!=null)token.setName(tokenHist.getName());
                if(tokenHist.getDesc()!=null)token.setDesc(tokenHist.getDesc());
                if(tokenHist.getConsensusId()!=null)token.setConsensusId(tokenHist.getConsensusId());
                if(tokenHist.getCapacity()!=null)token.setCapacity(tokenHist.getCapacity());
                if(tokenHist.getDecimal()!=null) token.setDecimal(tokenHist.getDecimal());
                else token.setDecimal("0");
                if(tokenHist.getTransferable()!=null)token.setTransferable(tokenHist.getTransferable());
                else token.setTransferable(Boolean.FALSE);
                if(tokenHist.getClosable()!=null)token.setClosable(tokenHist.getClosable());
                else token.setClosable(Boolean.FALSE);
                if(tokenHist.getOpenIssue()!=null) {
                    token.setOpenIssue(tokenHist.getOpenIssue());
                    if (token.getOpenIssue().equals(Boolean.TRUE)) {
                        if (tokenHist.getMaxAmtPerIssue() != null)
                            token.setMaxAmtPerIssue(tokenHist.getMaxAmtPerIssue());
                        if (tokenHist.getMinCddPerIssue() != null)
                            token.setMinCddPerIssue(tokenHist.getMinCddPerIssue());
                        if (tokenHist.getMaxIssuesPerAddr() != null)
                            token.setMaxIssuesPerAddr(tokenHist.getMaxIssuesPerAddr());
                    }
                }else token.setOpenIssue(Boolean.FALSE);

                token.setClosed(Boolean.FALSE);

                token.setDeployer(tokenHist.getSigner());

                token.setBirthTime(tokenHist.getTime());
                token.setBirthHeight(tokenHist.getHeight());

                updateTokenLastInfo(tokenHist, token);

                Token token1=token;

                IndexResponse result =esClient.index(i->i.index(IndicesNames.TOKEN).id(tokenHist.getTokenId()).document(token1));
                System.out.println(result.result());
                // Create News
                News.createNews(esClient, tokenHist.getId(), tokenHist.getSigner(), CREATE, Feip.FeipProtocol.TOKEN.getName(),
                        tokenHist.getId(), tokenHist.getName(), tokenHist.getDesc(), tokenHist.getHeight(), tokenHist.getTime());

                return true;

            case OpNames.ISSUE:
                token = EsUtils.getById(esClient, IndicesNames.TOKEN, tokenHist.getTokenId(), Token.class);
                if(token==null || Boolean.TRUE.equals(token.getClosed())){
                    System.out.println("Token is null or closed");
                    return false;
                }
                if(Boolean.FALSE.equals(token.getOpenIssue()) && !tokenHist.getSigner().equals(token.getDeployer())){
                    System.out.println("Token open issue is false and signer is not deployer");
                    return false;
                }

                ArrayList<String> tokenRecipientIdListIssue = new ArrayList<>();
                Map<String,Double> receiverAmountMapIssue = new HashMap<>();
                Map<String,String> idReceiverMapIssue = new HashMap<>();
                Double amount = 0d;

                if(tokenHist.getIssueTo()==null){
                    System.out.println("Issue to is null");
                    return false;
                }

                for (TokenHistory.FidAmount issueTo : tokenHist.getIssueTo()) {
                    if(!KeyTools.isGoodFid(issueTo.getFid())){
                        System.out.println("Issue to owner is not good");
                        return false;
                    }
                    if(issueTo.getAmount()==null){
                        System.out.println("Issue to amount is null");
                        return false;
                    }
                    if(isBadDecimal(token, issueTo)){
                        System.out.println("Issue to amount is bad");
                        return false;
                    }
                    amount += issueTo.getAmount();
                    receiverAmountMapIssue.put(issueTo.getFid(),issueTo.getAmount());
                    String tokenHolderId = TokenHolder.getTokenHolderId(issueTo.getFid(), tokenHist.getTokenId());
                    idReceiverMapIssue.put(tokenHolderId,issueTo.getFid());
                    tokenRecipientIdListIssue.add(tokenHolderId);
                }

                if(Boolean.TRUE.equals(token.getOpenIssue())){
                    if(token.getMaxAmtPerIssue()!=null){
                        if(amount>Double.parseDouble(token.getMaxAmtPerIssue())){
                            System.out.println("Amount is greater than max amount per issue");
                            return false;
                        }
                    }
                    if(token.getMinCddPerIssue()!=null){
                        Long histCdd = tokenHist.getCdd();
                        if(histCdd==null || histCdd<Long.parseLong(token.getMinCddPerIssue())){
                            System.out.println("Cdd is null or less than min cdd per issue");
                            return false;
                        }
                    }

                    if(token.getMaxIssuesPerAddr()!=null){
                        long times = Long.parseLong(token.getMaxIssuesPerAddr());
                        SearchResponse<Void> search = esClient.search(s -> s.index(IndicesNames.TOKEN_HISTORY)
                                        .trackTotalHits(tr -> tr.enabled(true))
                                        .size(0)
                                        .query(q -> q.bool(b -> b
                                                .must(m1 -> m1.term(t -> t.field("signer").value(tokenHist.getSigner())))
                                                .must(m3->m3.term(t3->t3.field("tokenId").value(tokenHist.getTokenId())))
                                                .must(m2 -> m2.term(t2 -> t2.field("op").value("issue")))))
                                , void.class);
                        if (search != null) {
                            var totalHits = search.hits().total();
                            if (totalHits != null && totalHits.value() >= times) {
                                System.out.println("Times is greater than max issues per addr");
                                return false;
                            }
                        }
                    }
                }

                double circulating;
                if(token.getCirculating()==null)circulating = amount;
                else circulating = token.getCirculating() + amount;

                if(token.getCapacity()!=null && circulating > Double.parseDouble(token.getCapacity())){
                    System.out.println("Circulating is greater than capacity");
                    return false;
                }
                token.setCirculating(circulating);
                updateTokenLastInfo(tokenHist, token);

                //Set balances of the holders

                EsUtils.MgetResult<TokenHolder> resultMultiGet = EsUtils.getMultiByIdList(esClient, IndicesNames.TOKEN_HOLDER, tokenRecipientIdListIssue, TokenHolder.class);
                if (resultMultiGet == null) {
                    System.out.println("Token holder mget result is null");
                    return false;
                }

                ArrayList<TokenHolder> newHolderList = new ArrayList<>();

                for (String tokenHolderId : resultMultiGet.getMissList()) {
                    TokenHolder tokenHolder = new TokenHolder();

                    tokenHolder.setId(tokenHolderId);
                    String toFid = idReceiverMapIssue.get(tokenHolderId);
                    tokenHolder.setFid(toFid);
                    tokenHolder.setTokenId(tokenHist.getTokenId());
                    tokenHolder.setFirstHeight(tokenHist.getHeight());
                    tokenHolder.setLastHeight(tokenHist.getHeight());

                    Double perFid = receiverAmountMapIssue.get(toFid);
                    tokenHolder.setBalance(perFid != null ? perFid : 0d);
                    newHolderList.add(tokenHolder);
                }

                for( TokenHolder tokenHolder: resultMultiGet.getResultList()) {
                    String fid = tokenHolder.getFid();
                    double oldBalance = tokenHolder.getBalance() != null ? tokenHolder.getBalance() : 0d;
                    tokenHolder.setBalance(oldBalance + receiverAmountMapIssue.get(fid));
                    tokenHolder.setLastHeight(tokenHist.getHeight());
                    newHolderList.add(tokenHolder);
                }

                EsUtils.bulkWriteList(esClient,IndicesNames.TOKEN_HOLDER,newHolderList,tokenRecipientIdListIssue,TokenHolder.class);
                Token finalToken3 = token;
                IndexResponse result1 =  esClient.index(i->i.index(IndicesNames.TOKEN).id(tokenHist.getTokenId()).document(finalToken3));
                System.out.println(result1.result());
                return CREATED.equals(result1.result().jsonValue()) || UPDATED.equals(result1.result().jsonValue());

            case OpNames.TRANSFER:
                token = EsUtils.getById(esClient, IndicesNames.TOKEN, tokenHist.getTokenId(), Token.class);
                if(token==null || Boolean.TRUE.equals(token.getClosed())){
                    System.out.println("Token is null or closed");
                    return false;
                }
                int decimal = Integer.parseInt(token.getDecimal());
                String fromFid = tokenHist.getSigner();
                String tokenHolderId = TokenHolder.getTokenHolderId(fromFid, tokenHist.getTokenId());
                TokenHolder tokenHolder = EsUtils.getById(esClient, IndicesNames.TOKEN_HOLDER, tokenHolderId, TokenHolder.class);
                if(tokenHolder==null){
                    System.out.println("Token holder is null");
                    return false;
                }
                double senderOldBalance = tokenHolder.getBalance() != null ? tokenHolder.getBalance() : 0d;

                ArrayList<TokenHolder> newHolderListTransfer = new ArrayList<>();
                ArrayList<String> tokenHolderIdListTransfer = new ArrayList<>();
                Map<String,String> idReceiverMapTransfer = new HashMap<>();

                double sum = 0;
                Map<String,Double> receiverAmountMap = new HashMap<>();

                for (TokenHistory.FidAmount sendTo : tokenHist.getTransferTo()) {
                    if(!KeyTools.isGoodFid(sendTo.getFid())){
                        System.out.println("Send to owner is not good");
                        return false;
                    }
                    if(sendTo.getAmount()==null){
                        System.out.println("Transfer amount is null");
                        return false;
                    }
                    if(isBadDecimal(token, sendTo)){
                        System.out.println("Send to amount is bad");
                        return false;
                    }
                    String id = TokenHolder.getTokenHolderId(sendTo.getFid(), tokenHist.getTokenId());
                    tokenHolderIdListTransfer.add(id);
                    sum+=sendTo.getAmount();
                    receiverAmountMap.put(sendTo.getFid(),sendTo.getAmount());
                    idReceiverMapTransfer.put(id,sendTo.getFid());
                }

                if(sum>senderOldBalance){
                    System.out.println("Sum is greater than sender old balance");
                    return false;
                }

                tokenHolder.setBalance(NumberUtils.roundDouble(senderOldBalance-sum,decimal,RoundingMode.FLOOR));
                tokenHolder.setLastHeight(tokenHist.getHeight());

                EsUtils.MgetResult<TokenHolder> resultTransfer = EsUtils.getMultiByIdList(esClient, IndicesNames.TOKEN_HOLDER, tokenHolderIdListTransfer, TokenHolder.class);
                if (resultTransfer == null) {
                    System.out.println("Token holder mget result is null");
                    return false;
                }

                for(String id:resultTransfer.getMissList()) {
                    TokenHolder tokenReceiver = new TokenHolder();

                    tokenReceiver.setId(id);
                    String toFid = idReceiverMapTransfer.get(id);
                    tokenReceiver.setFid(toFid);
                    tokenReceiver.setTokenId(tokenHist.getTokenId());
                    tokenReceiver.setFirstHeight(tokenHist.getHeight());
                    tokenReceiver.setLastHeight(tokenHist.getHeight());
                    tokenReceiver.setBalance(receiverAmountMap.get(toFid));

                    newHolderListTransfer.add(tokenReceiver);
                }

                for( TokenHolder tokenReceiver: resultTransfer.getResultList()) {
                    String toFid = tokenReceiver.getFid();
                    double oldBalance = tokenReceiver.getBalance() != null ? tokenReceiver.getBalance() : 0d;
                    tokenReceiver.setBalance(receiverAmountMap.get(toFid) + oldBalance);
                    tokenReceiver.setLastHeight(tokenHist.getHeight());
                    newHolderListTransfer.add(tokenReceiver);
                }

                newHolderListTransfer.add(tokenHolder);
                tokenHolderIdListTransfer.add(tokenHolderId);

                BulkResponse result2 = EsUtils.bulkWriteList(esClient, IndicesNames.TOKEN_HOLDER, newHolderListTransfer, tokenHolderIdListTransfer, TokenHolder.class);
                if(result2==null ||result2.errors()){
                    System.out.println("Failed to bulk write token holder");
                    return false;
                }else{
                    System.out.println("Done");
                    return true;
                }

            case OpNames.DESTROY:

                token = EsUtils.getById(esClient, IndicesNames.TOKEN, tokenHist.getTokenId(), Token.class);
                if(token==null || Boolean.TRUE.equals(token.getClosed())){
                    System.out.println("Token is null or closed");
                    return false;
                }
                decimal = Integer.parseInt(token.getDecimal());
                String tokenReceiverId= TokenHolder.getTokenHolderId(tokenHist.getSigner(), tokenHist.getTokenId());
                TokenHolder tokenHolderDestroy = EsUtils.getById(esClient, IndicesNames.TOKEN_HOLDER, tokenReceiverId, TokenHolder.class);

                if(tokenHolderDestroy==null){
                    System.out.println("Token holder destroy is null");
                    return false;
                }
                if(!tokenHolderDestroy.getFid().equals(tokenHist.getSigner())){
                    System.out.println("Token holder destroy fid is not the same as the signer");
                    return false;
                }

                double balance = tokenHolderDestroy.getBalance() != null ? tokenHolderDestroy.getBalance() : 0d;
                if(balance <=0){
                    System.out.println("Balance is zero or negative");
                    return false;
                }

                tokenHolderDestroy.setBalance(0D);
                tokenHolderDestroy.setLastHeight(tokenHist.getHeight());

                token.setCirculating(NumberUtils.roundDouble(token.getCirculating()-balance,decimal,RoundingMode.FLOOR));
                updateTokenLastInfo(tokenHist, token);

                IndexResponse result3 = esClient.index(i->i.index(IndicesNames.TOKEN_HOLDER).id(tokenReceiverId).document(tokenHolderDestroy));
                if(result3==null || result3.result()==null){
                    System.out.println("Failed to index token holder");
                    return false;
                }
                System.out.println(IndicesNames.TOKEN_HOLDER+":"+result3.result());

                Token finalToken4 = token;
                IndexResponse result4 = esClient.index(i->i.index(IndicesNames.TOKEN).id(tokenHist.getTokenId()).document(finalToken4));
                if(result4==null || result4.result()==null){
                    System.out.println("Failed to index token");
                    return false;
                }
                System.out.println(IndicesNames.TOKEN+":"+result4.result());
                return true;
            case OpNames.CLOSE:
                if (tokenHist.getTokenIds() == null || tokenHist.getTokenIds().isEmpty()) {
                    System.out.println("Token ids is null or empty");
                    return false;
                }
                for (String tid : tokenHist.getTokenIds()) {
                    token = EsUtils.getById(esClient, IndicesNames.TOKEN, tid, Token.class);
                    if(token==null || Boolean.TRUE.equals(token.getClosed())){
                        System.out.println("Token is null or closed: " + tid);
                        return false;
                    }

                    if(!tokenHist.getSigner().equals(token.getDeployer())){
                        System.out.println("Token signer is not the same as the deployer");
                        return false;
                    }

                    token.setClosed(Boolean.TRUE);
                    updateTokenLastInfo(tokenHist, token);

                    Token finalToken = token;
                    IndexResponse result5 = esClient.index(i->i.index(IndicesNames.TOKEN).id(tid).document(finalToken));
                    if(result5==null || result5.result()==null){
                        System.out.println("Failed to index token");
                        return false;
                    }
                    System.out.println(result5.result());
                    if (!CREATED.equals(result5.result().jsonValue()) && !UPDATED.equals(result5.result().jsonValue())) {
                        return false;
                    }
                }

                // Create News
                News.createNews(esClient, tokenHist.getId(), tokenHist.getSigner(), CLOSE, Feip.FeipProtocol.TOKEN.getName(),
                        null, null, StringUtils.listToString(tokenHist.getTokenIds()), tokenHist.getHeight(), tokenHist.getTime());
                return true;
        }
        return false;
    }

    private static void updateTokenLastInfo(TokenHistory tokenHist, Token token) {
        if(tokenHist==null)return;
        token.setLastHeight(tokenHist.getHeight());
        token.setLastTime(tokenHist.getTime());
        token.setLastTxId(tokenHist.getId());
    }

    private static boolean isBadDecimal(Token token, TokenHistory.FidAmount issueTo) {
        try {
            int decimalPlaces = NumberUtils.getDecimalPlaces(issueTo.getAmount());
            int maxDecimal = Integer.parseInt(token.getDecimal());
            return decimalPlaces > maxDecimal;
        }catch (Exception e){
            return true;
        }
    }

    public boolean parseProof(ElasticsearchClient esClient, ProofHistory proofHist) throws Exception {
        if(proofHist==null || proofHist.getOp()==null){
            System.out.println("Proof hist is null or op is null");
            return false;
        }
        Proof proof;
        switch(proofHist.getOp()) {
            case OpNames.ISSUE:
                proof = EsUtils.getById(esClient, IndicesNames.PROOF, proofHist.getProofId(), Proof.class);
                if(proof!=null){
                    System.out.println("Proof already exists");
                    return false;
                }

                proof = new Proof();
                proof.setId(proofHist.getId());
                proof.setTitle(proofHist.getTitle());
                proof.setContent(proofHist.getContent());
                proof.setActive(true);
                proof.setDestroyed(false);

                if(proofHist.getCosigners()!=null && proofHist.getCosigners().size()>0){
                    List<String> cosigners = proofHist.getCosigners();
                    ArrayList<String> cosignerList = new ArrayList<>();
                    for(String signer: cosigners){
                        if (signer.equals(proofHist.getSigner()))continue;
                        cosignerList.add(signer);
                    }
                    proof.setCosignersInvited(cosignerList.toArray(new String[0]));
                    if(Boolean.TRUE.equals(proofHist.isAllSignsRequired()) && proof.getCosignersInvited().length>0)
                        proof.setActive(false);
                }

                proof.setTransferable(proofHist.isTransferable());

                proof.setIssuer(proofHist.getSigner());

                if(proofHist.getRecipient()!=null) {
                    proof.setOwner(proofHist.getRecipient());
                }else proof.setOwner(proofHist.getSigner());

                proof.setBirthTime(proofHist.getTime());
                proof.setBirthHeight(proofHist.getHeight());

                proof.setLastTxId(proofHist.getId());
                proof.setLastTime(proofHist.getTime());
                proof.setLastHeight(proofHist.getHeight());

                Proof proof1=proof;

                IndexResponse result1 =esClient.index(i->i.index(IndicesNames.PROOF).id(proofHist.getProofId()).document(proof1));
                if(result1==null || result1.result()==null){
                    System.out.println("Failed to index proof");
                    return false;
                }
                System.out.println(result1.result());
                return CREATED.equals(result1.result().jsonValue()) || UPDATED.equals(result1.result().jsonValue());

            case OpNames.SIGN:

                proof = EsUtils.getById(esClient, IndicesNames.PROOF, proofHist.getProofId(), Proof.class);

                if(proof==null){
                    System.out.println("Proof is null");
                    return false;
                }

                if(Boolean.TRUE.equals(proof.isDestroyed())){
                    System.out.println("Proof is destroyed");
                    return false;
                }

                if(proof.getCosignersInvited()==null){
                    System.out.println("Cosigners invited is null");
                    return false;
                }

                for(String signer:proof.getCosignersInvited()) {
                    if(proofHist.getSigner().equals(signer)){
                        if(proof.getCosignersSigned()!=null) {
                            for (String signed : proof.getCosignersSigned()) {
                                if (signer.equals(signed)){
                                    System.out.println("Signer is already signed");
                                    return false;
                                }
                            }
                        }
                        String[] cosignerSigned;
                        if(proof.getCosignersSigned()==null) {
                            cosignerSigned = new String[]{signer};
                        }else {
                            cosignerSigned = new String[proof.getCosignersSigned().length+1];
                            for(int i= 0; i<cosignerSigned.length-1;i++) cosignerSigned[i]=proof.getCosignersSigned()[i];
                            cosignerSigned[cosignerSigned.length-1]=signer;
                        }

                        if(cosignerSigned.length==proof.getCosignersInvited().length)proof.setActive(true);

                        proof.setCosignersSigned(cosignerSigned);
                        proof.setLastTxId(proofHist.getId());
                        proof.setLastTime(proofHist.getTime());
                        proof.setLastHeight(proofHist.getHeight());
                        Proof finalProof = proof;
                        IndexResponse result2 = esClient.index(i->i.index(IndicesNames.PROOF).id(proofHist.getProofId()).document(finalProof));
                        if(result2==null || result2.result()==null){
                            System.out.println("Failed to index proof");
                            return false;
                        }
                        System.out.println(result2.result());
                        return CREATED.equals(result2.result().jsonValue()) || UPDATED.equals(result2.result().jsonValue());
                    }
                }
                return false;

            case OpNames.TRANSFER:

                proof = EsUtils.getById(esClient, IndicesNames.PROOF, proofHist.getProofId(), Proof.class);

                if(proof==null){
                    System.out.println("Proof is null");
                    return false;
                }

                if(Boolean.TRUE.equals(proof.isDestroyed())|| Boolean.FALSE.equals(proof.isActive())){
                    System.out.println("Proof is destroyed or not active");
                    return false;
                }

                if(! proof.getOwner().equals(proofHist.getSigner())){
                    System.out.println("Proof owner is not the same as the signer");
                    return false;
                }

                if(proofHist.getRecipient()==null){
                    System.out.println("Recipient is null");
                    return false;
                }

                proof.setOwner(proofHist.getRecipient());

                Proof finalProof1 = proof;
                IndexResponse result3 = esClient.index(i->i.index(IndicesNames.PROOF).id(proofHist.getProofId()).document(finalProof1));
                if(result3==null || result3.result()==null){
                    System.out.println("Failed to index proof");
                    return false;
                }
                System.out.println(result3.result());
                return CREATED.equals(result3.result().jsonValue()) || UPDATED.equals(result3.result().jsonValue());

            case DESTROY:

                if(proofHist.getProofIds()==null||proofHist.getProofIds().isEmpty()){
                    System.out.println("Proof ids is null or empty");
                    return false;
                }

                EsUtils.MgetResult<Proof> result = EsUtils.getMultiByIdList(esClient, IndicesNames.PROOF, proofHist.getProofIds(), Proof.class);
                if(result.getResultList() == null || result.getResultList().isEmpty()){
                    System.out.println("Result is null or result list is null or empty");
                    return false;
                }
                BulkRequest.Builder br = new BulkRequest.Builder();
                for(Proof proofItem:result.getResultList()){
                    if(Boolean.TRUE.equals(proofItem.isDestroyed())){
                        System.out.println("Proof item is destroyed");
                        continue;
                    }

                    if(! proofItem.getOwner().equals(proofHist.getSigner())){
                        System.out.println("Proof item owner is not the same as the signer");
                        continue;
                    }

                    proofItem.setDestroyed(true);
                    proofItem.setActive(false);

                    br.operations(op -> op
                        .index(idx -> idx
                            .index(IndicesNames.PROTOCOL)
                            .id(proofItem.getId())
                            .document(proofItem)
                        )
                    );
                }

                BulkResponse result4 = esClient.bulk(br.build());
                if(result4==null || result4.errors()){
                    System.out.println("Failed to bulk index proof");
                    return false;
                } else System.out.println("Done");
                return  true;
        }
        return false;
    }
}
