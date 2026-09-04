# NetTransfer — Java LAN File Transfer App

## Stack

- Java 17, JavaFX 21 (NOT Swing — migrated), Gson 2.11.0, Maven
- Run: `mvn javafx:run`  ·  Build: `mvn package`
- Entry point: `nettransfer.App`

## Architecture

- `App.java` — JavaFX Application. Starts ConnectionManager, DiscoveryService, MainController.
- `MainController.java` — All UI. Tab-based: Devices / Logs / Settings. Full-screen overlay during transfers.
- `Peer.java` — id, name, hostName, ipAddress, tcpPort
- `DiscoveryMessage.java` — UDP broadcast payload
- `DiscoveryService.java` — UDP broadcast/receive, port 54321, 5s interval, per-interface send, rate-limited receive
- `NetworkInterfaceInfo.java` — model + enumeration of usable network interfaces
- `AppSettings.java` — persists enabled interfaces to ~/.config/nettransfer/settings.json
- `ConnectionManager.java` — TCP ServerSocket(0), bounded thread pool (max 8 concurrent), 30s socket timeout
- `FileTransferService.java` — send/receive, 64KB chunks, streaming, disk space checks, partial cleanup, per-file SHA-256
- `Handshake.java` — ECDH P-256 ephemeral → HKDF-SHA256 (key, verification code, nonce prefixes), public key validation
- `EncryptedOutputStream` / `EncryptedInputStream` — AES-256-GCM records with sequential nonce, bounded record size
- `TransferMessage.java` — length-prefixed JSON protocol
- `TransferLogger.java` — writes to ~/Downloads/NetTransfer/nettransfer.log
- `TransferStatus.java` — WAITING, TRANSFERRING, DONE, REJECTED, ERROR
- `TransferListener.java` — callback interface
- `app.css` in src/main/resources/nettransfer/ — monochromatic dark theme

## UI conventions (IMPORTANT — user is particular about these)

- **Monochromatic only.** Blacks #111111/#1a1a1a, greys #2a2a2a/#555/#888/#aaa/#ccc, white #f0f0f0.
  NO colours, NO accent hues, NO green/red/orange status colours.
- **All UI text in English.**
- **Keyboard-first.** Every clickable thing must have a keyboard binding, shown in the
  button label as `[Key]` e.g. `Send  [Ctrl+S]`.
- Minimal, no clutter, no badges, no decorative metadata.
- No drag-and-drop zone (removed deliberately).

## Existing keybinds

- `Ctrl+1` Devices tab · `Ctrl+2` Logs tab · `Ctrl+3` Settings tab
- `←/→/↑/↓` navigate + select device cards · `Shift+arrow` extend selection
- `Enter` / `Space` toggle focused card · `Ctrl+A` select all
- `F` file chooser · `Ctrl+S` send · `Esc` clear selection
- `Ctrl+D` open downloads folder · `Ctrl+L` open log file (logs tab)
- Overlay: `O` open folder · `Esc` close (when all transfers finished)
- Logs: arrows/PgUp/PgDn/Home/End scroll · `Ctrl+F` search · `Ctrl +/-/0` zoom
- Settings: `←/→/↑/↓` navigate interface rows · `Enter`/`Space` toggle row ·
  `Ctrl+A` select all · `Ctrl+Shift+A` deselect all

## Code style

- Simple and clean. No excessive comments.
- No new layers or abstractions unless clearly needed.
- All network/IO on background threads. UI updates via `Platform.runLater`.
- Follow existing patterns (public fields on Peer/DiscoveryMessage, Gson for JSON).

## Git

Commit after each coherent implementation. Verify `mvn compile` passes before committing.
