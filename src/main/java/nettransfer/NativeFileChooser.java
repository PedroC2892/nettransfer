package nettransfer;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NativeFileChooser {

    // Returns selected files/dirs. Falls back to JFileChooser if zenity unavailable.
    public static List<File> open(JFrame parent) {
        if (isZenityAvailable()) {
            return openWithZenity();
        }
        return openWithSwing(parent);
    }

    private static boolean isZenityAvailable() {
        try {
            Process p = new ProcessBuilder("which", "zenity").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<File> openWithZenity() {
        List<File> result = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "zenity", "--file-selection",
                "--multiple",
                "--title=Selecionar ficheiros ou pastas",
                "--separator=\n"
            );
            pb.redirectErrorStream(false);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    result.add(new File(trimmed));
                }
            }
            p.waitFor();
        } catch (Exception e) {
            // fallback handled by caller checking for empty result
        }
        return result;
    }

    private static List<File> openWithSwing(JFrame parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            return Arrays.asList(chooser.getSelectedFiles());
        }
        return List.of();
    }
}
