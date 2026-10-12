package org.parkar.ppt;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.parkar.chart.BurndownChartRenderer;
import org.parkar.domain.DateRange;
import org.parkar.domain.Task;
import org.parkar.domain.TaskStatus;
import org.parkar.domain.TimeEntry;
import org.parkar.service.BurndownService;
import org.parkar.service.DeveloperReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptTemplateExportTest {

    private static void text(XSLFSlide slide, String value, double x, double y) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle2D.Double(x, y, 500, 60));
        box.clearText();
        XSLFTextParagraph p = box.addNewTextParagraph();
        XSLFTextRun run = p.addNewTextRun();
        run.setText(value);
    }

    private Path buildTemplate(Path dir) throws Exception {
        Path file = dir.resolve("template.pptx");
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            ppt.setPageSize(new Dimension(960, 540));

            XSLFSlide title = ppt.createSlide();
            text(title, "{{title}}", 40, 40);
            text(title, "{{date}}", 40, 120);

            XSLFSlide summary = ppt.createSlide();
            text(summary, "{{developer}}", 40, 20);
            text(summary, "{{summary}}", 40, 320);
            XSLFTable table = summary.createTable();
            XSLFTableRow header = table.addRow();
            for (String h : new String[]{"Task", "Sprint", "Status", "Start", "End", "Hours"}) {
                XSLFTableCell cell = header.addCell();
                cell.setText(h);
            }
            table.setAnchor(new Rectangle2D.Double(40, 100, 880, 180));

            XSLFSlide chart = ppt.createSlide();
            text(chart, "{{developer}} - Burndown", 40, 20);
            text(chart, "{{chart}}", 40, 100);

            try (OutputStream out = Files.newOutputStream(file)) {
                ppt.write(out);
            }
        }
        return file;
    }

    private DeveloperReport report(String dev, String task, LocalDate end) {
        List<Task> tasks = List.of(new Task(dev, task, "S1", TaskStatus.DONE, null,
                end.minusDays(3), end, List.of(new TimeEntry(end.minusDays(1), 5))));
        DateRange range = new DateRange(end.minusDays(4), end);
        var burndown = new BurndownService().compute(dev, tasks, range);
        return new DeveloperReport(dev, tasks, "Narrative for " + dev, burndown);
    }

    @Test
    void exportsFromTemplate(@TempDir Path dir) throws Exception {
        Path template = buildTemplate(dir);
        Path out = dir.resolve("deck.pptx");

        List<DeveloperReport> reports = List.of(
                report("Alice", "Login page", LocalDate.of(2024, 10, 5)),
                report("Bob", "API auth", LocalDate.of(2024, 10, 6)));

        new PptExportService(new BurndownChartRenderer()).export(out, "Sprint Report", reports,
                template.toString());

        assertTrue(Files.exists(out));
        try (XMLSlideShow result = new XMLSlideShow(Files.newInputStream(out))) {
            // 1 title + 2 developers * (summary + chart)
            assertEquals(5, result.getSlides().size());
            String allText = allText(result);
            assertFalse(allText.contains("{{"), "placeholders should be replaced: " + allText);
            assertTrue(allText.contains("Alice"));
            assertTrue(allText.contains("Bob"));
            assertTrue(allText.contains("Login page"));
            assertTrue(allText.contains("Sprint Report"));
        }
    }

    private String allText(XMLSlideShow ppt) {
        StringBuilder sb = new StringBuilder();
        ppt.getSlides().forEach(s -> s.getShapes().forEach(shape -> {
            if (shape instanceof XSLFTable table) {
                table.getRows().forEach(row -> row.getCells().forEach(cell -> sb.append(cell.getText()).append('|')));
                sb.append('\n');
            } else if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape t) {
                sb.append(t.getText()).append('\n');
            }
        }));
        return sb.toString();
    }
}
