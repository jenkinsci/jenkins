package hudson.util;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;

public class CompoundEnumerationTest {

    @Test
    public void smokes() {
        assertEquals(
                rangeClosed(1, 12),
                Collections.list(
                        new CompoundEnumeration<>(
                                Collections.enumeration(rangeClosed(1, 4)),
                                Collections.enumeration(rangeClosed(5, 8)),
                                Collections.enumeration(rangeClosed(9, 12)))));
    }

    @Test
    public void empty() {
        assertEquals(Collections.emptyList(), Collections.list(new CompoundEnumeration<>()));
        assertEquals(
                Collections.emptyList(),
                Collections.list(new CompoundEnumeration<>(Collections.emptyEnumeration())));
        assertEquals(
                Collections.emptyList(),
                Collections.list(
                        new CompoundEnumeration<>(
                                Collections.emptyEnumeration(), Collections.emptyEnumeration())));
        assertEquals(
                Collections.emptyList(),
                Collections.list(
                        new CompoundEnumeration<>(
                                Collections.emptyEnumeration(),
                                Collections.emptyEnumeration(),
                                Collections.emptyEnumeration())));
    }

    @Test
    public void gaps() {
        assertEquals(
                rangeClosed(1, 8),
                Collections.list(
                        new CompoundEnumeration<>(
                                Collections.emptyEnumeration(),
                                Collections.enumeration(rangeClosed(1, 4)),
                                Collections.enumeration(rangeClosed(5, 8)))));
        assertEquals(
                rangeClosed(1, 8),
                Collections.list(
                        new CompoundEnumeration<>(
                                Collections.enumeration(rangeClosed(1, 4)),
                                Collections.emptyEnumeration(),
                                Collections.enumeration(rangeClosed(5, 8)))));
        assertEquals(
                rangeClosed(1, 8),
                Collections.list(
                        new CompoundEnumeration<>(
                                Collections.enumeration(rangeClosed(1, 4)),
                                Collections.enumeration(rangeClosed(5, 8)),
                                Collections.emptyEnumeration())));
    }

    private static List<Integer> rangeClosed(int startInclusive, int endInclusive) {
        return IntStream.rangeClosed(startInclusive, endInclusive)
                .boxed()
                .collect(Collectors.toList());
    }
}
