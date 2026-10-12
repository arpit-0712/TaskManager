package org.parkar.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * A single task row imported from the source Excel sheet, including day-wise time spent.
 */
public record Task(
        String developer,
        String name,
        String sprint,
        TaskStatus status,
        LocalDate assignedDate,
        LocalDate startDate,
        LocalDate endDate,
        List<TimeEntry> timeSpent) {

    public Task {
        timeSpent = timeSpent == null ? List.of() : List.copyOf(timeSpent);
    }

    /** Total logged hours across all day-wise entries. */
    public double totalHours() {
        return timeSpent.stream().mapToDouble(TimeEntry::hours).sum();
    }

    /**
     * The date on which the task is considered complete, or {@code null} if it is
     * not (yet) done. Used to drive the task-count based burndown.
     */
    public LocalDate completedOn() {
        return status == TaskStatus.DONE ? endDate : null;
    }

    /** True when the task is still open (remaining) as of the given date. */
    public boolean isRemainingOn(LocalDate date) {
        LocalDate done = completedOn();
        return done == null || done.isAfter(date);
    }
}
