package org.opendiplom.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** Registration numbers as the operator writes them. */
final class NumbersTest {
    @Test
    void cannotMissNumberOfRangeWithGap() {
        assertEquals(
            Arrays.asList("10001", "10002", "10003", "10010", "10012"),
            Numbers.parse(" 10001 – 10003, 10010;10012 "),
            "A range with a gap was not read as written"
        );
    }

    @Test
    void cannotLoseLeadingZeros() {
        assertEquals(Arrays.asList("0098", "0099", "0100"), Numbers.parse("0098-0100"), "Leading zeros were lost");
    }

    @Test
    void cannotAcceptNumberTwice() {
        assertThrows(IllegalArgumentException.class, () -> Numbers.parse("10001-10003, 10002"),
            "A number written twice would go to two graduates");
    }

    @Test
    void cannotAcceptRangeBackwards() {
        assertThrows(IllegalArgumentException.class, () -> Numbers.parse("10007-10001"),
            "A range written backwards was taken");
    }

    @Test
    void cannotInventNumbers() {
        assertEquals(Collections.emptyList(), Numbers.parse("  "), "Numbers appeared from an empty line");
    }
}
