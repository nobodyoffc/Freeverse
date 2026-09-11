package finance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import startFEIP.FeipConstants;
import startFEIP.StartFEIP;
import utils.StringUtils;

import java.math.RoundingMode;
import java.util.*;

import static constants.OpNames.*;
import static constants.Values.*;

public class FinanceParser {

    private static final Logger log = LoggerFactory.getLogger(FinanceParser.class);

    public TokenHistory makeToken(OpReturn opre, Feip feip) {

        Gson gson = new Gson();
        TokenOpData tokenRaw = new TokenOpData();

        try {
            tokenRaw = gson.fromJson(gson.toJsonTree(feip.getData()), TokenOpData.class);
            if(tokenRaw==null)
            {
                log.info("Token raw is null");
                return null;
            }
        }catch(com.google.gson.JsonSyntaxException e) {
            log.info("Json syntax exception");
            return null;
        }

        TokenHistory tokenHist = new TokenHistory();

        if(tokenRaw.getOp()==null){
            log.info("Op is null");
            return null;
        }

        tokenHist.setOp(tokenRaw.getOp());
        tokenHist.setCdd(opre.getCdd());

        switch(tokenRaw.getOp()) {
            case OpNames.DEPLOY:
                if (opre.getHeight() > StartFEIP.CddCheckHeight) {
                    Long cdd = opre.getCdd();
                    if (cdd == null || cdd < StartFEIP.CddRequired * FeipConstants.CDD_MULTIPLIER) {
                        log.info("Height is greater than CddCheckHeight and Cdd is null or less than CddRequired * FeipConstants.CDD_MULTIPLIER");
                        return null;
                    }
                }
                if(tokenRaw.getName()==null){
                    log.info("Name is null");
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
                        log.info("Decimal is not an integer");
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
                    log.info("Token id is null");
                    return null;
                }
                tokenHist.setTokenId(tokenRaw.getTokenId());
                setTxInfo(opre,tokenHist);
                if(tokenRaw.getIssueTo()==null){
                    log.info("Issue to is null");
                    return null;
                }
                tokenHist.setIssueTo(tokenRaw.getIssueTo());
                break;

            case OpNames.TRANSFER:
                if(tokenRaw.getTokenId()==null){
                    log.info("Token id is null");
                    return null;
                }
                tokenHist.setTokenId(tokenRaw.getTokenId());
                setTxInfo(opre, tokenHist);
                if(tokenRaw.getTransferTo()==null){
                    log.info("Transfer to is null");
                    return null;
                }
                tokenHist.setTransferTo(tokenRaw.getTransferTo());
                break;

            case OpNames.DESTROY:
                if(tokenRaw.getTokenIds()==null||tokenRaw.getTokenIds().isEmpty()){
                    log.info("Token ids is null or empty");
                    return null;
                }
                if(tokenRaw.getTokenIds().size()!=1){
                    log.info("Destroy requires exactly one tokenId in tokenIds");
                    return null;
                }
                tokenHist.setTokenIds(tokenRaw.getTokenIds());
                tokenHist.setTokenId(tokenRaw.getTokenIds().get(0));
                setTxInfo(opre, tokenHist);
                break;
            case OpNames.CLOSE:
                if(tokenRaw.getTokenIds()==null||tokenRaw.getTokenIds().isEmpty()){
                    log.info("Token ids is null or empty");
                    return null;
                }
                tokenHist.setTokenIds(tokenRaw.getTokenIds());
                setTxInfo(opre, tokenHist);
                break;

            default:
                log.info("Invalid operation");
                return null;
        }

        return tokenHist;
    }

    public static void setTxInfo(OpReturn opre, TokenHistory tokenHist) {
        if(tokenHist==null){
            log.info("Token history is null");
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
            proofRaw = gson.fromJson(gson.toJsonTree(feip.getData()), ProofOpData.class);
            if(proofRaw==null){
                log.info("Proof raw is null");
                return null;
            }
        }catch(com.google.gson.JsonSyntaxException e) {
            log.info("Json syntax exception");
            return null;
        }

        ProofHistory proofHist = new ProofHistory();

        if(proofRaw.getOp()==null){
            log.info("Op is null");
            return null;
        }

        proofHist.setOp(proofRaw.getOp());

        switch(proofRaw.getOp()) {
            case OpNames.ISSUE:
                if(proofRaw.getTitle()==null){
                    log.info("Title is null");
                    return null;
                }
                if(proofRaw.getContent()==null){
                    log.info("Content is null");
                    return null;
                }
                if (opre.getHeight() > StartFEIP.CddCheckHeight) {
                    Long cdd = opre.getCdd();
                    if (cdd == null || cdd < StartFEIP.CddRequired * FeipConstants.CDD_MULTIPLIER) {
                        log.info("Height is greater than CddCheckHeight and Cdd is null or less than CddRequired");
                        return null;
                    }
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
                if(proofRaw.getProofId()==null){
                    log.info("Proof id is null");
                    return null;
                }
                proofHist.setProofId(proofRaw.getProofId());
                proofHist.setId(opre.getId());
                proofHist.setHeight(opre.getHeight());
                proofHist.setIndex(opre.getTxIndex());
                proofHist.setTime(opre.getTime());
                proofHist.setSigner(opre.getSigner());
                break;
            case OpNames.DESTROY:
                if(proofRaw.getProofIds()==null||proofRaw.getProofIds().isEmpty()){
                    log.info("Proof ids is null or empty");
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
                    log.info("Proof id is null");
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
                log.info("Invalid operation");
                return null;
        }

        return proofHist;
    }


    public boolean parseToken(ElasticsearchClient esClient, TokenHistory tokenHist) throws Exception {
        if(tokenHist==null || tokenHist.getOp()==null){
            log.info("Token history is null or op is null");
            return false;
        }
        Token token;
        switch(tokenHist.getOp()) {
            case OpNames.DEPLOY:
                token = EsUtils.getById(esClient, IndicesNames.TOKEN, tokenHist.getTokenId(), Token.class);
                if(token!=null){
                    log.info("Token already exists");
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
                log.info("{}", result.result());
                // Create News
                News.createNews(esClient, tokenHist.getId(), tokenHist.getSigner(), CREATE, Feip.FeipProtocol.TOKEN.getName(),
                        tokenHist.getId(), tokenHist.getName(), tokenHist.getDesc(), tokenHist.getHeight(), tokenHist.getTime());

                return true;

            case OpNames.ISSUE:
                token = EsUtils.getById(esClient, IndicesNames.TOKEN, tokenHist.getTokenId(), Token.class);
                if(token==null || Boolean.TRUE.equals(token.getClosed())){
                    log.info("Token is null or closed");
                    return false;
                }
                if(Boolean.FALSE.equals(token.getOpenIssue()) && !tokenHist.getSigner().equals(token.getDeployer())){
                    log.info("Token open issue is false and signer is not deployer");
                    return false;
                }

                if(tokenHist.getIssueTo()==null){
                    log.info("Issue to is null");
                    return false;
                }

                RecipientTotals issueTotals = aggregateRecipients(tokenHist.getIssueTo(), token, tokenHist.getTokenId());
                if(issueTotals==null) return false;

                ArrayList<String> tokenRecipientIdListIssue = issueTotals.holderIds;
                Map<String,Double> receiverAmountMapIssue = issueTotals.amountByFid;
                Map<String,String> idReceiverMapIssue = issueTotals.fidByHolderId;
                Double amount = issueTotals.total;

                if(Boolean.TRUE.equals(token.getOpenIssue())){
                    if(token.getMaxAmtPerIssue()!=null){
                        if(amount>Double.parseDouble(token.getMaxAmtPerIssue())){
                            log.info("Amount is greater than max amount per issue");
                            return false;
                        }
                    }
                    if(token.getMinCddPerIssue()!=null){
                        Long histCdd = tokenHist.getCdd();
                        if(histCdd==null || histCdd<Long.parseLong(token.getMinCddPerIssue())){
                            log.info("Cdd is null or less than min cdd per issue");
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
                                log.info("Times is greater than max issues per addr");
                                return false;
                            }
                        }
                    }
                }

                double circulating;
                if(token.getCirculating()==null)circulating = amount;
                else circulating = token.getCirculating() + amount;

                if(token.getCapacity()!=null && circulating > Double.parseDouble(token.getCapacity())){
                    log.info("Circulating is greater than capacity");
                    return false;
                }
                token.setCirculating(circulating);
                updateTokenLastInfo(tokenHist, token);

                //Set balances of the holders

                EsUtils.MgetResult<TokenHolder> resultMultiGet = EsUtils.getMultiByIdList(esClient, IndicesNames.TOKEN_HOLDER, tokenRecipientIdListIssue, TokenHolder.class);
                if (resultMultiGet == null) {
                    log.info("Token holder mget result is null");
                    return false;
                }

                // Keyed by each holder's own document id; see the transfer branch.
                Map<String, TokenHolder> issuedHolders = new LinkedHashMap<>();

                for( TokenHolder tokenHolder: resultMultiGet.getResultList()) {
                    String fid = tokenHolder.getFid();
                    Double credit = receiverAmountMapIssue.get(fid);
                    if(credit==null){
                        log.info("Stored token holder fid {} is not among the recipients", fid);
                        return false;
                    }
                    double oldBalance = tokenHolder.getBalance() != null ? tokenHolder.getBalance() : 0d;
                    tokenHolder.setBalance(oldBalance + credit);
                    tokenHolder.setLastHeight(tokenHist.getHeight());
                    issuedHolders.put(TokenHolder.getTokenHolderId(fid, tokenHist.getTokenId()), tokenHolder);
                }

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
                    issuedHolders.put(tokenHolderId, tokenHolder);
                }

                writeHolders(esClient, issuedHolders);
                Token finalToken3 = token;
                IndexResponse result1 =  esClient.index(i->i.index(IndicesNames.TOKEN).id(tokenHist.getTokenId()).document(finalToken3));
                log.info("{}", result1.result());
                requireWritten(result1, "token " + tokenHist.getTokenId() + " after crediting its holders");
                return true;

            case OpNames.TRANSFER:
                token = EsUtils.getById(esClient, IndicesNames.TOKEN, tokenHist.getTokenId(), Token.class);
                if(token==null || Boolean.TRUE.equals(token.getClosed())){
                    log.info("Token is null or closed");
                    return false;
                }
                if(!Boolean.TRUE.equals(token.getTransferable())){
                    log.info("Token is not transferable");
                    return false;
                }
                if(tokenHist.getTransferTo()==null || tokenHist.getTransferTo().isEmpty()){
                    log.info("Transfer to is null or empty");
                    return false;
                }
                int decimal = Integer.parseInt(token.getDecimal());
                String fromFid = tokenHist.getSigner();
                String tokenHolderId = TokenHolder.getTokenHolderId(fromFid, tokenHist.getTokenId());
                TokenHolder tokenHolder = EsUtils.getById(esClient, IndicesNames.TOKEN_HOLDER, tokenHolderId, TokenHolder.class);
                if(tokenHolder==null){
                    log.info("Token holder is null");
                    return false;
                }
                double senderOldBalance = tokenHolder.getBalance() != null ? tokenHolder.getBalance() : 0d;

                RecipientTotals transferTotals = aggregateRecipients(tokenHist.getTransferTo(), token, tokenHist.getTokenId());
                if(transferTotals==null) return false;

                double sum = transferTotals.total;

                if(sum>senderOldBalance){
                    log.info("Sum is greater than sender old balance");
                    return false;
                }

                List<String> otherRecipientIds = new ArrayList<>(transferTotals.holderIds);
                otherRecipientIds.remove(tokenHolderId);

                List<TokenHolder> existingRecipients = new ArrayList<>();
                List<String> newRecipientIds = new ArrayList<>();
                if(!otherRecipientIds.isEmpty()) {
                    EsUtils.MgetResult<TokenHolder> resultTransfer = EsUtils.getMultiByIdList(esClient, IndicesNames.TOKEN_HOLDER, otherRecipientIds, TokenHolder.class);
                    if (resultTransfer == null) {
                        log.info("Token holder mget result is null");
                        return false;
                    }
                    existingRecipients.addAll(resultTransfer.getResultList());
                    newRecipientIds.addAll(resultTransfer.getMissList());
                }

                Map<String, TokenHolder> changedHolders = planTransfer(tokenHolder, fromFid, transferTotals,
                        existingRecipients, newRecipientIds, tokenHist.getTokenId(), tokenHist.getHeight(), decimal);
                if(changedHolders==null) return false;

                writeHolders(esClient, changedHolders);
                log.info("Done");
                return true;

            case OpNames.DESTROY:

                token = EsUtils.getById(esClient, IndicesNames.TOKEN, tokenHist.getTokenId(), Token.class);
                if(token==null || Boolean.TRUE.equals(token.getClosed())){
                    log.info("Token is null or closed");
                    return false;
                }
                decimal = Integer.parseInt(token.getDecimal());
                String tokenReceiverId= TokenHolder.getTokenHolderId(tokenHist.getSigner(), tokenHist.getTokenId());
                TokenHolder tokenHolderDestroy = EsUtils.getById(esClient, IndicesNames.TOKEN_HOLDER, tokenReceiverId, TokenHolder.class);

                if(tokenHolderDestroy==null){
                    log.info("Token holder destroy is null");
                    return false;
                }
                if(!tokenHolderDestroy.getFid().equals(tokenHist.getSigner())){
                    log.info("Token holder destroy fid is not the same as the signer");
                    return false;
                }

                double balance = tokenHolderDestroy.getBalance() != null ? tokenHolderDestroy.getBalance() : 0d;
                if(balance <=0){
                    log.info("Balance is zero or negative");
                    return false;
                }

                tokenHolderDestroy.setBalance(0D);
                tokenHolderDestroy.setLastHeight(tokenHist.getHeight());

                token.setCirculating(NumberUtils.roundDouble(token.getCirculating()-balance,decimal,RoundingMode.FLOOR));
                updateTokenLastInfo(tokenHist, token);

                IndexResponse result3 = esClient.index(i->i.index(IndicesNames.TOKEN_HOLDER).id(tokenReceiverId).document(tokenHolderDestroy));
                if(result3==null || result3.result()==null){
                    log.info("Failed to index token holder");
                    return false;
                }
                log.info(IndicesNames.TOKEN_HOLDER+":"+result3.result());

                Token finalToken4 = token;
                IndexResponse result4 = esClient.index(i->i.index(IndicesNames.TOKEN).id(tokenHist.getTokenId()).document(finalToken4));
                requireWritten(result4, "token " + tokenHist.getTokenId() + " after zeroing a holder");
                log.info(IndicesNames.TOKEN+":"+result4.result());
                return true;
            case OpNames.CLOSE:
                if (tokenHist.getTokenIds() == null || tokenHist.getTokenIds().isEmpty()) {
                    log.info("Token ids is null or empty");
                    return false;
                }
                // Validate every target before writing any: a later bad id used to reject the op
                // after the earlier tokens had already been written as closed.
                Map<String, Token> tokensToClose = new LinkedHashMap<>();
                for (String tid : tokenHist.getTokenIds()) {
                    token = EsUtils.getById(esClient, IndicesNames.TOKEN, tid, Token.class);
                    if(token==null || Boolean.TRUE.equals(token.getClosed())){
                        log.info("Token is null or closed: " + tid);
                        return false;
                    }

                    if(!tokenHist.getSigner().equals(token.getDeployer())){
                        log.info("Token signer is not the same as the deployer");
                        return false;
                    }
                    tokensToClose.put(tid, token);
                }

                for (Map.Entry<String, Token> entry : tokensToClose.entrySet()) {
                    Token closing = entry.getValue();
                    closing.setClosed(Boolean.TRUE);
                    updateTokenLastInfo(tokenHist, closing);

                    IndexResponse result5 = esClient.index(i->i.index(IndicesNames.TOKEN).id(entry.getKey()).document(closing));
                    log.info("{}", result5.result());
                    requireWritten(result5, "closed token " + entry.getKey());
                }

                // Create News
                News.createNews(esClient, tokenHist.getId(), tokenHist.getSigner(), CLOSE, Feip.FeipProtocol.TOKEN.getName(),
                        null, null, StringUtils.listToString(tokenHist.getTokenIds()), tokenHist.getHeight(), tokenHist.getTime());
                return true;
        }
        return false;
    }

    /**
     * The holder documents a transfer changes, keyed by each one's own document id, or null if a
     * stored recipient does not match the transfer. The caller has already checked that the
     * sender can cover `totals.total`.
     *
     * Keyed, not paired: the ids used to travel as a separate list in recipient order while the
     * documents were built new-holders-first, so whenever new and existing recipients interleaved,
     * balances were written under each other's ids.
     *
     * Sending to oneself moves nothing. The sender's own record used to be credited through the
     * recipient path and debited separately -- two writes to one document -- and the debit,
     * written last, won: sending your whole balance to yourself zeroed it.
     *
     * Pure, so it can be tested without Elasticsearch.
     */
    static Map<String, TokenHolder> planTransfer(TokenHolder sender, String senderFid, RecipientTotals totals,
                                                 List<TokenHolder> existingRecipients, List<String> newRecipientIds,
                                                 String tokenId, Long height, int decimal) {
        Map<String, TokenHolder> changed = new LinkedHashMap<>();

        double senderOldBalance = sender.getBalance() != null ? sender.getBalance() : 0d;
        Double selfCredit = totals.amountByFid.get(senderFid);
        double netDebit = totals.total - (selfCredit != null ? selfCredit : 0d);
        sender.setBalance(NumberUtils.roundDouble(senderOldBalance - netDebit, decimal, RoundingMode.FLOOR));
        sender.setLastHeight(height);
        changed.put(TokenHolder.getTokenHolderId(senderFid, tokenId), sender);

        for (TokenHolder receiver : existingRecipients) {
            String toFid = receiver.getFid();
            Double credit = totals.amountByFid.get(toFid);
            if (credit == null || toFid.equals(senderFid)) {
                log.info("Stored token holder {} does not match the transfer", toFid);
                return null;
            }
            double oldBalance = receiver.getBalance() != null ? receiver.getBalance() : 0d;
            receiver.setBalance(NumberUtils.roundDouble(credit + oldBalance, decimal, RoundingMode.FLOOR));
            receiver.setLastHeight(height);
            changed.put(TokenHolder.getTokenHolderId(toFid, tokenId), receiver);
        }

        for (String id : newRecipientIds) {
            String toFid = totals.fidByHolderId.get(id);
            Double credit = toFid == null ? null : totals.amountByFid.get(toFid);
            if (credit == null) {
                log.info("No transfer amount for new holder {}", id);
                return null;
            }
            TokenHolder receiver = new TokenHolder();
            receiver.setId(id);
            receiver.setFid(toFid);
            receiver.setTokenId(tokenId);
            receiver.setFirstHeight(height);
            receiver.setLastHeight(height);
            receiver.setBalance(credit);
            changed.put(id, receiver);
        }
        return changed;
    }

    private static void writeHolders(ElasticsearchClient esClient, Map<String, TokenHolder> holdersById) throws Exception {
        BulkResponse response = EsUtils.bulkWriteList(esClient, IndicesNames.TOKEN_HOLDER,
                new ArrayList<>(holdersById.values()), new ArrayList<>(holdersById.keySet()), TokenHolder.class);
        if (response == null || response.errors()) {
            // Some holders may already be written, so this is not a rejected op but a partial one.
            // Throwing hands it to FileParser, which rolls the token back and replays.
            throw new java.io.IOException("Failed to write token holders");
        }
    }

    /** A write that follows other writes of the same op must not fail quietly; see writeHolders. */
    private static void requireWritten(IndexResponse response, String what) throws java.io.IOException {
        if (response == null || response.result() == null
                || !(CREATED.equals(response.result().jsonValue()) || UPDATED.equals(response.result().jsonValue()))) {
            throw new java.io.IOException("Failed to write " + what);
        }
    }

    private static void updateTokenLastInfo(TokenHistory tokenHist, Token token) {
        if(tokenHist==null)return;
        token.setLastHeight(tokenHist.getHeight());
        token.setLastTime(tokenHist.getTime());
        token.setLastTxId(tokenHist.getId());
    }

    /**
     * Validate a list of recipients and aggregate their amounts.
     *
     * Returns null if any entry is unusable. Duplicate fids are summed, never overwritten: the
     * running total counts every entry, so an overwritten duplicate silently destroys the
     * difference between what the sender is debited and what the recipients are credited.
     *
     * Extracted from the issue and transfer branches so this arithmetic can be tested without
     * an Elasticsearch client -- the parser writes to the live `token` / `token_holder` indices.
     */
    static RecipientTotals aggregateRecipients(List<TokenHistory.FidAmount> recipients, Token token, String tokenId) {
        if (recipients == null) {
            log.info("Recipient list is null");
            return null;
        }
        RecipientTotals totals = new RecipientTotals();
        for (TokenHistory.FidAmount to : recipients) {
            if (!KeyTools.isGoodFid(to.getFid())) {
                log.info("Recipient fid is not good");
                return null;
            }
            if (isBadAmount(to.getAmount())) {
                log.info("Recipient amount is null, not finite, or not positive");
                return null;
            }
            if (isBadDecimal(token, to)) {
                log.info("Recipient amount has too many decimal places");
                return null;
            }
            totals.total += to.getAmount();
            totals.amountByFid.merge(to.getFid(), to.getAmount(), Double::sum);
            String holderId = TokenHolder.getTokenHolderId(to.getFid(), tokenId);
            totals.fidByHolderId.put(holderId, to.getFid());
            if (!totals.holderIds.contains(holderId)) totals.holderIds.add(holderId);
        }
        return totals;
    }

    /** Aggregated recipients: one entry per distinct fid, with the total across all entries. */
    static class RecipientTotals {
        final Map<String, Double> amountByFid = new HashMap<>();
        final Map<String, String> fidByHolderId = new HashMap<>();
        final ArrayList<String> holderIds = new ArrayList<>();
        double total = 0d;
    }

    /**
     * An amount must be a finite, strictly positive number. Without this a negative amount flows
     * straight through the balance arithmetic: the sender's `oldBalance - sum` grows instead of
     * shrinking, and the recipient is credited a negative balance.
     */
    private static boolean isBadAmount(Double amount) {
        return amount == null || amount.isNaN() || amount.isInfinite() || amount <= 0d;
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
            log.info("Proof hist is null or op is null");
            return false;
        }
        Proof proof;
        switch(proofHist.getOp()) {
            case OpNames.ISSUE:
                proof = EsUtils.getById(esClient, IndicesNames.PROOF, proofHist.getProofId(), Proof.class);
                if(proof!=null){
                    log.info("Proof already exists");
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
                    log.info("Failed to index proof");
                    return false;
                }
                log.info("{}", result1.result());
                return CREATED.equals(result1.result().jsonValue()) || UPDATED.equals(result1.result().jsonValue());

            case OpNames.SIGN:

                proof = EsUtils.getById(esClient, IndicesNames.PROOF, proofHist.getProofId(), Proof.class);

                if(proof==null){
                    log.info("Proof is null");
                    return false;
                }

                if(Boolean.TRUE.equals(proof.isDestroyed())){
                    log.info("Proof is destroyed");
                    return false;
                }

                if(proof.getCosignersInvited()==null){
                    log.info("Cosigners invited is null");
                    return false;
                }

                for(String signer:proof.getCosignersInvited()) {
                    if(proofHist.getSigner().equals(signer)){
                        if(proof.getCosignersSigned()!=null) {
                            for (String signed : proof.getCosignersSigned()) {
                                if (signer.equals(signed)){
                                    log.info("Signer is already signed");
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
                            log.info("Failed to index proof");
                            return false;
                        }
                        log.info("{}", result2.result());
                        return CREATED.equals(result2.result().jsonValue()) || UPDATED.equals(result2.result().jsonValue());
                    }
                }
                return false;

            case OpNames.TRANSFER:

                proof = EsUtils.getById(esClient, IndicesNames.PROOF, proofHist.getProofId(), Proof.class);

                if(proof==null){
                    log.info("Proof is null");
                    return false;
                }

                if(Boolean.TRUE.equals(proof.isDestroyed())|| Boolean.FALSE.equals(proof.isActive())){
                    log.info("Proof is destroyed or not active");
                    return false;
                }

                if(!Boolean.TRUE.equals(proof.isTransferable())){
                    log.info("Proof is not transferable");
                    return false;
                }

                if(! proof.getOwner().equals(proofHist.getSigner())){
                    log.info("Proof owner is not the same as the signer");
                    return false;
                }

                if(proofHist.getRecipient()==null){
                    log.info("Recipient is null");
                    return false;
                }

                proof.setOwner(proofHist.getRecipient());
                proof.setLastTxId(proofHist.getId());
                proof.setLastTime(proofHist.getTime());
                proof.setLastHeight(proofHist.getHeight());

                Proof finalProof1 = proof;
                IndexResponse result3 = esClient.index(i->i.index(IndicesNames.PROOF).id(proofHist.getProofId()).document(finalProof1));
                if(result3==null || result3.result()==null){
                    log.info("Failed to index proof");
                    return false;
                }
                log.info("{}", result3.result());
                return CREATED.equals(result3.result().jsonValue()) || UPDATED.equals(result3.result().jsonValue());

            case DESTROY:

                if(proofHist.getProofIds()==null||proofHist.getProofIds().isEmpty()){
                    log.info("Proof ids is null or empty");
                    return false;
                }

                EsUtils.MgetResult<Proof> result = EsUtils.getMultiByIdList(esClient, IndicesNames.PROOF, proofHist.getProofIds(), Proof.class);
                if(result.getResultList() == null || result.getResultList().isEmpty()){
                    log.info("Result is null or result list is null or empty");
                    return false;
                }
                BulkRequest.Builder br = new BulkRequest.Builder();
                int destroyedCount = 0;
                for(Proof proofItem:result.getResultList()){
                    if(Boolean.TRUE.equals(proofItem.isDestroyed())){
                        log.info("Proof item is destroyed");
                        continue;
                    }

                    if(! proofItem.getOwner().equals(proofHist.getSigner())){
                        log.info("Proof item owner is not the same as the signer");
                        continue;
                    }

                    proofItem.setDestroyed(true);
                    proofItem.setActive(false);
                    proofItem.setLastTxId(proofHist.getId());
                    proofItem.setLastTime(proofHist.getTime());
                    proofItem.setLastHeight(proofHist.getHeight());

                    br.operations(op -> op
                        .index(idx -> idx
                            .index(IndicesNames.PROOF)
                            .id(proofItem.getId())
                            .document(proofItem)
                        )
                    );
                    destroyedCount++;
                }

                if(destroyedCount==0){
                    log.info("No proof to destroy");
                    return false;
                }

                BulkResponse result4 = esClient.bulk(br.build());
                if(result4==null || result4.errors()){
                    log.info("Failed to bulk index proof");
                    return false;
                } else log.info("Done");
                return  true;
        }
        return false;
    }
}
