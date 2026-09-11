package startFEIP;

import data.fcData.FcEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Replays surviving histories after a rollback has deleted the entities they built.
 *
 * The rollbackers used to discard what each replay reported, so a rebuild that threw part-way
 * left an entity half-reconstructed while the rollback still reported success. An exception is
 * now an error the caller must surface. A history the parser rejects is only logged: the same
 * history was accepted when first parsed, but it can depend on state owned by another protocol
 * (a master, a membership) that the same rollback has changed, so a rejection is not by itself
 * proof that the rebuild is wrong.
 */
public final class Reparser {

    private static final Logger log = LoggerFactory.getLogger(Reparser.class);

    @FunctionalInterface
    public interface Step<T> {
        boolean apply(T hist) throws Exception;
    }

    private Reparser() {}

    /**
     * @return true if any replay threw
     */
    public static <T extends FcEntity> boolean replay(String what, List<T> hists, Step<T> step) {
        if (hists == null) return false;
        boolean error = false;
        for (T hist : hists) {
            try {
                if (!step.apply(hist)) {
                    log.warn("Rollback reparse: {} history {} was rejected on replay.", what, hist.getId());
                }
            } catch (NumberFormatException e) {
                // Malformed numbers in on-chain data: the live parser treats this as an invalid op.
                log.warn("Rollback reparse: {} history {} has a malformed number: {}", what, hist.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("Rollback reparse: {} history {} threw.", what, hist.getId(), e);
                error = true;
            }
        }
        return error;
    }
}
