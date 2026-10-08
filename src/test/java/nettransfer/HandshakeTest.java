package nettransfer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HandshakeTest {

    @Test
    void rejectsOversizedPublicKeyLength() {
        byte[] hostile = ByteBuffer.allocate(4).putInt(Integer.MAX_VALUE).array();
        assertThrows(IOException.class, () ->
                Handshake.forReceiver(new ByteArrayInputStream(hostile), new ByteArrayOutputStream()));
    }

    @Test
    void rejectsNegativePublicKeyLength() {
        byte[] hostile = ByteBuffer.allocate(4).putInt(-1).array();
        assertThrows(IOException.class, () ->
                Handshake.forReceiver(new ByteArrayInputStream(hostile), new ByteArrayOutputStream()));
    }

    @Test
    void bothSidesDeriveSameKeyAndCode() throws Exception {
        PipedInputStream senderIn = new PipedInputStream(), receiverIn = new PipedInputStream();
        PipedOutputStream senderOut = new PipedOutputStream(receiverIn), receiverOut = new PipedOutputStream(senderIn);
        CompletableFuture<Handshake> receiver = CompletableFuture.supplyAsync(() -> {
            try { return Handshake.forReceiver(receiverIn, receiverOut); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        Handshake sender = Handshake.forSender(senderIn, senderOut);
        Handshake recv = receiver.get(10, TimeUnit.SECONDS);

        assertEquals(sender.getVerificationCode(), recv.getVerificationCode());
        assertArrayEquals(sender.aesKey.getEncoded(), recv.aesKey.getEncoded());
        assertArrayEquals(sender.getSendNoncePrefix(), recv.getRecvNoncePrefix());
    }
}
