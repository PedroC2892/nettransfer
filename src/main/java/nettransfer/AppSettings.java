package nettransfer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

public class AppSettings {
    private static final Path FILE = Paths.get(System.getProperty("user.home"), ".config", "nettransfer", "settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Set<String> enabledInterfaces = new LinkedHashSet<>();
    public boolean autoSelectAll = true;

    public static AppSettings load() {
        try {
            if (Files.exists(FILE)) {
                AppSettings loaded = GSON.fromJson(Files.readString(FILE), AppSettings.class);
                if (loaded != null) {
                    if (loaded.enabledInterfaces == null) loaded.enabledInterfaces = new LinkedHashSet<>();
                    return loaded;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through to defaults
        }
        return new AppSettings();
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[AppSettings] failed to save: " + e.getMessage());
        }
    }

    public boolean isInterfaceEnabled(String name) {
        return autoSelectAll || enabledInterfaces.isEmpty() || enabledInterfaces.contains(name);
    }
}
