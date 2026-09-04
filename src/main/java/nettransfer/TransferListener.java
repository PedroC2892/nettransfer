package nettransfer;

import java.util.List;

public interface TransferListener {
    boolean onIncomingRequest(String transferId, String senderName, String senderIp,
                              List<TransferMessage.FileEntry> files, long totalSize, int totalFiles,
                              long usableSpace, boolean enoughSpace, String verificationCode);

    void onProgress(String transferId, long transferred, long total, double speedBytesPerSec);

    void onStatusChange(String transferId, TransferStatus status);

    default void onReceiveDir(String transferId, String dirPath) {}

    default void onSendStart(String transferId, String peerName, String peerIp,
                             List<TransferMessage.FileEntry> files, long totalSize) {}

    default void onVerificationCode(String transferId, String code) {}
}
