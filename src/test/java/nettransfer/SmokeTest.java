package nettransfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmokeTest {
    @Test
    void formatsSizes() {
        assertEquals("512 B", TransferLogger.formatSize(512));
    }
}
