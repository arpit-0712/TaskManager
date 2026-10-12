package org.parkar.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.parkar.domain.DateRange;
import org.parkar.domain.Task;
import org.parkar.domain.TaskStatus;
import org.parkar.domain.TimeEntry;
import org.parkar.util.DateParser;
import org.parkar.util.ProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imports the source Excel workbook into a list of {@link Task}s.
 *
 * <p>Expected columns (header matched case/space-insensitively):
 * {@code Developer, Task, Sprint, Status, Assigned Date, Start Date, End Date}
 * followed by any number of day-wise time-spent columns whose headers are dates.</p>
 */
public class ExcelImportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportService.class);

    private final DataFormatter formatter = new DataFormatter(Locale.ENGLISH);

    public ImportResult importTasks(Path file) throws IOException {
        return importTasks(file, 0, null);
    }

    public ImportResult importTasks(Path file, int sheetIndex) throws IOException {
        return importTasks(file, sheetIndex, null);
    }

    /**
     * Imports the workbook and reports coarse progress (0..1) through the supplied
     * {@link ProgressListener} as rows are parsed. The listener may be {@code null}.
     */
    public ImportResult importTasks(Path file, int sheetIndex, ProgressListener progress) throws IOException {
        List<Task> tasks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> dateColumns = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IOException("Workbook has no sheets: " + file);
            }
            Sheet sheet = workbook.getSheetAt(Math.min(sheetIndex, workbook.getNumberOfSheets() - 1));
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                throw new IOException("Sheet '" + sheet.getSheetName() + "' has no header row");
            }

            Map<String, Integer> columns = mapHeaders(header);
            Integer devCol = columns.get("developer");
            Integer taskCol = columns.get("task");
            log.debug("Sheet '{}': mapped {} header column(s), rows {}..{}",
                    sheet.getSheetName(), columns.size(), header.getRowNum() + 1, sheet.getLastRowNum());
            if (devCol == null || taskCol == null) {
                log.error("Required columns 'Developer' and 'Task' not found in sheet '{}'", sheet.getSheetName());
                throw new IOException("Required columns 'Developer' and 'Task' were not found. Headers: "
                        + columns.keySet());
            }

            Map<Integer, LocalDate> timeColumns = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : columns.entrySet()) {
                if (isKnownColumn(e.getKey())) {
                    continue;
                }
                Cell c = header.getCell(e.getValue());
                LocalDate d = DateParser.fromCell(c);
                if (d != null) {
                    timeColumns.put(e.getValue(), d);
                }
            }
            timeColumns.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> dateColumns.add(e.getValue().toString()));
            log.debug("Sheet '{}': detected {} day-wise time column(s): {}",
                    sheet.getSheetName(), timeColumns.size(), String.join(", ", dateColumns));

            int firstDataRow = header.getRowNum() + 1;
            int lastDataRow = sheet.getLastRowNum();
            int totalRows = Math.max(1, lastDataRow - firstDataRow + 1);
            if (progress != null) {
                progress.onProgress(0.1, "Reading rows...");
            }
            for (int r = firstDataRow; r <= lastDataRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String developer = text(row.getCell(devCol), evaluator);
                String taskName = text(row.getCell(taskCol), evaluator);
                if (isBlank(developer) && isBlank(taskName)) {
                    continue;
                }
                if (isBlank(developer) || isBlank(taskName)) {
                    warnings.add("Row " + (r + 1) + ": missing Developer or Task; row skipped");
                    continue;
                }

                TaskStatus status = parseStatus(text(row.getCell(columns.get("status")), evaluator), r, warnings);
                LocalDate assigned = DateParser.fromCell(cell(row, columns.get("assigneddate")));
                LocalDate start = DateParser.fromCell(cell(row, columns.get("startdate")));
                LocalDate end = DateParser.fromCell(cell(row, columns.get("enddate")));

                List<TimeEntry> entries = new ArrayList<>();
                for (Map.Entry<Integer, LocalDate> tc : timeColumns.entrySet()) {
                    double hours = number(row.getCell(tc.getKey()), evaluator);
                    if (hours > 0) {
                        entries.add(new TimeEntry(tc.getValue(), hours));
                    }
                }

                tasks.add(new Task(developer.trim(), taskName.trim(),
                        trimOrEmpty(text(row.getCell(columns.get("sprint")), evaluator)),
                        status, assigned, start, end, entries));

                if (progress != null && (r - firstDataRow) % 25 == 0) {
                    progress.onProgress(0.1 + 0.85 * (r - firstDataRow + 1) / (double) totalRows, "Reading rows...");
                }
            }
        }

        if (progress != null) {
            progress.onProgress(1.0, "Import complete");
        }
        log.info("Imported {} tasks across {} day-wise columns from {}", tasks.size(), dateColumns.size(), file);
        return new ImportResult(tasks, warnings, dateColumns);
    }

    private static Cell cell(Row row, Integer idx) {
        return idx == null ? null : row.getCell(idx);
    }

    private Map<String, Integer> mapHeaders(Row header) {
        Map<String, Integer> map = new HashMap<>();
        for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            if (cell == null) {
                continue;
            }
            String key = normalize(formatter.formatCellValue(cell));
            if (!key.isEmpty()) {
                map.putIfAbsent(key, c);
            }
        }
        return map;
    }

    private static boolean isKnownColumn(String normalized) {
        return normalized.equals("developer") || normalized.equals("task") || normalized.equals("sprint")
                || normalized.equals("status") || normalized.equals("assigneddate")
                || normalized.equals("startdate") || normalized.equals("enddate");
    }

    private static String normalize(String header) {
        if (header == null) {
            return "";
        }
        return header.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String text(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        try {
            return formatter.formatCellValue(cell, evaluator).trim();
        } catch (RuntimeException e) {
            return formatter.formatCellValue(cell).trim();
        }
    }

    private double number(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return 0;
        }
        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return cell.getNumericCellValue();
                case FORMULA:
                    try {
                        return cell.getNumericCellValue();
                    } catch (RuntimeException e) {
                        return parseNumber(formatter.formatCellValue(cell, evaluator));
                    }
                default:
                    return parseNumber(formatter.formatCellValue(cell, evaluator));
            }
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static double parseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replace("hrs", "").replace("hr", "").replace("h", "").trim();
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static TaskStatus parseStatus(String raw, int rowIndex, List<String> warnings) {
        TaskStatus status = TaskStatus.fromCell(raw);
        if (status == null) {
            warnings.add("Row " + (rowIndex + 1) + ": unrecognised status '" + raw + "' mapped to To Do");
            return TaskStatus.TO_DO;
        }
        return status;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
