package startFEIP;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.IndicesNames;
import data.fchData.Freer;
import utils.EsUtils;

/**
 * FEIP6 master rights: an owner's master may act on the owner's items.
 *
 * It is the OWNER's Freer that names the master. Several parsers loaded the signer's own Freer
 * and asked whether the signer was its own master; since the master op does not forbid naming
 * yourself, anyone could set themselves as their own master and then stop, close or transfer
 * items they did not own.
 */
public final class Permission {

    private Permission() {}

    public static boolean isOwnerOrMaster(ElasticsearchClient esClient, String owner, String signer) throws Exception {
        if (owner == null || signer == null) return false;
        if (owner.equals(signer)) return true;
        Freer ownerFreer = EsUtils.getById(esClient, IndicesNames.FREER, owner, Freer.class);
        return ownerFreer != null && signer.equals(ownerFreer.getMaster());
    }
}
