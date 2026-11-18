package model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskEntryTest {

    @Test
    void rejectsEmptyText() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TaskEntry("", TaskPriority.NORMAL)
        );
    }

    @Test
    void rejectsWhitespaceOnlyText() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TaskEntry("   ", TaskPriority.NORMAL)
        );
    }

    @Test
    void rejectsTabsAndLineBreaksOnlyText() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TaskEntry("\t\n", TaskPriority.NORMAL)
        );
    }

    @Test
    void acceptsTextContainingNonWhitespaceCharacters() {
        assertDoesNotThrow(
                () -> new TaskEntry("  Read a book  ", TaskPriority.NORMAL)
        );
    }
}

