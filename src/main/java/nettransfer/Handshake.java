package nettransfer;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * ECDH ephemeral key exchange (P-256) + HKDF-SHA256 (RFC 5869) key derivation.
 * No passwords. Each connection generates a fresh key pair — forward secrecy.
 *
 * Protocol:
 *   Sender writes  its public key first, then reads receiver's.
 *   Receiver reads sender's public key first, then writes its own.
 *   Both derive the same AES-256 key, verification code, and per-direction
 *   nonce prefixes from the ECDH shared secret via HKDF.
 */
public class Handshake {

    private static final String KEY_INFO = "NetTransfer v1 file transfer";
    private static final String VERIFY_INFO = "NetTransfer v1 verification";
    private static final String NONCE_INFO = "NetTransfer v1 nonce prefix";

    final SecretKey aesKey;
    private final String verificationCode;
    private final byte[] sendNoncePrefix;
    private final byte[] recvNoncePrefix;

    private Handshake(SecretKey aesKey, String verificationCode, byte[] sendNoncePrefix, byte[] recvNoncePrefix) {
        this.aesKey = aesKey;
        this.verificationCode = verificationCode;
        this.sendNoncePrefix = sendNoncePrefix;
        this.recvNoncePrefix = recvNoncePrefix;
    }

    /** 6-digit code derived from both ephemeral public keys — must match on both devices (MITM defence). */
    public String getVerificationCode() {
        return verificationCode;
    }

    /** 4-byte prefix used for records this side writes. */
    public byte[] getSendNoncePrefix() {
        return sendNoncePrefix;
    }

    /** 4-byte prefix expected on records this side reads. */
    public byte[] getRecvNoncePrefix() {
        return recvNoncePrefix;
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
        validateSecp256r1PublicKey(theirPub);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(myKeyPair.getPrivate());
        ka.doPhase(theirPub, true);
        byte[] sharedSecret = ka.generateSecret();

        byte[] prk = hkdfExtract(new byte[32], sharedSecret);
        byte[] keyBytes = hkdfExpand(prk, KEY_INFO.getBytes(StandardCharsets.UTF_8), 32);

        byte[] sortedConcat = sortedConcat(myPubBytes, theirPubBytes);
        byte[] verifyInfo = concat(VERIFY_INFO.getBytes(StandardCharsets.UTF_8), sortedConcat);
        byte[] verifyBytes = hkdfExpand(prk, verifyInfo, 3);
        String code = renderVerificationCode(verifyBytes);

        byte[] noncePrefixSenderRole = hkdfExpand(prk, concat(NONCE_INFO.getBytes(StandardCharsets.UTF_8), "|S2R".getBytes(StandardCharsets.UTF_8)), 4);
        byte[] noncePrefixReceiverRole = hkdfExpand(prk, concat(NONCE_INFO.getBytes(StandardCharsets.UTF_8), "|R2S".getBytes(StandardCharsets.UTF_8)), 4);
        byte[] sendPrefix = senderRole ? noncePrefixSenderRole : noncePrefixReceiverRole;
        byte[] recvPrefix = senderRole ? noncePrefixReceiverRole : noncePrefixSenderRole;

        return new Handshake(new SecretKeySpec(keyBytes, "AES"), code, sendPrefix, recvPrefix);
    }

    private static byte[] sortedConcat(byte[] a, byte[] b) {
        byte[] first = compareBytes(a, b) <= 0 ? a : b;
        byte[] second = first == a ? b : a;
        return concat(first, second);
    }

    private static int compareBytes(byte[] a, byte[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int cmp = Integer.compare(a[i] & 0xFF, b[i] & 0xFF);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.length, b.length);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static String renderVerificationCode(byte[] threeBytes) {
        int value = ((threeBytes[0] & 0xFF) << 16) | ((threeBytes[1] & 0xFF) << 8) | (threeBytes[2] & 0xFF);
        int code = value % 1_000_000;
        return String.format("%06d", code);
    }

    // ── HKDF (RFC 5869), implemented manually with HmacSHA256 — no external deps ──

    private static byte[] hkdfExtract(byte[] salt, byte[] ikm) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        return mac.doFinal(ikm);
    }

    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        int hashLen = 32;
        int n = (int) Math.ceil((double) length / hashLen);
        byte[] okm = new byte[n * hashLen];
        byte[] previous = new byte[0];
        for (int i = 1; i <= n; i++) {
            mac.reset();
            mac.update(previous);
            mac.update(info);
            mac.update((byte) i);
            previous = mac.doFinal();
            System.arraycopy(previous, 0, okm, (i - 1) * hashLen, hashLen);
        }
        return Arrays.copyOf(okm, length);
    }

    // ── Public key validation (invalid curve attack defence) ──

    private static void validateSecp256r1PublicKey(PublicKey key) throws Exception {
        if (!(key instanceof ECPublicKey ecKey)) {
            TransferLogger.logSecurityEvent("Received public key is not an EC key", null);
            throw new SecurityException("Invalid public key: not an EC key");
        }

        ECParameterSpec expected = secp256r1Params();
        ECParameterSpec actual = ecKey.getParams();
        EllipticCurve curve = actual.getCurve();
        if (!(curve.getField() instanceof ECFieldFp)) {
            TransferLogger.logSecurityEvent("Received public key uses non-Fp field", null);
            throw new SecurityException("Invalid public key: unexpected field type");
        }
        if (!curveMatches(actual, expected)) {
            TransferLogger.logSecurityEvent("Received public key curve parameters do not match secp256r1", null);
            throw new SecurityException("Invalid public key: curve parameters mismatch");
        }

        ECPoint w = ecKey.getW();
        if (ECPoint.POINT_INFINITY.equals(w)) {
            TransferLogger.logSecurityEvent("Received public key is the point at infinity", null);
            throw new SecurityException("Invalid public key: point at infinity");
        }
        if (!isOnCurve(w, curve)) {
            TransferLogger.logSecurityEvent("Received public key point is not on secp256r1 curve", null);
            throw new SecurityException("Invalid public key: point not on curve");
        }
    }

    private static boolean isOnCurve(ECPoint w, EllipticCurve curve) {
        BigInteger p = ((ECFieldFp) curve.getField()).getP();
        BigInteger a = curve.getA();
        BigInteger b = curve.getB();
        BigInteger x = w.getAffineX();
        BigInteger y = w.getAffineY();
        BigInteger lhs = y.multiply(y).mod(p);
        BigInteger rhs = x.multiply(x).multiply(x).add(a.multiply(x)).add(b).mod(p);
        return lhs.equals(rhs);
    }

    private static boolean curveMatches(ECParameterSpec a, ECParameterSpec b) {
        EllipticCurve ca = a.getCurve(), cb = b.getCurve();
        BigInteger pa = ((ECFieldFp) ca.getField()).getP();
        BigInteger pb = ((ECFieldFp) cb.getField()).getP();
        return pa.equals(pb) && ca.getA().equals(cb.getA()) && ca.getB().equals(cb.getB())
                && a.getOrder().equals(b.getOrder()) && a.getCofactor() == b.getCofactor()
                && a.getGenerator().getAffineX().equals(b.getGenerator().getAffineX())
                && a.getGenerator().getAffineY().equals(b.getGenerator().getAffineY());
    }

    private static ECParameterSpec secp256r1Params() throws Exception {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        return params.getParameterSpec(ECParameterSpec.class);
    }
}
