package org.parkar.service;

import org.parkar.domain.DateRange;
import org.parkar.domain.Task;
import org.parkar.domain.TaskStatus;
import org.parkar.domain.TimeEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BurndownServiceTest {

    private final BurndownService service = new BurndownService();

    private Task task(String name, TaskStatus status, LocalDate end) {
        return new Task("Alice", name, "S1", status, end.minusDays(5), end.minusDays(5), end, List.of());
    }

    @Test
    void burnsDownTaskCount() {
        List<Task> tasks = List.of(
                task("A", TaskStatus.DONE, LocalDate.of(2024, 10, 2)),
                task("B", TaskStatus.DONE, LocalDate.of(2024, 10, 4)),
                task("C", TaskStatus.IN_PROGRESS, LocalDate.of(2024, 10, 6)),
                task("D", TaskStatus.BLOCKED, LocalDate.of(2024, 10, 6)));

        DateRange range = new DateRange(LocalDate.of(2024, 10, 1), LocalDate.of(2024, 10, 5));
        BurndownSeries series = service.compute("Alice", tasks, range);

        assertEquals(5, series.dates().size());
        assertEquals(4.0, series.actual().get(0));
        assertEquals(3.0, series.actual().get(1));
        assertEquals(3.0, series.actual().get(2));
        assertEquals(2.0, series.actual().get(3));
        assertEquals(2.0, series.actual().get(4));

        assertEquals(4.0, series.ideal().get(0));
        assertEquals(0.0, series.ideal().get(4));
    }

    @Test
    void idealIsLinear() {
        List<Task> tasks = List.of(
                task("A", TaskStatus.TO_DO, LocalDate.of(2024, 10, 10)),
                task("B", TaskStatus.TO_DO, LocalDate.of(2024, 10, 10)));
        DateRange range = new DateRange(LocalDate.of(2024, 10, 1), LocalDate.of(2024, 10, 3));
        BurndownSeries series = service.compute("Alice", tasks, range);
        assertEquals(2.0, series.ideal().get(0));
        assertEquals(1.0, series.ideal().get(1));
        assertEquals(0.0, series.ideal().get(2));
    }

    @Test
    void totalHoursAggregates() {
        Task t = new Task("Bob", "X", "S1", TaskStatus.DONE,
                LocalDate.now(), LocalDate.now(), LocalDate.now(),
                List.of(new TimeEntry(LocalDate.now(), 2.5), new TimeEntry(LocalDate.now().plusDays(1), 1.5)));
        assertEquals(4.0, t.totalHours());
    }
}
