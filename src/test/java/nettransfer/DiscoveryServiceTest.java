package nettransfer;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryServiceTest {
    private final DiscoveryService service = new DiscoveryService(40000);

    private DiscoveryMessage roundTrip(String json) throws Exception {
        byte[] packet = service.encryptPacket(json.getBytes(StandardCharsets.UTF_8));
        return DiscoveryService.parse(packet, packet.length);
    }

    @Test
    void acceptsValidAnnouncement() throws Exception {
        DiscoveryMessage m = roundTrip("{\"type\":\"DISCOVER\",\"id\":\"x\",\"userName\":\"u\",\"hostName\":\"h\",\"tcpPort\":40000}");
        assertNotNull(m);
        assertEquals("x", m.id);
    }

    /** Any JSON problem must drop the packet; an escaping exception would end the receiver loop. */
    @Test
    void ignoresMalformedJsonInsteadOfCrashing() throws Exception {
        assertNull(roundTrip("{\"type\":\"DISCOVER\",\"tcpPort\":\"not a number\"}"));
        assertNull(roundTrip("[1,2,3]"));
        assertNull(roundTrip("null"));
        assertNull(roundTrip("{"));
    }

    @Test
    void ignoresUnknownTypeAndBadPort() throws Exception {
        assertNull(roundTrip("{\"type\":\"PWN\",\"id\":\"x\",\"userName\":\"u\",\"hostName\":\"h\",\"tcpPort\":40000}"));
        assertNull(roundTrip("{\"type\":\"DISCOVER\",\"id\":\"x\",\"userName\":\"u\",\"hostName\":\"h\",\"tcpPort\":80}"));
    }

    @Test
    void ignoresUnencryptedOrShortPackets() {
        byte[] plain = "{\"type\":\"DISCOVER\"}".getBytes(StandardCharsets.UTF_8);
        assertNull(DiscoveryService.parse(plain, plain.length));
        assertNull(DiscoveryService.parse(new byte[3], 3));
    }

    @Test
    void rateLimiterStaysBoundedUnderSpoofedSources() {
        Map<String, java.util.Deque<Long>> recent = new HashMap<>();
        for (int i = 0; i < 100_000; i++) DiscoveryService.isRateLimited(recent, "10.0." + (i >> 8) + "." + (i & 255));
        assertTrue(recent.size() <= 4097);
    }

    @Test
    void rateLimiterBlocksFloodFromOneSource() {
        Map<String, java.util.Deque<Long>> recent = new HashMap<>();
        for (int i = 0; i < 10; i++) assertFalse(DiscoveryService.isRateLimited(recent, "1.2.3.4"));
        assertTrue(DiscoveryService.isRateLimited(recent, "1.2.3.4"));
    }
}
