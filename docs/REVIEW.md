# Code review (Phase 1)

Scope: the whole project as of `main` (`abd2c44`). Every fix has its own commit and a JUnit test.

## Fixed

| # | Area | Problem | Fix |
|---|------|---------|-----|
| 1 | Protocol | `Handshake` allocated whatever key length the peer sent (up to 2 GB, or crashed on negatives). | Length bounded to 1..256 bytes. |
| 2 | Protocol | `TransferMessage.readFrom` trusted the frame length and returned `null` / threw unchecked Gson errors. | Frames bounded to 8 MB; null, typeless or malformed JSON is an `IOException`. |
| 3 | Validation | The transfer request was trusted: a lying `totalSize` bypassed the free-space check, negative sizes and duplicates were accepted, and a short `transferId` crashed the logger *inside the error handler* (no cleanup, no status). | `validateRequest`: UUID id, printable bounded sender name, sizes ≥ 0, no duplicates, totals must match the list; the displayed name is derived from the path. |
| 4 | File names | Only `..` escapes were blocked. Backslashes, drive letters, Windows reserved names (`CON`, `nul.txt`), trailing dots/spaces and control characters (log forging) passed. | `checkRelativePath` rejects all of these before resolving; the `startsWith(base)` check stays as defence in depth. |
| 5 | Overwrite | `uniquePath` checked existence, then `FileOutputStream` truncated: two transfers in the same second could clobber each other. | `createUnique` uses atomic `Files.createFile` and moves to `name_2.ext` on conflict. |
| 6 | Integrity | A missing `fileHash` silently skipped SHA-256 verification; unknown messages were ignored; `TRANSFER_COMPLETE` was accepted with files missing. | Strict sequence: `FILE_START` → data → `FILE_END` (same path, hash mandatory); anything else aborts and removes partial files. |
| 7 | Streaming | A file growing while the receiver decided sent extra bytes and desynced the stream; a shrinking one hung the peer. | The sender sends exactly the announced size and fails clearly if the file shrank. |
| 8 | Timeouts | The sending socket had no read timeout; the receiver waited forever for the user (holding a connection slot), and an exception in the UI callback deadlocked it. | 30 s read timeout on both sides, 60 s limit to answer a request, dialog auto-closes as a rejection. |
| 9 | Coupling | `FileTransferService` called `Platform.runLater`, so network code needed the JavaFX toolkit and could not be tested. | FX-thread hand-off moved to `MainController`; the protocol runs headless in tests. |
| 10 | Discovery | Unknown message types were treated as announcements; the rate-limiter map grew without bound under spoofed sources; the 1 KB buffer could truncate valid packets. | One tested `parse()` that drops anything invalid; map capped at 4096 sources; 2 KB buffer. |
| 11 | DoS | One LAN host could hold all 8 connection slots with idle sockets. | Max 2 concurrent connections per host. |
| 12 | Errors | `printStackTrace`, `System.err` and empty `catch` blocks hid failures (e.g. a second instance silently lost discovery). | `java.util.logging` everywhere in network code. |
| 13 | Cleanup | Untranslated Portuguese strings; unused `javafx-fxml` dependency; unused `TransferProgressPanel`; duplicated `formatSize`. | Translated / removed. `pom.xml` now uses `maven.compiler.release`, pinned plugins and JUnit 5. |

## Known limitations (not changed)

- **Discovery is not authenticated.** The UDP key is derived from a constant in the source code, so it only hides names from passive sniffers. Anyone on the LAN can announce a fake device or send a fake `GOODBYE`. The real protection is the 6-digit verification code shown on both screens, which the users must compare.
- **The LAN handshake is unauthenticated ECDH.** Without comparing the verification code, an active attacker on the LAN can sit in the middle. The Internet mode (Phase 2) uses pinned certificates instead.
- **No write timeout.** Java sockets have none; a receiver that stops reading stalls the sender until the user cancels (cancel closes the socket).
- File names valid on Linux but not on Windows (e.g. `a:b`) are rejected by every receiver, not only Windows ones, to keep the rule simple.
