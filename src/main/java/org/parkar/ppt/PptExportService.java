package org.parkar.ppt;

import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.parkar.chart.BurndownChartRenderer;
import org.parkar.domain.Task;
import org.parkar.service.BurndownSeries;
import org.parkar.service.DeveloperReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates a per-developer PowerPoint deck. Two modes are supported:
 * <ul>
 *   <li><b>Built-in layout</b> — slides are built programmatically (default).</li>
 *   <li><b>Template</b> — a reference {@code .pptx} is used; its slide layouts are
 *       cloned once per developer and its {@code {{token}}} placeholders are filled.</li>
 * </ul>
 *
 * <p>Template contract: one slide containing {@code {{title}}}, one slide containing
 * {@code {{summary}}} (developer summary; a table on that slide receives the tasks),
 * and one slide containing {@code {{chart}}}. The title slide should be the first
 * slide; the summary and chart slides are cloned per developer.</p>
 */
public class PptExportService {

    private static final Logger log = LoggerFactory.getLogger(PptExportService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final int MAX_TABLE_ROWS = 14;

    private final BurndownChartRenderer chartRenderer;

    public PptExportService(BurndownChartRenderer chartRenderer) {
        this.chartRenderer = chartRenderer;
    }

    public Path export(Path output, String deckTitle, List<DeveloperReport> reports) throws IOException {
        return export(output, deckTitle, reports, null);
    }

    /**
     * Exports the deck, using the template when a readable path is supplied and it
     * is structurally valid; otherwise falls back to the built-in layout.
     */
    public Path export(Path output, String deckTitle, List<DeveloperReport> reports, String templatePath)
            throws IOException {
        if (templatePath != null && !templatePath.isBlank() && Files.exists(Path.of(templatePath))) {
            try {
                return exportFromTemplate(output, deckTitle, reports, Path.of(templatePath));
            } catch (Exception e) {
                log.warn("Template export failed ({}); using built-in layout instead", e.getMessage());
            }
        }
        return exportProgrammatic(output, deckTitle, reports);
    }

    // ------------------------------------------------------------ built-in layout

    private Path exportProgrammatic(Path output, String deckTitle, List<DeveloperReport> reports) throws IOException {
        log.info("Building deck with built-in layout: {} report(s)", reports.size());
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            ppt.setPageSize(new Dimension(960, 540));
            addTitleSlide(ppt, deckTitle);
            for (DeveloperReport report : reports) {
                log.debug("  built-in layout: adding developer slide for '{}'", report.developer());
                addDeveloperSlide(ppt, report);
                if (report.burndown() != null) {
                    log.debug("  built-in layout: adding burndown slide for '{}'", report.developer());
                    addBurndownSlide(ppt, report);
                }
            }
            write(ppt, output);
        }
        log.info("Exported PowerPoint (built-in layout) with {} developer report(s) to {}",
                reports.size(), output);
        return output;
    }

    // ---------------------------------------------------------------- template

    private Path exportFromTemplate(Path output, String deckTitle, List<DeveloperReport> reports, Path template)
            throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(Files.newInputStream(template))) {
            List<XSLFSlide> slides = ppt.getSlides();
            log.info("Loaded PPT template {} ({} slide(s))", template, slides.size());
            XSLFSlide titleSlide = null;
            XSLFSlide summaryTemplate = null;
            XSLFSlide chartTemplate = null;
            int summaryIndex = -1;
            int chartIndex = -1;

            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide s = slides.get(i);
                String text = collectText(s);
                if (titleSlide == null && text.contains("{{title}}")) {
                    titleSlide = s;
                } else if (chartTemplate == null && text.contains("{{chart}}")) {
                    chartTemplate = s;
                    chartIndex = i;
                } else if (summaryTemplate == null && (text.contains("{{summary}}") || findTable(s) != null)) {
                    summaryTemplate = s;
                    summaryIndex = i;
                }
            }
            if (titleSlide == null || summaryTemplate == null || chartTemplate == null) {
                throw new IOException("template must contain {{title}}, {{summary}} and {{chart}} slides");
            }

            replaceTokens(titleSlide, Map.of("{{title}}", deckTitle, "{{date}}", LocalDate.now().format(DATE)));

            for (DeveloperReport report : reports) {
                log.debug("Template: cloning summary + chart slides for '{}'", report.developer());
                XSLFSlide summarySlide = clone(ppt, summaryTemplate);
                replaceTokens(summarySlide, Map.of(
                        "{{developer}}", report.developer(),
                        "{{summary}}", report.summary()));
                fillTaskTable(summarySlide, report.tasks());

                XSLFSlide chartSlide = clone(ppt, chartTemplate);
                Rectangle2D anchor = findChartAnchor(chartSlide);
                replaceTokens(chartSlide, Map.of("{{developer}}", report.developer(), "{{chart}}", ""));
                if (report.burndown() != null) {
                    insertChart(ppt, chartSlide, report.burndown(), anchor);
                }
            }

            // Remove the original summary/chart template slides; keep the title,
            // leaving the title (first) followed by the per-developer generated slides.
            for (int i = slides.size() - 1; i >= 0; i--) {
                if (i == summaryIndex || i == chartIndex) {
                    ppt.removeSlide(i);
                }
            }
            write(ppt, output);
        }
        log.info("Exported PowerPoint from template {} with {} developer report(s) to {}",
                template.getFileName(), reports.size(), output);
        return output;
    }

    private static XSLFSlide clone(XMLSlideShow ppt, XSLFSlide source) {
        XSLFSlide target = ppt.createSlide(source.getSlideLayout());
        target.getXmlObject().set(source.getXmlObject().copy());
        return target;
    }

    private String collectText(XSLFSlide slide) {
        StringBuilder sb = new StringBuilder();
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape textShape) {
                sb.append(textShape.getText()).append('\n');
            }
        }
        return sb.toString();
    }

    private void replaceTokens(XSLFSlide slide, Map<String, String> tokens) {
        for (XSLFShape shape : slide.getShapes()) {
            if (!(shape instanceof XSLFTextShape textShape)) {
                continue;
            }
            for (XSLFTextParagraph paragraph : textShape.getTextParagraphs()) {
                for (XSLFTextRun run : paragraph.getTextRuns()) {
                    String text = run.getRawText();
                    if (text == null) {
                        continue;
                    }
                    String replaced = text;
                    for (Map.Entry<String, String> token : tokens.entrySet()) {
                        replaced = replaced.replace(token.getKey(), token.getValue());
                    }
                    if (!replaced.equals(text)) {
                        run.setText(replaced);
                    }
                }
            }
        }
    }

    private Rectangle2D findChartAnchor(XSLFSlide slide) {
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape textShape && textShape.getText().contains("{{chart}}")) {
                Rectangle2D anchor = shape.getAnchor();
                if (anchor != null) {
                    return anchor;
                }
            }
        }
        return new Rectangle2D.Double(40, 90, 880, 420);
    }

    private void insertChart(XMLSlideShow ppt, XSLFSlide slide, BurndownSeries series, Rectangle2D anchor) {
        log.debug("Rendering and inserting burndown chart at {}", anchor);
        try {
            BufferedImage image = chartRenderer.render(series, (int) Math.max(320, anchor.getWidth()),
                    (int) Math.max(200, anchor.getHeight()));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bos);
            XSLFPictureData pictureData = ppt.addPicture(bos.toByteArray(), PictureData.PictureType.PNG);
            XSLFPictureShape picture = slide.createPicture(pictureData);
            picture.setAnchor(anchor);
        } catch (IOException e) {
            log.warn("Could not render burndown image: {}", e.getMessage());
        }
    }

    private void fillTaskTable(XSLFSlide slide, List<Task> tasks) {
        XSLFTable table = findTable(slide);
        if (table == null) {
            return;
        }
        int shown = Math.min(tasks.size(), MAX_TABLE_ROWS);
        for (int r = 0; r < shown; r++) {
            Task t = tasks.get(r);
            XSLFTableRow row = table.addRow();
            row.setHeight(24);
            addCell(row, t.name());
            addCell(row, t.sprint());
            addCell(row, t.status().label());
            addCell(row, t.startDate() == null ? "-" : t.startDate().format(DATE));
            addCell(row, t.endDate() == null ? "-" : t.endDate().format(DATE));
            addCell(row, String.format(Locale.ENGLISH, "%.1f", t.totalHours()));
        }
        if (tasks.size() > shown) {
            XSLFTableRow row = table.addRow();
            row.setHeight(24);
            addCell(row, "... " + (tasks.size() - shown) + " more task(s)");
            for (int i = 1; i < 6; i++) {
                addCell(row, "");
            }
        }
    }

    private static XSLFTable findTable(XSLFSlide slide) {
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTable table) {
                return table;
            }
        }
        return null;
    }

    private void write(XMLSlideShow ppt, Path output) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(output)) {
            ppt.write(out);
        }
    }

    // ------------------------------------------------------- built-in slide builders

    private void addTitleSlide(XMLSlideShow ppt, String deckTitle) {
        XSLFSlide slide = ppt.createSlide();
        addText(slide, deckTitle, 60, 180, 840, 90, 40, true, new Color(0x1E, 0x88, 0xE5));
        addText(slide, "Generated on " + LocalDate.now().format(DATE), 60, 280, 840, 40, 18, false, Color.DARK_GRAY);
    }

    private void addDeveloperSlide(XMLSlideShow ppt, DeveloperReport report) {
        XSLFSlide slide = ppt.createSlide();
        addText(slide, report.developer(), 40, 20, 880, 50, 28, true, new Color(0x1E, 0x88, 0xE5));

        addText(slide, "Task Summary", 40, 75, 400, 30, 16, true, Color.DARK_GRAY);
        XSLFTable table = buildTaskTable(slide, report.tasks());
        table.setAnchor(new Rectangle2D.Double(40, 105, 880, 200));

        addText(slide, "Narrative", 40, 320, 880, 30, 16, true, Color.DARK_GRAY);
        addText(slide, report.summary(), 40, 350, 880, 165, 12, false, Color.BLACK);
    }

    private void addBurndownSlide(XMLSlideShow ppt, DeveloperReport report) {
        BurndownSeries series = report.burndown();
        XSLFSlide slide = ppt.createSlide();
        addText(slide, report.developer() + " - Burndown", 40, 20, 880, 50, 26, true, new Color(0x1E, 0x88, 0xE5));

        try {
            BufferedImage image = chartRenderer.render(series, 880, 440);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bos);
            XSLFPictureData pictureData = ppt.addPicture(bos.toByteArray(), PictureData.PictureType.PNG);
            XSLFPictureShape picture = slide.createPicture(pictureData);
            picture.setAnchor(new Rectangle2D.Double(40, 80, 880, 440));
        } catch (IOException e) {
            log.warn("Could not render burndown image for {}: {}", report.developer(), e.getMessage());
            addText(slide, "Burndown chart could not be generated.", 40, 120, 880, 40, 16, false, Color.RED);
        }
    }

    private XSLFTable buildTaskTable(XSLFSlide slide, List<Task> tasks) {
        XSLFTable table = slide.createTable();
        String[] headers = {"Task", "Sprint", "Status", "Start", "End", "Hours"};
        double[] widths = {300, 130, 120, 120, 120, 90};

        XSLFTableRow header = table.addRow();
        header.setHeight(28);
        for (int i = 0; i < headers.length; i++) {
            XSLFTableCell cell = header.addCell();
            cell.setText(headers[i]);
            boldCell(cell);
            table.setColumnWidth(i, widths[i]);
        }

        int shown = Math.min(tasks.size(), MAX_TABLE_ROWS);
        for (int r = 0; r < shown; r++) {
            Task t = tasks.get(r);
            XSLFTableRow row = table.addRow();
            row.setHeight(24);
            addCell(row, t.name());
            addCell(row, t.sprint());
            addCell(row, t.status().label());
            addCell(row, t.startDate() == null ? "-" : t.startDate().format(DATE));
            addCell(row, t.endDate() == null ? "-" : t.endDate().format(DATE));
            addCell(row, String.format(Locale.ENGLISH, "%.1f", t.totalHours()));
        }
        if (tasks.size() > shown) {
            XSLFTableRow row = table.addRow();
            row.setHeight(24);
            addCell(row, "... " + (tasks.size() - shown) + " more task(s)");
            for (int i = 1; i < headers.length; i++) {
                addCell(row, "");
            }
        }
        return table;
    }

    private void addCell(XSLFTableRow row, String value) {
        XSLFTableCell cell = row.addCell();
        cell.setText(value == null ? "-" : value);
        for (XSLFTextParagraph p : cell.getTextParagraphs()) {
            for (XSLFTextRun run : p.getTextRuns()) {
                run.setFontSize(10.0);
            }
        }
    }

    private void boldCell(XSLFTableCell cell) {
        for (XSLFTextParagraph p : cell.getTextParagraphs()) {
            for (XSLFTextRun run : p.getTextRuns()) {
                run.setFontSize(11.0);
                run.setBold(true);
            }
        }
    }

    private void addText(XSLFSlide slide, String text, double x, double y, double w, double h,
                         double fontSize, boolean bold, Color color) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle2D.Double(x, y, w, h));
        box.clearText();
        XSLFTextParagraph paragraph = box.addNewTextParagraph();
        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(text == null ? "" : text);
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setFontColor(color);
    }
}
