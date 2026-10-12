package org.parkar.service;

import org.parkar.chart.BurndownChartRenderer;
import org.parkar.domain.DateRange;
import org.parkar.domain.Task;
import org.parkar.llm.LlmClient;
import org.parkar.llm.LlmSummaryService;
import org.parkar.ppt.PptExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** High level application service tying together import, burndown, LLM and export. */
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final BurndownService burndownService = new BurndownService();
    private final PptExportService pptExportService = new PptExportService(new BurndownChartRenderer());
    private final LlmSummaryService summaryService;
    private String pptTemplatePath;

    public ProjectService(LlmClient llmClient) {
        this(llmClient, null);
    }

    public ProjectService(LlmClient llmClient, String pptTemplatePath) {
        this.summaryService = new LlmSummaryService(llmClient);
        this.pptTemplatePath = pptTemplatePath;
        log.info("ProjectService created (llm={}, template={})", llmClient.provider(),
                pptTemplatePath == null || pptTemplatePath.isBlank() ? "(built-in)" : pptTemplatePath);
    }

    public void setPptTemplatePath(String pptTemplatePath) {
        this.pptTemplatePath = pptTemplatePath;
    }

    public String pptTemplatePath() {
        return pptTemplatePath;
    }

    public List<String> developers(List<Task> tasks) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Task t : tasks) {
            names.add(t.developer());
        }
        List<String> result = new ArrayList<>(names);
        log.debug("Resolved {} distinct developer(s) from {} task(s)", result.size(), tasks.size());
        return result;
    }

    public List<Task> tasksFor(List<Task> tasks, String developer) {
        return tasks.stream()
                .filter(t -> t.developer().equalsIgnoreCase(developer))
                .toList();
    }

    public BurndownSeries burndown(String owner, List<Task> tasks, DateRange range) {
        return burndownService.compute(owner, tasks, range);
    }

    public DeveloperReport buildReport(String developer, List<Task> tasks, DateRange range, boolean withSummary) {
        log.debug("Building report for '{}' ({} task(s), summary={})", developer, tasks.size(), withSummary);
        BurndownSeries series = tasks.isEmpty() ? null : burndownService.compute(developer, tasks, range);
        String summary = withSummary ? summaryService.summarize(developer, tasks) : "";
        return new DeveloperReport(developer, tasks, summary, series);
    }

    public Path export(Path output, String deckTitle, List<DeveloperReport> reports) throws IOException {
        log.info("Exporting deck '{}' to {} ({} report(s), template={})", deckTitle, output,
                reports.size(), pptTemplatePath == null ? "(built-in)" : pptTemplatePath);
        Path written = pptExportService.export(output, deckTitle, reports, pptTemplatePath);
        log.debug("Deck written to {}", written);
        return written;
    }
}
