package org.parkar.domain;

import java.util.Locale;

/** Task lifecycle status as defined by the source Excel document. */
public enum TaskStatus {
    TO_DO("To Do"),
    IN_PROGRESS("In-progress"),
    DONE("Done"),
    BLOCKED("Blocked");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Best-effort mapping of a free-form status cell value to an enum value. */
    public static TaskStatus fromCell(String raw) {
        if (raw == null || raw.isBlank()) {
            return TO_DO;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT).replace("_", " ").replace("-", " ");
        while (v.contains("  ")) {
            v = v.replace("  ", " ");
        }
        return switch (v) {
            case "to do", "todo", "to-do", "new", "open", "not started", "backlog" -> TO_DO;
            case "in progress", "inprogress", "progress", "ongoing", "wip" -> IN_PROGRESS;
            case "done", "completed", "complete", "closed", "finished" -> DONE;
            case "blocked", "blocker", "on hold", "impeded" -> BLOCKED;
            default -> null;
        };
    }
}
