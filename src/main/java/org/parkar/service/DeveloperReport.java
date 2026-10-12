package org.parkar.service;

import org.parkar.domain.Task;

import java.util.List;

/** A per-developer bundle passed to the PPTX exporter. */
public record DeveloperReport(
        String developer,
        List<Task> tasks,
        String summary,
        BurndownSeries burndown) {

    public DeveloperReport {
        tasks = List.copyOf(tasks);
    }
}
