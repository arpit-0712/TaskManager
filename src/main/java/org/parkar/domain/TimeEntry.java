package org.parkar.domain;

import java.time.LocalDate;

/** A single day-wise time-spent entry for a task. */
public record TimeEntry(LocalDate date, double hours) {

    public TimeEntry {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        if (hours < 0) {
            throw new IllegalArgumentException("hours must not be negative");
        }
    }
}
