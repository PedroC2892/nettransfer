package nettransfer;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferMessageTest {
    private final Gson gson = new Gson();

    private static DataInputStream frame(int declaredLength, String body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(declaredLength);
        out.write(body.getBytes(StandardCharsets.UTF_8));
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    @Test
    void roundTrips() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TransferMessage.transferComplete("id").writeTo(new DataOutputStream(bytes), gson);
        TransferMessage m = TransferMessage.readFrom(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), gson);
        assertEquals("TRANSFER_COMPLETE", m.type);
    }

    @Test
    void rejectsOversizedFrame() {
        assertThrows(IOException.class, () -> TransferMessage.readFrom(frame(Integer.MAX_VALUE, ""), gson));
    }

    @Test
    void rejectsNegativeFrame() {
        assertThrows(IOException.class, () -> TransferMessage.readFrom(frame(-5, ""), gson));
    }

    @Test
    void rejectsJsonNull() {
        assertThrows(IOException.class, () -> TransferMessage.readFrom(frame(4, "null"), gson));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(IOException.class, () -> TransferMessage.readFrom(frame(5, "{oops"), gson));
    }
}
