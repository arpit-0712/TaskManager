package org.parkar.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Lenient date parsing for both header text and Excel cell values. */
public final class DateParser {

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy/M/d", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH));

    private DateParser() {
    }

    public static Optional<LocalDate> tryParse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String v = text.trim();
        if (v.isEmpty()) {
            return Optional.empty();
        }
        // Extract a leading date token from values like "01-10-2024 (Mon)".
        String candidate = v.split("\\(", 2)[0].trim();
        for (DateTimeFormatter f : FORMATTERS) {
            try {
                return Optional.of(LocalDate.parse(candidate, f));
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return Optional.empty();
    }

    /** Reads a date from a cell, supporting real Excel dates, serials and text. */
    public static LocalDate fromCell(Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate();
                }
                double serial = cell.getNumericCellValue();
                if (serial > 0) {
                    return DateUtil.getJavaDate(serial).toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate();
                }
                return null;
            }
            case STRING -> {
                return tryParse(cell.getStringCellValue()).orElse(null);
            }
            case FORMULA -> {
                try {
                    return cell.getLocalDateTimeCellValue().toLocalDate();
                } catch (RuntimeException e) {
                    return tryParse(cell.toString()).orElse(null);
                }
            }
            default -> {
                return null;
            }
        }
    }
}
