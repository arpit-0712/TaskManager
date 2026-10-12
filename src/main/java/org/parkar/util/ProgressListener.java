package org.parkar.util;

/** Callback used to report the progress of a long-running operation. */
@FunctionalInterface
public interface ProgressListener {

    /**
     * @param fraction completion ratio in the range {@code [0, 1]}
     * @param message  optional human-readable description of the current step
     */
    void onProgress(double fraction, String message);
}
