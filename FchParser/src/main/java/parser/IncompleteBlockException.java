package parser;

import java.io.IOException;

/**
 * Thrown when a block read from a blk*.dat file passes the magic/size/length
 * checks but its body is not yet a complete, valid block — typically because
 * the fullnode has committed the block's size header but has not finished
 * flushing the (pre-allocated, zero-padded) body to disk.
 * <p>
 * This is a recoverable condition: the parser should wait for the file to be
 * updated and re-read the same block, rather than aborting. It is distinguished
 * from genuine corruption by the fact that a later re-read succeeds once the
 * fullnode finishes writing.
 */
public class IncompleteBlockException extends IOException {
	public IncompleteBlockException(String message) {
		super(message);
	}
}
