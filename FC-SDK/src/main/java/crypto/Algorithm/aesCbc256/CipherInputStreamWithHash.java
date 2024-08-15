package crypto.Algorithm.aesCbc256;

import com.google.common.hash.Hasher;

import javax.crypto.*;
import java.io.IOException;
import java.io.InputStream;

public class CipherInputStreamWithHash extends CipherInputStream {
    private Cipher cipher;
    private InputStream input;
    private byte[] ibuffer = new byte[512];
    private boolean done = false;
    private byte[] obuffer = null;
    private int ostart = 0;
    private int ofinish = 0;
    private boolean closed = false;
    public CipherInputStreamWithHash(InputStream is, Cipher c) {
        super(is);
        this.input = is;
        this.cipher = c;
    }

    protected CipherInputStreamWithHash(InputStream is) {
        super(is);
    }

    public int read(byte[] b, Hasher hasherSrc, Hasher hasherDest) throws IOException {
        return this.read(b, 0, b.length, hasherDest,hasherSrc);
    }

    public int read(byte[] b, int off, int len, Hasher hasher, Hasher hasherSrc) throws IOException {
        int available;
        if (this.ostart >= this.ofinish) {
            for(available = 0; available == 0; available = this.getMoreData(hasherSrc)) {

            }

            if (available == -1) {
                return -1;
            }
        }

        if (len <= 0) {
            return 0;
        } else {
            available = this.ofinish - this.ostart;
            if (len < available) {
                available = len;
            }

            if (b != null) {
                System.arraycopy(this.obuffer, this.ostart, b, off, available);
                hasher.putBytes(b, 0, available);
            }

            this.ostart += available;
            return available;
        }
    }

    private int getMoreData(Hasher hasherSrc) throws IOException {
        if (this.done) {
            return -1;
        } else {
            int readin = this.input.read(this.ibuffer);

            if (readin == -1) {
                this.done = true;
                this.ensureCapacity(0);

                try {
                    this.ofinish = this.cipher.doFinal(this.obuffer, 0);
                } catch (BadPaddingException | ShortBufferException | IllegalBlockSizeException var3) {
                    throw new IOException(var3);
                }

                return this.ofinish == 0 ? -1 : this.ofinish;
            } else {
                hasherSrc.putBytes(this.ibuffer, 0, readin);
                this.ensureCapacity(readin);

                try {
                    this.ofinish = this.cipher.update(this.ibuffer, 0, readin, this.obuffer, this.ostart);
                } catch (IllegalStateException var4) {
                    throw var4;
                } catch (ShortBufferException var5) {
                    throw new IOException(var5);
                }

                return this.ofinish;
            }
        }
    }
    private void ensureCapacity(int inLen) {
        int minLen = this.cipher.getOutputSize(inLen);
        if (this.obuffer == null || this.obuffer.length < minLen) {
            this.obuffer = new byte[minLen];
        }

        this.ostart = 0;
        this.ofinish = 0;
    }
}
