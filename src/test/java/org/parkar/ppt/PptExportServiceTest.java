package org.parkar.ppt;

import org.parkar.chart.BurndownChartRenderer;
import org.parkar.domain.DateRange;
import org.parkar.domain.Task;
import org.parkar.domain.TaskStatus;
import org.parkar.domain.TimeEntry;
import org.parkar.service.BurndownService;
import org.parkar.service.DeveloperReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PptExportServiceTest {

    @Test
    void producesNonEmptyDeck(@TempDir Path dir) throws Exception {
        List<Task> tasks = List.of(
                new Task("Alice", "Login", "S1", TaskStatus.DONE, null,
                        LocalDate.of(2024, 10, 1), LocalDate.of(2024, 10, 2),
                        List.of(new TimeEntry(LocalDate.of(2024, 10, 1), 4))),
                new Task("Alice", "Dashboard", "S1", TaskStatus.IN_PROGRESS, null,
                        LocalDate.of(2024, 10, 2), LocalDate.of(2024, 10, 5), List.of()));

        DateRange range = new DateRange(LocalDate.of(2024, 10, 1), LocalDate.of(2024, 10, 5));
        var burndown = new BurndownService().compute("Alice", tasks, range);
        DeveloperReport report = new DeveloperReport("Alice", tasks, "Narrative text", burndown);

        Path out = dir.resolve("deck.pptx");
        new PptExportService(new BurndownChartRenderer()).export(out, "Test Deck", List.of(report));

        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 1000);
    }
}
