package org.parkar.excel;

import org.parkar.domain.Task;

import java.util.List;

/** Outcome of an Excel import: parsed tasks plus diagnostic metadata. */
public record ImportResult(List<Task> tasks, List<String> warnings, List<String> timeColumns) {

    public ImportResult {
        tasks = List.copyOf(tasks);
        warnings = List.copyOf(warnings);
        timeColumns = List.copyOf(timeColumns);
    }
}
