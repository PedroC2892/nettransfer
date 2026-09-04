# NetTransfer — Java LAN File Transfer App

## Architecture Overview

NetTransfer is a Java 17 + Swing application for peer discovery and file transfer on a local network.

### Existing files

- `Main.java` — Entry point. Starts DiscoveryService and PeerListFrame.
- `Peer.java` — Peer model: id, name, hostName, ipAddress, tcpPort.
- `DiscoveryMessage.java` — UDP broadcast message: type, id, userName, hostName, tcpPort.
- `DiscoveryService.java` — UDP broadcast/receive on port 54321, 5s interval. Uses Gson for JSON.
- `ConnectionManager.java` — Empty. Intended for TCP connection management.
- `FileTransferService.java` — Empty. Intended for file transfer logic.
- `PeerListFrame.java` — Basic Swing JTable showing discovered peers.

### Dependencies

- Java 17
- Gson 2.11.0 (via Maven)
- Swing (standard library)

### Network

- UDP port 54321 — discovery broadcasts
- TCP — file transfer (port to be assigned, included in discovery message)

## What needs to be implemented

### Core (mandatory)
1. TCP server in each instance (fixed or dynamic port, published in broadcasts)
2. Transfer request protocol: sender → metadata → receiver accepts/rejects → data flows
3. File transfer over TCP: streaming (no full-file-in-memory), chunked, with progress
4. Directory transfer preserving structure
5. Received files saved to `~/Downloads/NetTransfer/` (Linux convention)
6. Path traversal protection: never write outside the destination directory
7. Duplicate file handling: rename with suffix if file exists

### GUI (mandatory)
- Device cards instead of table (JPanel grid, one card per peer)
- Multi-selection of devices (click to select/deselect, visual highlight)
- Send files button (enabled when ≥1 device selected and files chosen)
- File selection: drag & drop + file chooser button (supports files and directories)
- Transfer request dialog on receiver: shows sender, file names/types/count/total size, Accept/Reject
- Transfer progress panel: progress bar, percentage, speed (MB/s), transferred/total, ETA, status
- Per-device status when sending to multiple peers: waiting / transferring / done / rejected / error
- Non-blocking GUI during transfers (all network work on background threads)

### Robustness
- Handle disconnected peer during transfer gracefully
- Handle rejected transfer
- Handle connection errors
- Handle write errors (disk full, permissions)
- Don't crash if one peer transfer fails; continue others

## Code style

- Follow existing style (public fields on Peer/DiscoveryMessage, Gson for JSON)
- Keep code simple and clean — no unnecessary comments
- No new layers or abstractions beyond what is clearly needed
- Use standard Java Swing patterns (SwingUtilities.invokeLater for UI updates)
- All network/IO on background threads, never on EDT

## Build & run

```
mvn package -q
java -jar target/gson-1.0-SNAPSHOT.jar
# or with Maven exec plugin (if configured)
mvn compile exec:java -Dexec.mainClass=nettransfer.Main
```

## Git

Commit after each significant implementation. Verify build before committing.
