package fapi.chain;

import data.fchData.Block;
import data.fchData.Cash;
import data.feipData.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * What a FAPI server reads from the chain. A full server reads its own Elasticsearch
 * ({@link EsChainSource}); a light server asks an upstream BASE ({@link UpstreamChainSource}).
 * <p>
 * Every method throws {@link ChainUnavailableException} when the source can't answer. An
 * empty result always means "none", never "failed": callers that advance a scan height on an
 * empty answer rely on this.
 */
public interface ChainSource {

    /** The best block, with height and id. */
    Block bestBlock();

    /**
     * Every valid cash owned by {@code owner} born above {@code fromHeight}, ordered by birth
     * height, then id. All pages are read, so none at the last height is cut off.
     */
    List<Cash> cashesOwnedSince(String owner, long fromHeight);

    /** The OpReturn text of each given transaction that has one: txId → text. */
    Map<String, String> opReturns(Collection<String> txIds);

    /** Up to {@code limit} valid cashes of {@code fid}, lowest CD first. */
    List<Cash> validCashes(String fid, int limit);

    /** The services with these ids: sid → service. Unknown ids are left out. */
    Map<String, Service> services(Collection<String> sids);

    /** For logs: where the chain is read from. */
    String describe();
}
