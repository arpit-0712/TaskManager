package org.parkar.llm;

import org.parkar.domain.Task;
import org.parkar.domain.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/**
 * Produces a natural-language status narrative per developer using the configured
 * LLM, falling back to a deterministic summary when the LLM is unavailable.
 */
public class LlmSummaryService {

    private static final Logger log = LoggerFactory.getLogger(LlmSummaryService.class);

    private static final String SYSTEM_PROMPT = """
            You are an experienced engineering manager writing concise sprint status
            narratives. Given a developer's task list, produce a short report (4-6 bullet
            points) covering: overall progress, completed work, work in progress, any
            blockers or risks, and a recommended focus for the next few days. Be factual,
            reference task names, and do not invent data. Plain text, no markdown headings.
            """;

    private final LlmClient client;

    public LlmSummaryService(LlmClient client) {
        this.client = client;
    }

    public String summarize(String developer, List<Task> tasks) {
        if (tasks.isEmpty()) {
            return "No tasks assigned.";
        }
        try {
            return client.complete(SYSTEM_PROMPT, buildPrompt(developer, tasks));
        } catch (LlmException e) {
            log.info("LLM summary unavailable for {} ({}); using template", developer, e.getMessage());
            return fallback(developer, tasks);
        }
    }

    private String buildPrompt(String developer, List<Task> tasks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Developer: ").append(developer).append('\n');
        sb.append("Tasks:\n");
        for (Task t : tasks) {
            sb.append("- ").append(t.name())
                    .append(" | Sprint: ").append(safe(t.sprint()))
                    .append(" | Status: ").append(t.status().label())
                    .append(" | Start: ").append(t.startDate())
                    .append(" | End: ").append(t.endDate())
                    .append(" | Hours logged: ").append(trim(t.totalHours()))
                    .append('\n');
        }
        sb.append("\nWrite the status narrative now.");
        return sb.toString();
    }

    static String fallback(String developer, List<Task> tasks) {
        long done = tasks.stream().filter(t -> t.status() == TaskStatus.DONE).count();
        long inProgress = tasks.stream().filter(t -> t.status() == TaskStatus.IN_PROGRESS).count();
        long blocked = tasks.stream().filter(t -> t.status() == TaskStatus.BLOCKED).count();
        long todo = tasks.stream().filter(t -> t.status() == TaskStatus.TO_DO).count();
        double hours = tasks.stream().mapToDouble(Task::totalHours).sum();

        StringBuilder sb = new StringBuilder();
        sb.append("Status summary for ").append(developer).append(" (auto-generated)\n");
        sb.append(String.format(Locale.ENGLISH, "Progress: %d of %d tasks done (%.0f%%).%n",
                done, tasks.size(), tasks.isEmpty() ? 0 : done * 100.0 / tasks.size()));
        sb.append("Completed: ").append(done).append(" | In progress: ").append(inProgress)
                .append(" | To do: ").append(todo).append(" | Blocked: ").append(blocked).append('\n');
        sb.append("Total hours logged: ").append(trim(hours)).append('\n');
        if (blocked > 0) {
            sb.append("Attention: ").append(blocked).append(" blocked task(s) need follow-up.\n");
        }
        return sb.toString().trim();
    }

    private static String safe(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private static String trim(double value) {
        return String.format(Locale.ENGLISH, "%.1f", value);
    }
}
