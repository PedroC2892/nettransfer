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

On Windows, if you already have [winget](https://learn.microsoft.com/windows/package-manager/winget/)
(Windows 11, or Windows 10 with the App Installer from the Microsoft Store), this does both in one go:

```
winget install EclipseAdoptium.Temurin.17.JDK
winget install Apache.Maven
```

If you don't have `winget`, or aren't comfortable with the command line, see the
[step-by-step Windows guide](#windows-step-by-step-no-command-line-experience-needed) below instead.

## Build and run

### Linux

```
git clone https://github.com/PedroC2892/nettransfer.git
cd nettransfer
mvn package -DskipTests
./run.sh
```

Always use `run.sh` to launch — running the jar directly with `java -jar` will fail.

### Windows (quick version, if you already have Java + Maven)

```
git clone https://github.com/PedroC2892/nettransfer.git
cd nettransfer
mvn javafx:run
```

The first run needs Maven to fetch the Windows JavaFX natives, so use `mvn javafx:run` once —
after that, `mvn package -DskipTests` followed by double-clicking `run.bat` works the same way
`run.sh` does on Linux.

### Windows, step-by-step (no command-line experience needed)

This walks through everything from a blank Windows 10/11 install — every step is a program
you download and double-click, not something you type.

**1. Install Java.**

- Go to <https://adoptium.net/temurin/releases/?version=17&os=windows&package=jdk> and download
  the `.msi` for your system (almost always **x64**).
- Double-click the downloaded file. Click Next through the wizard — the defaults already include
  "Set JAVA_HOME variable" and "Add to PATH", which is what you want. Click Install, then Finish.
- Check it worked: press the Windows key, type `powershell`, press Enter. In the black/blue window
  that opens, type `java -version` and press Enter — you should see a line mentioning version 17
  or higher. If instead you see "java is not recognized", close that window, open a **new**
  PowerShell window (Windows only picks up the PATH change for windows opened after installing),
  and try again.

**2. Install Maven.**

Maven doesn't have an installer — it's just a folder you unzip.

- Go to <https://maven.apache.org/download.cgi> and download the file called
  "Binary zip archive" (`apache-maven-x.y.z-bin.zip`).
- Right-click the downloaded zip → **Extract All...** → extract it to `C:\maven`, so you end up
  with a folder like `C:\maven\apache-maven-3.9.16`.
- There's no need to touch any Windows settings for this — the commands below always point
  directly at that folder.

**3. Download NetTransfer itself.**

- On the [GitHub page for this project](https://github.com/PedroC2892/nettransfer), click the
  green **Code** button → **Download ZIP**.
- Right-click the downloaded zip → **Extract All...** → extract it somewhere easy to find, e.g.
  `C:\Users\<you>\nettransfer`.

**4. First run.**

Open PowerShell (Windows key → type `powershell` → Enter), then paste these one at a time,
replacing `<you>` and the Maven folder name with your actual paths/version:

```
cd C:\Users\<you>\nettransfer
C:\maven\apache-maven-3.9.16\bin\mvn.cmd javafx:run
```

The first time, this downloads a few things over the internet (needs a working connection) and
then opens the NetTransfer window. This can take a minute or two — that's normal.

**5. Windows Firewall will ask for permission.**

The first time NetTransfer tries to talk to other devices, Windows shows a
"Windows Defender Firewall has blocked some features of this app" popup. Tick **both** the
Private and Public networks checkboxes and click **Allow access** — otherwise other devices on
the network won't be found, and you won't be able to send or receive files.

**6. Everyday use, after the first run.**

You don't need Maven or PowerShell for this — just:

```
C:\maven\apache-maven-3.9.16\bin\mvn.cmd package -DskipTests
```

once, and from then on double-click `run.bat` inside the `nettransfer` folder to launch the app,
the same way `run.sh` is used on Linux.

**Troubleshooting**

- *"java is not recognized as an internal or external command"* — Java isn't installed or isn't
  on PATH; redo step 1, making sure "Add to PATH" was checked, and open a fresh PowerShell window.
- *Other devices don't show up* — almost always the Firewall prompt from step 5 was dismissed or
  blocked instead of allowed. Open **Windows Security → Firewall & network protection → Allow an
  app through firewall**, find `java`, and make sure both Private and Public are ticked. Also
  double-check both devices are actually on the same network.
- *Where do received files go?* — `%USERPROFILE%\Downloads\NetTransfer\<date-time>\`.

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
