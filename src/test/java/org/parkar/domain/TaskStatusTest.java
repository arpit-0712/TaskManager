package org.parkar.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaskStatusTest {

    @Test
    void mapsLabels() {
        assertEquals(TaskStatus.TO_DO, TaskStatus.fromCell("To Do"));
        assertEquals(TaskStatus.IN_PROGRESS, TaskStatus.fromCell("In-progress"));
        assertEquals(TaskStatus.IN_PROGRESS, TaskStatus.fromCell("in progress"));
        assertEquals(TaskStatus.DONE, TaskStatus.fromCell("Done"));
        assertEquals(TaskStatus.DONE, TaskStatus.fromCell("completed"));
        assertEquals(TaskStatus.BLOCKED, TaskStatus.fromCell("Blocked"));
    }

    @Test
    void unknownReturnsNull() {
        assertNull(TaskStatus.fromCell("something else"));
    }
}
