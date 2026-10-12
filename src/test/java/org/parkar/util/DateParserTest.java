package org.parkar.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateParserTest {

    @Test
    void parsesCommonFormats() {
        assertEquals(LocalDate.of(2024, 10, 1), DateParser.tryParse("2024-10-01").orElseThrow());
        assertEquals(LocalDate.of(2024, 10, 1), DateParser.tryParse("01-10-2024").orElseThrow());
        assertEquals(LocalDate.of(2024, 10, 1), DateParser.tryParse("1/10/2024").orElseThrow());
        assertEquals(LocalDate.of(2024, 10, 1), DateParser.tryParse("01 Oct 2024").orElseThrow());
        assertEquals(LocalDate.of(2024, 5, 3), DateParser.tryParse("3-May-2024").orElseThrow());
    }

    @Test
    void extractsTokenFromDecoratedHeader() {
        assertEquals(LocalDate.of(2024, 10, 1), DateParser.tryParse("01-10-2024 (Mon)").orElseThrow());
    }

    @Test
    void rejectsNonDates() {
        assertTrue(DateParser.tryParse("Total").isEmpty());
        assertTrue(DateParser.tryParse("Sprint").isEmpty());
        assertTrue(DateParser.tryParse("2024-10-01").isPresent());
    }
}
