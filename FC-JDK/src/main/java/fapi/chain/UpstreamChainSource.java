package fapi.chain;

import constants.FieldNames;
import constants.IndicesNames;
import constants.Values;
import data.apipData.Fcdsl;
import data.fchData.Block;
import data.fchData.Cash;
import data.fchData.OpReturn;
import data.feipData.Service;
import fapi.FapiCode;
import fapi.client.FapiClient;
import fapi.message.FapiResponse;
import utils.ObjectUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Reads the chain from an upstream FAPI server's BASE, as a paying client. For light servers.
 * <p>
 * BASE answers an empty search with NOT_FOUND, so NOT_FOUND is read as "none" and every other
 * failure, including no response, as {@link ChainUnavailableException}.
 */
public class UpstreamChainSource implements ChainSource {

    static final int CASH_PAGE = 1000;
    static final int IDS_BATCH = 100;

    private final Supplier<FapiClient> client;
    private final String name;

    /**
     * @param client gives the connected upstream client; asked on every call, so a reconnect
     *               behind it is picked up
     * @param name   for logs, e.g. the upstream URL or SID
     */
    public UpstreamChainSource(Supplier<FapiClient> client, String name) {
        this.client = client;
        this.name = name;
    }

    private FapiClient client() {
        FapiClient c = client.get();
        if (c == null) throw new ChainUnavailableException("Upstream " + name + " is not connected");
        return c;
    }

    /** A successful answer: its data and paging cursor. */
    private record Answer(Object data, List<String> last) {}

    /** The answer, or null for NOT_FOUND. */
    private Answer call(String api, Fcdsl fcdsl) {
        FapiResponse r = client().query(api, fcdsl);
        if (r == null) throw new ChainUnavailableException("Upstream " + name + " did not answer " + api);
        Integer code = r.getCode();
        if (code != null && code == FapiCode.NOT_FOUND) return null;
        if (code == null || code != FapiCode.SUCCESS) {
            throw new ChainUnavailableException("Upstream " + name + " failed " + api + ": " + code + " " + r.getMessage());
        }
        return new Answer(r.getData(), r.getLast());
    }

    @Override
    public Block bestBlock() {
        Fcdsl f = new Fcdsl();
        f.setEntity(IndicesNames.BLOCK);
        f.addSort(FieldNames.HEIGHT, Values.DESC);
        f.addSize(1);
        Answer r = call("base.search", f);
        List<Block> blocks = r == null ? null : ObjectUtils.objectToList(r.data(), Block.class);
        if (blocks == null || blocks.isEmpty() || blocks.get(0) == null) {
            throw new ChainUnavailableException("Upstream " + name + " has no block");
        }
        return blocks.get(0);
    }

    @Override
    public List<Cash> cashesOwnedSince(String owner, long fromHeight) {
        List<Cash> out = new ArrayList<>();
        List<String> after = null;
        while (true) {
            Fcdsl f = new Fcdsl();
            f.setEntity(IndicesNames.CASH);
            f.addNewQuery().addNewTerms().addNewFields(FieldNames.OWNER).addNewValues(owner);
            f.getQuery().addNewRange().addNewFields(FieldNames.BIRTH_HEIGHT).addGt(String.valueOf(fromHeight));
            f.addNewFilter().addNewTerms().addNewFields(FieldNames.VALID).addNewValues("true");
            f.addSort(FieldNames.BIRTH_HEIGHT, Values.ASC);
            f.addSort(FieldNames.ID, Values.ASC);
            f.addSize(CASH_PAGE);
            if (after != null) f.setAfter(after);

            Answer r = call("base.search", f);
            List<Cash> page = r == null ? null : ObjectUtils.objectToList(r.data(), Cash.class);
            if (page == null || page.isEmpty()) return out;
            out.addAll(page);
            if (page.size() < CASH_PAGE) return out;
            List<String> last = r.last();
            if (last == null || last.isEmpty() || last.equals(after)) {
                throw new ChainUnavailableException("Upstream " + name + " returned a full cash page without a new cursor");
            }
            after = last;
        }
    }

    @Override
    public Map<String, String> opReturns(Collection<String> txIds) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, OpReturn> e : byIds(IndicesNames.OPRETURN, OpReturn.class, txIds).entrySet()) {
            if (e.getValue() != null && e.getValue().getOpReturn() != null) out.put(e.getKey(), e.getValue().getOpReturn());
        }
        return out;
    }

    @Override
    public List<Cash> validCashes(String fid, int limit) {
        Fcdsl f = new Fcdsl();
        f.setEntity(IndicesNames.CASH);
        f.addNewQuery().addNewTerms().addNewFields(FieldNames.OWNER).addNewValues(fid);
        f.addNewFilter().addNewTerms().addNewFields(FieldNames.VALID).addNewValues("true");
        f.addSort(FieldNames.CD, Values.ASC);
        f.addSort(FieldNames.ID, Values.ASC);
        f.addSize(limit);
        Answer r = call("base.search", f);
        List<Cash> cashes = r == null ? null : ObjectUtils.objectToList(r.data(), Cash.class);
        return cashes == null ? new ArrayList<>() : cashes;
    }

    @Override
    public Map<String, Service> services(Collection<String> sids) {
        return byIds(IndicesNames.SERVICE, Service.class, sids);
    }

    private <T> Map<String, T> byIds(String entity, Class<T> cls, Collection<String> ids) {
        Map<String, T> out = new HashMap<>();
        List<String> all = new ArrayList<>(new LinkedHashSet<>(ids));
        for (int i = 0; i < all.size(); i += IDS_BATCH) {
            List<String> batch = all.subList(i, Math.min(i + IDS_BATCH, all.size()));
            Fcdsl f = new Fcdsl();
            f.setEntity(entity);
            f.addIds(batch);
            Answer r = call("base.getByIds", f);
            if (r == null) continue;
            Map<String, T> got = ObjectUtils.objectToMap(r.data(), String.class, cls);
            if (got != null) got.forEach((k, v) -> {
                if (v != null) out.put(k, v);
            });
        }
        return out;
    }

    @Override
    public String describe() {
        return "upstream " + name;
    }
}
