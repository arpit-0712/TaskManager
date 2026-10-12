package org.parkar.service;

import org.parkar.domain.DateRange;

import java.time.LocalDate;
import java.util.List;

/** Computed burndown data ready for rendering or reporting. */
public record BurndownSeries(
        String owner,
        DateRange range,
        List<LocalDate> dates,
        List<Double> ideal,
        List<Double> actual,
        int totalTasks) {

    public BurndownSeries {
        dates = List.copyOf(dates);
        ideal = List.copyOf(ideal);
        actual = List.copyOf(actual);
    }
}
