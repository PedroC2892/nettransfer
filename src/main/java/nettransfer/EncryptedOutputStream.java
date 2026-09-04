package nettransfer;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;

/**
 * Wraps an OutputStream and encrypts data in chunks using AES-256-GCM.
 * Each flushed chunk is an independent authenticated record:
 *   [4-byte record length][12-byte random IV][ciphertext + 16-byte GCM tag]
 */
public class EncryptedOutputStream extends OutputStream {

    private static final int CHUNK_SIZE = 64 * 1024;
    private static final SecureRandom RNG = new SecureRandom();

    private final DataOutputStream sink;
    private final Handshake crypto;
    private final byte[] buffer = new byte[CHUNK_SIZE];
    private int bufPos = 0;

    public EncryptedOutputStream(OutputStream out, Handshake crypto) {
        this.sink = new DataOutputStream(out);
        this.crypto = crypto;
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        int written = 0;
        while (written < len) {
            int space = CHUNK_SIZE - bufPos;
            int toCopy = Math.min(space, len - written);
            System.arraycopy(b, off + written, buffer, bufPos, toCopy);
            bufPos += toCopy;
            written += toCopy;
            if (bufPos == CHUNK_SIZE) {
                flushBuffer();
            }
        }
    }

    @Override
    public void flush() throws IOException {
        flushBuffer();
        sink.flush();
    }

    private void flushBuffer() throws IOException {
        if (bufPos == 0) return;
        try {
            byte[] iv = new byte[12];
            RNG.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, crypto.aesKey, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(buffer, 0, bufPos);

            // record = [12-byte IV][ciphertext+tag]
            sink.writeInt(12 + ciphertext.length);
            sink.write(iv);
            sink.write(ciphertext);
            bufPos = 0;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("AES-GCM encryption failed", e);
        }
    }
}
