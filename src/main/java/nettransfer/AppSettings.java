package nettransfer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AppSettings {
    private static final Logger LOG = Logger.getLogger(AppSettings.class.getName());
    private static final Path FILE = Paths.get(System.getProperty("user.home"), ".config", "nettransfer", "settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Set<String> enabledInterfaces = new LinkedHashSet<>();
    public boolean autoSelectAll = true;
    public String deviceId;

    /** Stable identity for discovery, persisted so restarting the app doesn't look like a new device to peers. */
    public String getOrCreateDeviceId() {
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = UUID.randomUUID().toString();
            save();
        }
        return deviceId;
    }

    public static AppSettings load() {
        try {
            if (Files.exists(FILE)) {
                AppSettings loaded = GSON.fromJson(Files.readString(FILE), AppSettings.class);
                if (loaded != null) {
                    if (loaded.enabledInterfaces == null) loaded.enabledInterfaces = new LinkedHashSet<>();
                    return loaded;
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not read " + FILE + ", using defaults", e);
        }
        return new AppSettings();
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not save " + FILE, e);
        }
    }

    public boolean isInterfaceEnabled(String name) {
        return autoSelectAll || enabledInterfaces.isEmpty() || enabledInterfaces.contains(name);
    }
}
