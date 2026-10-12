package org.parkar.excel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.parkar.domain.Task;
import org.parkar.domain.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelImportServiceTest {

    private Path writeWorkbook(Path dir) throws Exception {
        Path file = dir.resolve("sample.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Tasks");
            Row header = sheet.createRow(0);
            String[] cols = {"Developer", "Task", "Sprint", "Status", "Assigned Date", "Start Date", "End Date",
                    "01-10-2024", "02-10-2024", "03-10-2024"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Alice");
            r1.createCell(1).setCellValue("Login page");
            r1.createCell(2).setCellValue("Sprint 1");
            r1.createCell(3).setCellValue("Done");
            r1.createCell(4).setCellValue("30-09-2024");
            r1.createCell(5).setCellValue("01-10-2024");
            r1.createCell(6).setCellValue("02-10-2024");
            r1.createCell(7).setCellValue(4);
            r1.createCell(8).setCellValue(3);

            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("Alice");
            r2.createCell(1).setCellValue("Dashboard");
            r2.createCell(2).setCellValue("Sprint 1");
            r2.createCell(3).setCellValue("In-progress");
            r2.createCell(4).setCellValue("01-10-2024");
            r2.createCell(5).setCellValue("02-10-2024");
            r2.createCell(6).setCellValue("05-10-2024");
            r2.createCell(9).setCellValue(2);

            try (OutputStream out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }
        return file;
    }

    @Test
    void importsTasksAndTimeEntries(@TempDir Path dir) throws Exception {
        Path file = writeWorkbook(dir);
        ImportResult result = new ExcelImportService().importTasks(file);

        assertEquals(2, result.tasks().size());
        assertEquals(3, result.timeColumns().size());

        Task login = result.tasks().get(0);
        assertEquals("Alice", login.developer());
        assertEquals("Login page", login.name());
        assertEquals(TaskStatus.DONE, login.status());
        assertEquals(LocalDate.of(2024, 10, 1), login.startDate());
        assertEquals(2, login.timeSpent().size());
        assertEquals(7.0, login.totalHours());

        Task dash = result.tasks().get(1);
        assertEquals(TaskStatus.IN_PROGRESS, dash.status());
        assertEquals(1, dash.timeSpent().size());
        assertEquals(LocalDate.of(2024, 10, 3), dash.timeSpent().get(0).date());
        assertTrue(result.warnings().isEmpty());
    }
}
