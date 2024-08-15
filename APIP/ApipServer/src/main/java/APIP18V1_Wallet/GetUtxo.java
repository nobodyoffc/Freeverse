package APIP18V1_Wallet;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import constants.*;
import fcData.FcReplier;
import fch.CashListReturn;
import fch.Wallet;
import fch.fchData.Cash;
import initial.Initiator;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static fch.Wallet.checkUnconfirmed;


@WebServlet(name = ApiNames.GetUtxo, value = "/"+ApiNames.SN_18+"/"+ApiNames.Version2 +"/"+ApiNames.GetUtxo)
public class GetUtxo extends HttpServlet {


    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }


    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, JedisPool jedisPool) {
        FcReplier replier = new FcReplier(sid,response);
        try(Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis, false);
            if (requestCheckResult==null){
                return;
            }
            String idRequested = request.getParameter("address");
            if (idRequested==null){
                replier.reply(ReplyCodeMessage.Code2003IllegalFid,null,jedis);
                return;
            }

            if(request.getParameter("amount")!=null){
                long amount=(long)(Double.parseDouble(request.getParameter("amount"))* Constants.FchToSatoshi);
                CashListReturn cashListReturn = Wallet.getCashListForPay(amount,idRequested,Initiator.esClient);
                if(cashListReturn.getCode()!=0){
                    replier.replyOtherError(cashListReturn.getMsg(),null,jedis);
                    return;
                }

                List<Cash> cashList = cashListReturn.getCashList();
                List<apip.apipData.Utxo> utxoList = new ArrayList<>();
                for(Cash cash:cashList)
                    utxoList.add(apip.apipData.Utxo.cashToUtxo(cash));

                int size = cashList.size();
                replier.setTotal(cashListReturn.getTotal());
                replier.setGot((long) size);
                replier.setBestHeight(Long.parseLong(jedis.get(Strings.BEST_HEIGHT)));
                replier.reply0Success(utxoList,jedis, null);
                return;
            }

            ElasticsearchClient esClient = Initiator.esClient;

            if(idRequested.charAt(0) == 'F' || idRequested.charAt(0) == '3'){

                ArrayList<apip.apipData.Sort> sortList = apip.apipData.Sort.makeSortList("valid", false, "cd", true, "cashId", true);


                SearchResponse<Cash> cashResult = esClient.search(s -> s.index(IndicesNames.CASH)
                        .query(q ->q.bool(b->b
                                        .must(m->m.term(t -> t.field("owner").value(idRequested)))
                                        .must(m1->m1.term(t1->t1.field("valid").value(true)))
                                )
                        )
                        .trackTotalHits(tr->tr.enabled(true))
                        .aggregations("sum",a->a.sum(s1->s1.field("value")))
                        .sort(apip.apipData.Sort.getSortList(sortList))
                        .size(200), Cash.class);

                List<Hit<Cash>> hitList = cashResult.hits().hits();

                if(hitList==null || hitList.size()==0){
                    replier.reply(ReplyCodeMessage.Code2007CashNoFound,null,jedis);
                    return;
                }

                List<Cash> cashList = new ArrayList<>();
                for(Hit<Cash> hit : hitList){
                    cashList.add(hit.source());
                }

                checkUnconfirmed(idRequested,cashList);

                List<apip.apipData.Utxo> utxoList = new ArrayList<>();
                for(Cash cash:cashList)
                    utxoList.add(apip.apipData.Utxo.cashToUtxo(cash));

                int size = cashList.size();
                replier.setTotal(cashResult.hits().total().value());
                replier.setGot((long) size);
                replier.setBestHeight(Long.parseLong(jedis.get(Strings.BEST_HEIGHT)));
                replier.reply0Success(utxoList,jedis, null);
            }else {
                replier.reply(ReplyCodeMessage.Code2003IllegalFid,null,jedis);
            }
        } catch (IOException e) {
            replier.replyOtherError(e.getMessage(),null,null);
        }
    }
}