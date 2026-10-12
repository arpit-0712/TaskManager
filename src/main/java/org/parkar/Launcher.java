package org.parkar;

import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Plain launcher so the application can start both the JavaFX UI and the
 * headless {@code --export} mode without triggering the JavaFX launcher check.
 */
@NoArgsConstructor
public final class Launcher {

    private static final Logger log = LoggerFactory.getLogger(Launcher.class);

    public static void main(String[] args) {
        log.info("TaskManager launcher started with args {}", Arrays.toString(args));
        try {
            App.main(args);
        } catch (RuntimeException e) {
            log.error("Application failed to start", e);
            throw e;
        }
    }
}
