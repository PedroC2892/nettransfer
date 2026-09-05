# NetTransfer

LAN file transfer with end-to-end encryption. No accounts, no internet, no
configuration — devices on the same network discover each other automatically
and exchange files over an encrypted channel.

## How it works

Devices broadcast their presence over UDP. When another device appears, you
select it, pick files, and send. The connection is encrypted with ephemeral
ECDH P-256 keys so no key material persists after the transfer. A six-digit
verification code lets you confirm the connection is not being intercepted.

## Security

- Ephemeral ECDH P-256 key exchange (new key pair per connection, forward secrecy)
- AES-256-GCM authenticated encryption with sequential nonces
- HKDF-SHA256 key derivation — key, verification code, and nonce prefixes are
  derived independently
- Public key validation against the secp256r1 curve to prevent invalid-curve attacks
- Six-digit out-of-band verification code to detect active MITM on the LAN
- Per-file SHA-256 integrity check; file is deleted on mismatch
- Bounded record size and connection pool to resist DoS
- UDP discovery rate-limiting per source IP

See `SECURITY_ANALYSIS.md` for the full threat model and audit.

## Requirements

- Java 17 or later
- Maven 3.6 or later (to build)
- A desktop with a display (JavaFX requires a graphical environment)

On Arch Linux:

```
sudo pacman -S jdk17-openjdk maven
```

On Debian/Ubuntu:

```
sudo apt install openjdk-17-jdk maven
```

JavaFX is pulled from Maven Central during the build — no separate installation needed.

If the app starts but shows no text, install the DejaVu fonts:

```
# Arch
sudo pacman -S ttf-dejavu

# Debian/Ubuntu
sudo apt install fonts-dejavu
```

## Build and run

```
git clone https://github.com/pedrocruz/nettransfer.git
cd nettransfer
mvn package -DskipTests
./run.sh
```

`run.sh` sets the JavaFX module path from your local Maven repository and
launches the fat jar. Running the jar directly with `java -jar` will fail
because of how JavaFX ships its native libraries — always use `run.sh`.

During development you can also use:

```
mvn javafx:run
```

## Install as a desktop app (Linux)

To make NetTransfer appear in your application launcher:

```
# 1. Put the launcher somewhere on your PATH
cp run.sh ~/.local/bin/nettransfer
chmod +x ~/.local/bin/nettransfer

# 2. Register the .desktop entry
cat > ~/.local/share/applications/nettransfer.desktop << 'EOF'
[Desktop Entry]
Type=Application
Name=NetTransfer
Comment=LAN file transfer with end-to-end encryption
Exec=/home/YOUR_USERNAME/.local/bin/nettransfer
Icon=folder-remote
Terminal=false
Categories=Network;FileTransfer;
Keywords=transfer;lan;file;network;send;receive;
StartupNotify=false
EOF

# 3. Update the application database
update-desktop-database ~/.local/share/applications/
```

Replace `YOUR_USERNAME` with your actual username in the `Exec=` line, or use
`$HOME` — whichever your launcher expands.

The app will appear when you search for "nettransfer" or "transfer" in your
launcher.

## Usage

- Launch the app on two or more machines on the same network.
- Devices appear automatically in the list within a few seconds.
- Select one or more devices, press `F` to pick files, then `Ctrl+S` to send.
- The receiving machine is prompted to accept or reject each transfer.
- Received files land in `~/Downloads/NetTransfer/<timestamp>/`.

### Keyboard shortcuts

| Key | Action |
|---|---|
| `Ctrl+1` | Devices tab |
| `Ctrl+2` | Logs tab |
| `Ctrl+3` | Settings tab |
| `F` | Choose files to send |
| `Ctrl+S` | Send to selected devices |
| `Ctrl+A` | Select all devices |
| `Esc` | Clear selection |
| `Ctrl+D` | Open downloads folder |
| Arrows | Navigate device list |
| `Shift+Arrow` | Extend selection |
| `Enter` / `Space` | Toggle focused device |

## Settings

The Settings tab lets you choose which network interfaces NetTransfer listens
and broadcasts on. Useful if you have multiple interfaces (ethernet, Wi-Fi,
VPN) and want to restrict discovery to one.

Settings are saved to `~/.config/nettransfer/settings.json`.

## Logs

A transfer log is written to `~/Downloads/NetTransfer/nettransfer.log`. Open
it from the app with `Ctrl+L` in the Logs tab.

## License

MIT
