package nettransfer;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Counterpart to EncryptedOutputStream. Reads length-prefixed encrypted records
 * and decrypts each one with AES-256-GCM, authenticating the tag.
 */
public class EncryptedInputStream extends InputStream {

    private final DataInputStream source;
    private final Handshake crypto;
    private byte[] decrypted = new byte[0];
    private int decryptedPos = 0;

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
        byte[] record = new byte[recordLen];
        source.readFully(record);

        byte[] iv = new byte[12];
        System.arraycopy(record, 0, iv, 0, 12);
        int ctLen = recordLen - 12;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, crypto.aesKey, new GCMParameterSpec(128, iv));
            decrypted = cipher.doFinal(record, 12, ctLen);
            decryptedPos = 0;
            return true;
        } catch (Exception e) {
            throw new IOException("AES-GCM decryption failed (tampered data?)", e);
        }
    }
}
