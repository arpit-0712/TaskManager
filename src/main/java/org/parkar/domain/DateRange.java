package org.parkar.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Inclusive date range used for burndown and reporting. */
public record DateRange(LocalDate start, LocalDate end) {

    public DateRange {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end must not be null");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must not be before start");
        }
    }

    public List<LocalDate> days() {
        List<LocalDate> days = new ArrayList<>();
        start.datesUntil(end.plusDays(1)).forEach(days::add);
        return days;
    }

    public static DateRange covering(List<Task> tasks) {
        LocalDate min = null;
        LocalDate max = null;
        for (Task t : tasks) {
            min = earlier(min, t.startDate() != null ? t.startDate() : t.assignedDate());
            max = later(max, t.endDate());
        }
        if (min == null) {
            LocalDate today = LocalDate.now();
            return new DateRange(today, today);
        }
        if (max == null || max.isBefore(min)) {
            max = min;
        }
        return new DateRange(min, max);
    }

    private static LocalDate earlier(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}
