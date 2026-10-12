package org.parkar.llm;

import org.parkar.domain.Task;
import org.parkar.domain.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmSummaryServiceTest {

    @Test
    void fallsBackWhenLlmUnavailable() {
        LlmSummaryService service = new LlmSummaryService(new NoOpClient());
        List<Task> tasks = List.of(
                new Task("Alice", "Login", "S1", TaskStatus.DONE, null, null, LocalDate.of(2024, 10, 2), List.of()),
                new Task("Alice", "Dashboard", "S1", TaskStatus.BLOCKED, null, null, LocalDate.of(2024, 10, 5), List.of()));

        String summary = service.summarize("Alice", tasks);

        assertTrue(summary.contains("Alice"));
        assertTrue(summary.toLowerCase().contains("blocked"));
        assertTrue(summary.contains("2"));
    }

    @Test
    void emptyTaskList() {
        LlmSummaryService service = new LlmSummaryService(new NoOpClient());
        assertTrue(service.summarize("Bob", List.of()).contains("No tasks"));
    }
}
