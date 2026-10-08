package nettransfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import static nettransfer.TestSupport.file;
import static nettransfer.TestSupport.request;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {"../evil", "a/../../evil", "/etc/passwd", "a//b", "a/", "..", ".",
            "C:\\x", "a\\b", "CON", "nul.txt", "Com1.log", "trailing.", "trailing ", "new\nline", "a:b", "q?"})
    void rejectsUnsafePaths(String path) {
        assertThrows(IOException.class, () -> FileTransferService.checkRelativePath(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.txt", "dir/sub/file.tar.gz", "Ünïcødé ✓.pdf", ".hidden", "console.txt"})
    void acceptsNormalPaths(String path) {
        assertDoesNotThrow(() -> FileTransferService.checkRelativePath(path));
    }

    @Test
    void rejectsTotalsThatDoNotMatchFiles() {
        TransferMessage r = request(List.of(file("a", 10)));
        r.totalSize = 1; // lie to pass the free-space check
        assertThrows(IOException.class, () -> FileTransferService.validateRequest(r));
    }

    @Test
    void rejectsNegativeSize() {
        TransferMessage r = request(new ArrayList<>(List.of(file("a", 10), file("b", -10))));
        r.totalSize = 0;
        assertThrows(IOException.class, () -> FileTransferService.validateRequest(r));
    }

    @Test
    void rejectsDuplicates() {
        assertThrows(IOException.class, () -> FileTransferService.validateRequest(request(List.of(file("a", 1), file("a", 1)))));
    }

    @Test
    void rejectsMissingFileList() {
        TransferMessage r = request(List.of());
        r.files = null;
        assertThrows(IOException.class, () -> FileTransferService.validateRequest(r));
    }

    @Test
    void displayNameIsDerivedFromPath() throws IOException {
        TransferMessage r = request(List.of(new TransferMessage.FileEntry("innocent.txt", "dir/evil.exe", 1, false)));
        FileTransferService.validateRequest(r);
        assertEquals("evil.exe", r.files.get(0).name);
    }

    /** A short transfer id used to crash the logger inside the error handler, skipping cleanup and status. */
    @Test
    void malformedRequestFailsCleanlyWithoutAskingUser() throws Exception {
        TestSupport.RecordingListener receiver = new TestSupport.RecordingListener(true);
        ServerSocket server = TestSupport.startReceiver(receiver);
        try (TestSupport.RogueSender rogue = new TestSupport.RogueSender(server)) {
            TransferMessage r = request(List.of(file("a", 1)));
            r.transferId = "x";
            rogue.send(r);
            assertEquals(TransferStatus.ERROR, receiver.await());
        }
        assertFalse(receiver.asked);
    }

    @Test
    void traversalInRequestIsRejected() throws Exception {
        TestSupport.RecordingListener receiver = new TestSupport.RecordingListener(true);
        ServerSocket server = TestSupport.startReceiver(receiver);
        try (TestSupport.RogueSender rogue = new TestSupport.RogueSender(server)) {
            rogue.send(request(List.of(file("../../.bashrc", 1))));
            assertEquals(TransferStatus.ERROR, receiver.await());
        }
        assertFalse(receiver.asked);
    }
}
