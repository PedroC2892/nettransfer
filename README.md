# NetTransfer

LAN file transfer. Devices on the same network discover each other automatically,
no accounts or configuration needed.

## Requirements

- Java 17+
- Maven 3.6+

```
# Arch
sudo pacman -S jdk17-openjdk maven

# Debian/Ubuntu
sudo apt install openjdk-17-jdk maven
```

On Windows, install both with [winget](https://learn.microsoft.com/windows/package-manager/winget/):

```
winget install EclipseAdoptium.Temurin.17.JDK
winget install Apache.Maven
```

## Build and run

### Linux

```
git clone https://github.com/PedroC2892/nettransfer.git
cd nettransfer
mvn package -DskipTests
./run.sh
```

Always use `run.sh` to launch — running the jar directly with `java -jar` will fail.

### Windows

```
git clone https://github.com/PedroC2892/nettransfer.git
cd nettransfer
mvn javafx:run
```

The first run needs Maven to fetch the Windows JavaFX natives, so use `mvn javafx:run` once —
after that, `mvn package -DskipTests` followed by `run.bat` works the same way `run.sh` does on Linux.

## Install as a desktop app

```
cp run.sh ~/.local/bin/nettransfer
chmod +x ~/.local/bin/nettransfer

cat > ~/.local/share/applications/nettransfer.desktop << 'EOF'
[Desktop Entry]
Type=Application
Name=NetTransfer
Exec=/home/YOUR_USERNAME/.local/bin/nettransfer
Icon=folder-remote
Terminal=false
Categories=Network;FileTransfer;
EOF

update-desktop-database ~/.local/share/applications/
```

## Keyboard shortcuts

| Key | Action |
|---|---|
| `F` | Choose files |
| `Ctrl+S` | Send |
| `Ctrl+A` | Select all |
| `Esc` | Clear selection |
| `Ctrl+D` | Open downloads folder |
| `Ctrl+1/2/3` | Switch tabs |
| Arrows | Navigate devices |

`Ctrl` is `Cmd` on macOS. Received files go to `~/Downloads/NetTransfer/<timestamp>/`
(`%USERPROFILE%\Downloads\NetTransfer\<timestamp>\` on Windows).

## License

MIT
