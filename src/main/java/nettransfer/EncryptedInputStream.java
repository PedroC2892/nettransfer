package nettransfer;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Counterpart to EncryptedOutputStream. Reads length-prefixed encrypted records
 * and decrypts each one with AES-256-GCM using a deterministic sequential nonce,
 * authenticating the tag. A wrong-position record (reordered, replayed, dropped)
 * fails authentication immediately because the expected IV no longer matches.
 */
public class EncryptedInputStream extends InputStream {

    private static final int MAX_RECORD_SIZE = 1024 * 1024; // 1 MB — DoS guard

    private final DataInputStream source;
    private final Handshake crypto;
    private byte[] decrypted = new byte[0];
    private int decryptedPos = 0;
    private long counter = 0;

    public EncryptedInputStream(InputStream in, Handshake crypto) {
        this.source = new DataInputStream(in);
        this.crypto = crypto;
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int n = read(b, 0, 1);
        return n == -1 ? -1 : (b[0] & 0xFF);
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        while (decryptedPos >= decrypted.length) {
            if (!readNextRecord()) return -1;
        }
        int available = decrypted.length - decryptedPos;
        int toCopy = Math.min(available, len);
        System.arraycopy(decrypted, decryptedPos, buf, off, toCopy);
        decryptedPos += toCopy;
        return toCopy;
    }

    private boolean readNextRecord() throws IOException {
        int recordLen;
        try {
            recordLen = source.readInt();
        } catch (EOFException e) {
            return false;
        }
        if (recordLen <= 0 || recordLen > MAX_RECORD_SIZE) {
            throw new IOException("Invalid record length: " + recordLen);
        }
        byte[] record = new byte[recordLen];
        source.readFully(record);

        byte[] iv = nextIv();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, crypto.aesKey, new GCMParameterSpec(128, iv));
            decrypted = cipher.doFinal(record);
            decryptedPos = 0;
            return true;
        } catch (Exception e) {
            throw new IOException("AES-GCM decryption failed (tampered, reordered or replayed data?)", e);
        }
    }

    private byte[] nextIv() {
        byte[] prefix = crypto.getRecvNoncePrefix();
        byte[] iv = new byte[12];
        System.arraycopy(prefix, 0, iv, 0, 4);
        long c = counter++;
        for (int i = 0; i < 8; i++) {
            iv[4 + i] = (byte) (c >>> (8 * (7 - i)));
        }
        return iv;
    }
}
