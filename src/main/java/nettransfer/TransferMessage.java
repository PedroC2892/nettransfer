package nettransfer;

import com.google.gson.Gson;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class TransferMessage {
    public String type;
    public String transferId;
    public String senderName;
    public List<FileEntry> files;
    public long totalSize;
    public int totalFiles;
    public boolean accepted;
    public String relativePath;
    public long size;
    public boolean isDirectory;
    public String fileHash;

    public static class FileEntry {
        public String name;
        public String relativePath;
        public long size;
        public boolean isDirectory;

        public FileEntry(String name, String relativePath, long size, boolean isDirectory) {
            this.name = name;
            this.relativePath = relativePath;
            this.size = size;
            this.isDirectory = isDirectory;
        }
    }

    public static TransferMessage transferRequest(String transferId, String senderName, List<FileEntry> files, long totalSize, int totalFiles) {
        TransferMessage m = new TransferMessage();
        m.type = "TRANSFER_REQUEST";
        m.transferId = transferId;
        m.senderName = senderName;
        m.files = files;
        m.totalSize = totalSize;
        m.totalFiles = totalFiles;
        return m;
    }

    public static TransferMessage transferResponse(String transferId, boolean accepted) {
        TransferMessage m = new TransferMessage();
        m.type = "TRANSFER_RESPONSE";
        m.transferId = transferId;
        m.accepted = accepted;
        return m;
    }

    public static TransferMessage fileStart(String transferId, String relativePath, long size, boolean isDirectory) {
        TransferMessage m = new TransferMessage();
        m.type = "FILE_START";
        m.transferId = transferId;
        m.relativePath = relativePath;
        m.size = size;
        m.isDirectory = isDirectory;
        return m;
    }

    public static TransferMessage fileEnd(String transferId, String relativePath, String fileHash) {
        TransferMessage m = new TransferMessage();
        m.type = "FILE_END";
        m.transferId = transferId;
        m.relativePath = relativePath;
        m.fileHash = fileHash;
        return m;
    }

    public static TransferMessage transferComplete(String transferId) {
        TransferMessage m = new TransferMessage();
        m.type = "TRANSFER_COMPLETE";
        m.transferId = transferId;
        return m;
    }

    public void writeTo(DataOutputStream out, Gson gson) throws IOException {
        byte[] data = gson.toJson(this).getBytes(StandardCharsets.UTF_8);
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    public static TransferMessage readFrom(DataInputStream in, Gson gson) throws IOException {
        int len = in.readInt();
        byte[] data = new byte[len];
        in.readFully(data);
        return gson.fromJson(new String(data, StandardCharsets.UTF_8), TransferMessage.class);
    }
}
