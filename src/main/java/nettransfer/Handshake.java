package nettransfer;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * ECDH ephemeral key exchange (P-256) + AES-256-GCM session key derivation.
 * No passwords. Each connection generates a fresh key pair — forward secrecy.
 *
 * Protocol:
 *   Sender writes  its public key first, then reads receiver's.
 *   Receiver reads sender's public key first, then writes its own.
 *   Both derive the same AES-256 key from the ECDH shared secret via SHA-256.
 */
public class Handshake {

    final SecretKey aesKey;

    private Handshake(SecretKey aesKey) {
        this.aesKey = aesKey;
    }

    /** Called by the sender side: writes first, then reads. */
    public static Handshake forSender(InputStream rawIn, OutputStream rawOut) throws Exception {
        return perform(rawIn, rawOut, true);
    }

    /** Called by the receiver side: reads first, then writes. */
    public static Handshake forReceiver(InputStream rawIn, OutputStream rawOut) throws Exception {
        return perform(rawIn, rawOut, false);
    }

    private static Handshake perform(InputStream rawIn, OutputStream rawOut, boolean senderRole) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair myKeyPair = kpg.generateKeyPair();
        byte[] myPubBytes = myKeyPair.getPublic().getEncoded();

        DataOutputStream dos = new DataOutputStream(rawOut);
        DataInputStream dis = new DataInputStream(rawIn);

        byte[] theirPubBytes;
        if (senderRole) {
            // Sender writes first to avoid deadlock
            dos.writeInt(myPubBytes.length);
            dos.write(myPubBytes);
            dos.flush();
            int len = dis.readInt();
            theirPubBytes = new byte[len];
            dis.readFully(theirPubBytes);
        } else {
            int len = dis.readInt();
            theirPubBytes = new byte[len];
            dis.readFully(theirPubBytes);
            dos.writeInt(myPubBytes.length);
            dos.write(myPubBytes);
            dos.flush();
        }

        PublicKey theirPub = KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(theirPubBytes));

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(myKeyPair.getPrivate());
        ka.doPhase(theirPub, true);
        byte[] sharedSecret = ka.generateSecret();

        byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(sharedSecret);
        return new Handshake(new SecretKeySpec(keyBytes, "AES"));
    }
}
