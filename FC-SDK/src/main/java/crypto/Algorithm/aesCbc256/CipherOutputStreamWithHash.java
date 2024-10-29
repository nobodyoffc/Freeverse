// From javax.crypto;
package crypto.Algorithm.aesCbc256;

import com.google.common.hash.Hasher;

import javax.crypto.*;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CipherOutputStreamWithHash extends FilterOutputStream {
    private Cipher cipher;
    private OutputStream output;
    private byte[] ibuffer = new byte[1];
    private byte[] obuffer = null;
    private boolean closed = false;

    private void ensureCapacity(int inLen) {
        int minLen = this.cipher.getOutputSize(inLen);
        if (this.obuffer == null || this.obuffer.length < minLen) {
            this.obuffer = new byte[minLen];
        }

    }
    public void write(byte[] b, int off, int len, Hasher hasherOut) throws IOException {
        this.ensureCapacity(len);

        try {
            int ostored = this.cipher.update(b, off, len, this.obuffer);
            if (ostored > 0) {
                this.output.write(this.obuffer, 0, ostored);
            }
            hasherOut.putBytes(this.obuffer, 0, ostored);
        } catch (ShortBufferException var5) {
            throw new IOException(var5);
        }
    }

    public CipherOutputStreamWithHash(OutputStream os, Cipher c) {
        super(os);
        this.output = os;
        this.cipher = c;
    }

    protected CipherOutputStreamWithHash(OutputStream os) {
        super(os);
        this.output = os;
        this.cipher = new NullCipher();
    }

    public void write(int b) throws IOException {
        this.ibuffer[0] = (byte)b;
        this.ensureCapacity(1);

        try {
            int ostored = this.cipher.update(this.ibuffer, 0, 1, this.obuffer);
            if (ostored > 0) {
                this.output.write(this.obuffer, 0, ostored);
            }

        } catch (ShortBufferException var3) {
            throw new IOException(var3);
        }
    }

    public void write(byte[] b) throws IOException {
        this.write(b, 0, b.length);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        this.ensureCapacity(len);

        try {
            int ostored = this.cipher.update(b, off, len, this.obuffer);
            if (ostored > 0) {
                this.output.write(this.obuffer, 0, ostored);
            }

        } catch (ShortBufferException var5) {
            throw new IOException(var5);
        }
    }

    public void flush() throws IOException {
        this.output.flush();
    }

    public void close() throws IOException {
        if (!this.closed) {
            this.closed = true;
            this.ensureCapacity(0);

            try {
                int ostored = this.cipher.doFinal(this.obuffer, 0);
                if (ostored > 0) {
                    this.output.write(this.obuffer, 0, ostored);
                }
            } catch (BadPaddingException | ShortBufferException | IllegalBlockSizeException var3) {
            }

            this.obuffer = null;

            try {
                this.flush();
            } catch (IOException var2) {
            }

            this.output.close();
        }
    }
}

