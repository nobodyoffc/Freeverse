package finance;

import data.feipData.*;
import utils.EsUtils;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import constants.IndicesNames;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.google.gson.Gson;

import core.crypto.KeyTools;
import data.fchData.OpReturn;
import data.fchData.SendTo;
import utils.NumberUtils;
import startFEIP.StartFEIP;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.*;

import static constants.Values.FALSE;
import static constants.Values.TRUE;

public class FinanceParser {
    
    public TokenHistory makeToken(OpReturn opre, Feip feip) {

        Gson gson = new Gson();
        TokenOpData tokenRaw = new TokenOpData();

        try {
            tokenRaw = gson.fromJson(gson.toJson(feip.getData()), TokenOpData.class);
            if(tokenRaw==null)return null;
        }catch(com.google.gson.JsonSyntaxException e) {
            return null;
        }

        TokenHistory tokenHist = new TokenHistory();

        if(tokenRaw.getOp()==null)return null;

        tokenHist.setOp(tokenRaw.getOp());
        tokenHist.setCdd(opre.getCdd());

        switch(tokenRaw.getOp()) {
            case "deploy":
                if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100)
                    return null;
                if(tokenRaw.getName()==null)return null;
                setTxInfo(opre, tokenHist);

                tokenHist.setTokenId(opre.getId());
                if(tokenRaw.getName()!=null)tokenHist.setName(tokenRaw.getName());
                if(tokenRaw.getDesc()!=null)tokenHist.setDesc(tokenRaw.getDesc());
                if(tokenRaw.getConsensusId()!=null)tokenHist.setConsensusId(tokenRaw.getConsensusId());
                if(tokenRaw.getCapacity()!=null)tokenHist.setCapacity(tokenRaw.getCapacity());

                if(tokenRaw.getDecimal()!=null){
                    if (!NumberUtils.isInt(tokenRaw.getDecimal())) return null;
                    tokenHist.setDecimal(tokenRaw.getDecimal());
                }

                if(tokenRaw.getTransferable()!=null){
                    if (tokenRaw.getTransferable()==null) return null;
                    tokenHist.setTransferable(tokenRaw.getTransferable());
                }

                if(tokenRaw.getClosable()!=null){
                    if (tokenRaw.getClosable()==null) return null;
                    tokenHist.setClosable(tokenRaw.getClosable());
                }

                if(tokenRaw.getOpenIssue()!=null){
                    if (tokenRaw.getOpenIssue()==null) return null;
                    tokenHist.setOpenIssue(tokenRaw.getOpenIssue());
                }

                if(tokenRaw.getMaxAmtPerIssue()!=null)tokenHist.setMaxAmtPerIssue(tokenRaw.getMaxAmtPerIssue());
                if(tokenRaw.getMinCddPerIssue()!=null)tokenHist.setMinCddPerIssue(tokenRaw.getMinCddPerIssue());
                if(tokenRaw.getMaxIssuesPerAddr()!=null)tokenHist.setMaxIssuesPerAddr(tokenRaw.getMaxIssuesPerAddr());
                break;

            case "issue":
                if(tokenRaw.getTokenId()==null) return null;
                tokenHist.setTokenId(tokenRaw.getTokenId());
                setTxInfo(opre,tokenHist);
                if(tokenRaw.getIssueTo()==null)return null;
                tokenHist.setIssueTo(tokenRaw.getIssueTo());
                break;

            case "transfer":
                if(tokenRaw.getTokenId()==null) return null;
                tokenHist.setTokenId(tokenRaw.getTokenId());
                setTxInfo(opre, tokenHist);
                if(tokenRaw.getTransferTo()==null)return null;
                tokenHist.setTransferTo(tokenRaw.getTransferTo());
                break;

            case "destroy","close":
                if(tokenRaw.getTokenIds()==null||tokenRaw.getTokenIds().isEmpty()) return null;
                tokenHist.setTokenIds(tokenRaw.getTokenIds());
                setTxInfo(opre, tokenHist);
                break;

            default:
                return null;
        }

        return tokenHist;
    }

    public static void setTxInfo(OpReturn opre, TokenHistory tokenHist) {
        if(tokenHist==null)return;
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
            if(proofRaw==null)return null;
        }catch(com.google.gson.JsonSyntaxException e) {
            return null;
        }

        ProofHistory proofHist = new ProofHistory();

        if(proofRaw.getOp()==null)return null;

        proofHist.setOp(proofRaw.getOp());

        switch(proofRaw.getOp()) {
            case "issue":
                if(proofRaw.getTitle()==null) return null;
                if(proofRaw.getContent()==null) return null;
                if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100)
                    return null;
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
            case "sign":
            case "destroy":
                if(proofRaw.getProofId()==null) return null;
                proofHist.setProofId(proofRaw.getProofId());
                proofHist.setId(opre.getId());
                proofHist.setHeight(opre.getHeight());
                proofHist.setIndex(opre.getTxIndex());
                proofHist.setTime(opre.getTime());
                proofHist.setSigner(opre.getSigner());
                break;
            case "transfer":
                if(proofRaw.getProofId()==null) return null;
                proofHist.setProofId(proofRaw.getProofId());
                proofHist.setId(opre.getId());
                proofHist.setHeight(opre.getHeight());
                proofHist.setIndex(opre.getTxIndex());
                proofHist.setTime(opre.getTime());
                proofHist.setSigner(opre.getSigner());
                proofHist.setRecipient(opre.getRecipient());
                break;
            default:
                return null;
        }

        return proofHist;
    }

//
//    public boolean parseStatement(ElasticsearchClient esClient, OpReturn opre, Feip feip) throws ElasticsearchException, IOException {
//
//        boolean isValid = false;
//
//        Gson gson = new Gson();
//
//        StatementOpData statementRaw = new StatementOpData();
//
//        try {
//            statementRaw = gson.fromJson(gson.toJson(feip.getData()), StatementOpData.class);
//            if(statementRaw==null)return isValid;
//        }catch(com.google.gson.JsonSyntaxException e) {
//            return isValid;
//        }
//
//        Statement statement = new Statement();
//
//        statement.setId(opre.getId());
//
//        if(statementRaw.getConfirm()==null)return isValid;
//
//        if(!statementRaw.getConfirm().equals("This is a formal and irrevocable statement."))return isValid;
//
//        if(statementRaw.getTitle()==null && statementRaw.getContent()==null)return isValid;
//
//        if(statementRaw.getTitle()!=null) {
//            statement.setTitle(statementRaw.getTitle());
//        }
//
//        if(statementRaw.getContent()!=null) {
//            statement.setContent(statementRaw.getContent());
//        }
//
//        statement.setPublisher(opre.getSigner());
//        statement.setBirthTime(opre.getTime());
//        statement.setBirthHeight(opre.getHeight());
//
//        esClient.index(i->i.index(IndicesNames.STATEMENT).id(statement.getId()).document(statement));
//        isValid = true;
//
//        return isValid;
//    }

    public boolean parseToken(ElasticsearchClient esClient, TokenHistory tokenHist) throws Exception {
        if(tokenHist==null || tokenHist.getOp()==null)return false;
        Token token;
        switch(tokenHist.getOp()) {
            case "deploy":
                token = EsUtils.getById(esClient, IndicesNames.TOKEN, tokenHist.getTokenId(), Token.class);
                if(token!=null)return false;

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

                esClient.index(i->i.index(IndicesNames.TOKEN).id(tokenHist.getTokenId()).document(token1));
                return true;

            case "issue":
                token = EsUtils.getById(esClient, IndicesNames.TOKEN, tokenHist.getTokenId(), Token.class);
                if(token==null || token.getClosed().equals(Boolean.TRUE))return false;
                if(token.getOpenIssue().equals(FALSE) && !tokenHist.getSigner().equals(token.getDeployer()))return false;

                ArrayList<String> tokenRecipientIdListIssue = new ArrayList<>();
                Map<String,Double> receiverAmountMapIssue = new HashMap<>();
                Map<String,String> idReceiverMapIssue = new HashMap<>();
                Double amount = 0d;

                if(tokenHist.getIssueTo()==null)return false;

                for (SendTo issueTo : tokenHist.getIssueTo()) {
                    if(!KeyTools.isGoodFid(issueTo.getFid()))return false;
                    if(isBadDecimal(token, issueTo))return false;
                    amount += issueTo.getAmount();
                    receiverAmountMapIssue.put(issueTo.getFid(),issueTo.getAmount());
                    String tokenHolderId = TokenHolder.getTokenHolderId(issueTo.getFid(), tokenHist.getTokenId());
                    idReceiverMapIssue.put(tokenHolderId,issueTo.getFid());
                    tokenRecipientIdListIssue.add(tokenHolderId);
                }

                if(token.getOpenIssue().equals(Boolean.TRUE)){
                    if(token.getMaxAmtPerIssue()!=null){
                        if(amount>Double.parseDouble(token.getMaxAmtPerIssue()))return false;
                    }
                    if(token.getMinCddPerIssue()!=null){
                        if(tokenHist.getCdd()<Long.parseLong(token.getMinCddPerIssue()))return false;
                    }

                    if(token.getMaxIssuesPerAddr()!=null){
                        long times = Long.parseLong(token.getMaxIssuesPerAddr());
                        SearchResponse<Void> result = esClient.search(s -> s.index(IndicesNames.TOKEN_HISTORY)
                                        .trackTotalHits(tr -> tr.enabled(true))
                                        .size(0)
                                        .query(q -> q.bool(b -> b
                                                .must(m1 -> m1.term(t -> t.field("signer").value(tokenHist.getSigner())))
                                                .must(m3->m3.term(t3->t3.field("tokenId").value(tokenHist.getTokenId())))
                                                .must(m2 -> m2.term(t2 -> t2.field("op").value("issue")))))
                                , void.class);
                        if(result!=null && result.hits().total()!=null){
                            if(result.hits().total().value()>=times)return  false;
                        }
                    }
                }

                double circulating;
                if(token.getCirculating()==null)circulating = amount;
                else circulating = token.getCirculating() + amount;

                if(token.getCapacity()!=null && circulating > Double.parseDouble(token.getCapacity()))return false;
                token.setCirculating(circulating);
                updateTokenLastInfo(tokenHist, token);

                //Set balances of the holders

                EsUtils.MgetResult<TokenHolder> result = EsUtils.getMultiByIdList(esClient, IndicesNames.TOKEN_HOLDER, tokenRecipientIdListIssue, TokenHolder.class);

                ArrayList<TokenHolder> newHolderList = new ArrayList<>();

                for(String tokenHolderId:result.getMissList()) {
                    TokenHolder tokenHolder = new TokenHolder();

                    tokenHolder.setId(tokenHolderId);
                    String toFid = idReceiverMapIssue.get(tokenHolderId);
                    tokenHolder.setFid(toFid);
                    tokenHolder.setTokenId(tokenHist.getTokenId());
                    tokenHolder.setFirstHeight(tokenHist.getHeight());
                    tokenHolder.setLastHeight(tokenHist.getHeight());

                    tokenHolder.setBalance(amount);
                    newHolderList.add(tokenHolder);
                    break;

                }

                for( TokenHolder tokenHolder: result.getResultList()) {
                    String fid = tokenHolder.getFid();
                    double oldBalance = tokenHolder.getBalance();
                    tokenHolder.setBalance(oldBalance + receiverAmountMapIssue.get(fid));
                    tokenHolder.setLastHeight(tokenHist.getHeight());
                    newHolderList.add(tokenHolder);
                }

                EsUtils.bulkWriteList(esClient,IndicesNames.TOKEN_HOLDER,newHolderList,tokenRecipientIdListIssue,TokenHolder.class);
                Token finalToken3 = token;
                esClient.index(i->i.index(IndicesNames.TOKEN).id(tokenHist.getTokenId()).document(finalToken3));
                return true;

            case "transfer":
                token = EsUtils.getById(esClient, IndicesNames.TOKEN, tokenHist.getTokenId(), Token.class);
                if(token==null || token.getClosed().equals(Boolean.TRUE))return false;
                int decimal = Integer.parseInt(token.getDecimal());
                String fromFid = tokenHist.getSigner();
                String tokenHolderId = TokenHolder.getTokenHolderId(fromFid, tokenHist.getTokenId());
                TokenHolder tokenHolder = EsUtils.getById(esClient, IndicesNames.TOKEN_HOLDER, tokenHolderId, TokenHolder.class);
                if(tokenHolder==null)return false;
                double senderOldBalance = tokenHolder.getBalance();

                ArrayList<TokenHolder> newHolderListTransfer = new ArrayList<>();
                ArrayList<String> tokenHolderIdListTransfer = new ArrayList<>();
                Map<String,String> idReceiverMapTransfer = new HashMap<>();

                double sum = 0;
                Map<String,Double> receiverAmountMap = new HashMap<>();

                for (SendTo sendTo : tokenHist.getTransferTo()) {
                    if(!KeyTools.isGoodFid(sendTo.getFid()))return false;
                    if(isBadDecimal(token, sendTo))return false;
                    String id = TokenHolder.getTokenHolderId(sendTo.getFid(), tokenHist.getTokenId());
                    tokenHolderIdListTransfer.add(id);
                    sum+=sendTo.getAmount();
                    receiverAmountMap.put(sendTo.getFid(),sendTo.getAmount());
                    idReceiverMapTransfer.put(id,sendTo.getFid());
                }

                if(sum>senderOldBalance)return false;

                tokenHolder.setBalance(NumberUtils.roundDouble(senderOldBalance-sum,decimal,RoundingMode.FLOOR));
                tokenHolder.setLastHeight(tokenHist.getHeight());

                EsUtils.MgetResult<TokenHolder> resultTransfer = EsUtils.getMultiByIdList(esClient, IndicesNames.TOKEN_HOLDER, tokenHolderIdListTransfer, TokenHolder.class);

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
                    double oldBalance = tokenReceiver.getBalance();
                    tokenReceiver.setBalance(receiverAmountMap.get(toFid) + oldBalance);
                    tokenReceiver.setLastHeight(tokenHist.getHeight());
                    newHolderListTransfer.add(tokenReceiver);
                }

                newHolderListTransfer.add(tokenHolder);
                tokenHolderIdListTransfer.add(tokenHolderId);

                EsUtils.bulkWriteList(esClient,IndicesNames.TOKEN_HOLDER,newHolderListTransfer,tokenHolderIdListTransfer,TokenHolder.class);

                return true;

            case "destroy":

                token = EsUtils.getById(esClient, IndicesNames.TOKEN, tokenHist.getTokenId(), Token.class);
                if(token==null || token.getClosed().equals(Boolean.TRUE))return false;
                decimal = Integer.parseInt(token.getDecimal());
                String tokenReceiverId= TokenHolder.getTokenHolderId(tokenHist.getSigner(), tokenHist.getTokenId());
                TokenHolder tokenHolderDestroy = EsUtils.getById(esClient, IndicesNames.TOKEN_HOLDER, tokenReceiverId, TokenHolder.class);

                if(tokenHolderDestroy==null)return false;
                if(!tokenHolderDestroy.getFid().equals(tokenHist.getSigner()))return false;

                double balance = tokenHolderDestroy.getBalance();
                if(balance <=0)return false;

                tokenHolderDestroy.setBalance(0D);
                tokenHolderDestroy.setLastHeight(tokenHist.getHeight());

                token.setCirculating(NumberUtils.roundDouble(token.getCirculating()-balance,decimal,RoundingMode.FLOOR));
                updateTokenLastInfo(tokenHist, token);

                esClient.index(i->i.index(IndicesNames.TOKEN_HOLDER).id(tokenReceiverId).document(tokenHolderDestroy));

                Token finalToken4 = token;
                esClient.index(i->i.index(IndicesNames.TOKEN).id(tokenHist.getTokenId()).document(finalToken4));
                return  true;

            case "close":

                token = EsUtils.getById(esClient, IndicesNames.TOKEN, tokenHist.getTokenId(), Token.class);
                if(token==null || token.getClosed().equals(Boolean.TRUE))return false;

                if(!tokenHist.getSigner().equals(token.getDeployer()))return false;

                token.setClosed(Boolean.TRUE);
                updateTokenLastInfo(tokenHist, token);

                Token finalToken = token;
                esClient.index(i->i.index(IndicesNames.TOKEN).id(tokenHist.getTokenId()).document(finalToken));
                return  true;
        }
        return false;
    }

    private static void updateTokenLastInfo(TokenHistory tokenHist, Token token) {
        if(tokenHist==null)return;
        token.setLastHeight(tokenHist.getHeight());
        token.setLastTime(tokenHist.getTime());
        token.setLastTxId(tokenHist.getId());
    }

    private static boolean isBadDecimal(Token token, SendTo issueTo) {
        try {
            int decimalPlaces = NumberUtils.getDecimalPlaces(issueTo.getAmount());
            int maxDecimal = Integer.parseInt(token.getDecimal());
            return decimalPlaces > maxDecimal;
        }catch (Exception e){
            return true;
        }
    }

    public boolean parseProof(ElasticsearchClient esClient, ProofHistory proofHist) throws Exception {
        if(proofHist==null || proofHist.getOp()==null)return false;
        Proof proof;
        switch(proofHist.getOp()) {
            case "issue":
                proof = EsUtils.getById(esClient, IndicesNames.PROOF, proofHist.getProofId(), Proof.class);
                if(proof!=null)return false;

                proof = new Proof();
                proof.setId(proofHist.getId());
                proof.setTitle(proofHist.getTitle());
                proof.setContent(proofHist.getContent());
                proof.setActive(true);

                if(proofHist.getCosigners()!=null && proofHist.getCosigners().length>0){
                    String[] cosigners = proofHist.getCosigners();
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

                esClient.index(i->i.index(IndicesNames.PROOF).id(proofHist.getProofId()).document(proof1));
                return true;

            case "sign":

                proof = EsUtils.getById(esClient, IndicesNames.PROOF, proofHist.getProofId(), Proof.class);

                if(proof==null) return false;

                if(Boolean.TRUE.equals(proof.isDestroyed())) return false;

                if(proof.getCosignersInvited()==null)return false;

                for(String signer:proof.getCosignersInvited()) {
                    if(proofHist.getSigner().equals(signer)){
                        if(proof.getCosignersSigned()!=null) {
                            for (String signed : proof.getCosignersSigned()) {
                                if (signer.equals(signed)) return false;
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
                        esClient.index(i->i.index(IndicesNames.PROOF).id(proofHist.getProofId()).document(finalProof));
                        return true;
                    }
                }
                return false;

            case "transfer":

                proof = EsUtils.getById(esClient, IndicesNames.PROOF, proofHist.getProofId(), Proof.class);

                if(proof==null) return false;

                if(Boolean.TRUE.equals(proof.isDestroyed())|| Boolean.FALSE.equals(proof.isActive())) return false;

                if(! proof.getOwner().equals(proofHist.getSigner())) return false;

                if(proofHist.getRecipient()==null) return false;

                proof.setOwner(proofHist.getRecipient());

                Proof finalProof1 = proof;
                esClient.index(i->i.index(IndicesNames.PROOF).id(proofHist.getProofId()).document(finalProof1));
                return  true;

            case "destroy":

                if(proofHist.getProofIds()==null||proofHist.getProofIds().isEmpty())return false;

                EsUtils.MgetResult<Proof> result = EsUtils.getMultiByIdList(esClient, IndicesNames.PROOF, proofHist.getProofIds(), Proof.class);
                if(result==null||result.getResultList()==null||result.getResultList().isEmpty())return false;   
                BulkRequest.Builder br = new BulkRequest.Builder();
                for(Proof proofItem:result.getResultList()){
                    if(Boolean.TRUE.equals(proofItem.isDestroyed())) continue;

                    if(! proofItem.getOwner().equals(proofHist.getSigner())) continue;

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
                esClient.bulk(br.build());
                return  true;
        }
        return false;
    }
}
