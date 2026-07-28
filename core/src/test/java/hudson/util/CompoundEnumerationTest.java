package hudson.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CompoundEnumerationTest {

    @Test
    void smokes() {
        assertEquals(
                rangeClosed(1, 12),
                Collections.list(
                        new CompoundEnumeration<>(
                                Collections.enumeration(rangeClosed(1, 4)),
                                Collections.enumeration(rangeClosed(5, 8)),
                                Collections.enumeration(rangeClosed(9, 12)))));
    }

    @Test
    void empty() {
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
    void gaps() {
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

    /**
     * {@link Enumeration#nextElement} may be called without first consulting
     * {@link Enumeration#hasMoreElements}, so it has to skip exhausted enumerations too.
     * {@link hudson.PluginFirstClassLoader2#getResources} hits this for every resource the
     * plugin itself does not contain: the first enumeration in the chain is empty, and the
     * element the caller is after sits in the second one.
     */
    @Test
    void nextElementSkipsExhaustedEnumerationsWithoutHasMoreElements() {
        Enumeration<Integer> leadingEmpty =
                new CompoundEnumeration<>(
                        Collections.emptyEnumeration(), Collections.enumeration(rangeClosed(1, 2)));
        assertEquals(1, leadingEmpty.nextElement());
        assertEquals(2, leadingEmpty.nextElement());

        Enumeration<Integer> emptyInTheMiddle =
                new CompoundEnumeration<>(
                        Collections.enumeration(rangeClosed(1, 1)),
                        Collections.emptyEnumeration(),
                        Collections.enumeration(rangeClosed(2, 3)));
        assertEquals(rangeClosed(1, 3), drainWithNextElementOnly(emptyInTheMiddle, 3));
    }

    @Test
    void nextElementStillThrowsWhenTrulyExhausted() {
        Enumeration<Integer> allEmpty =
                new CompoundEnumeration<>(
                        Collections.emptyEnumeration(), Collections.emptyEnumeration());
        assertThrows(NoSuchElementException.class, allEmpty::nextElement);

        Enumeration<Integer> oneElement =
                new CompoundEnumeration<>(Collections.enumeration(rangeClosed(1, 1)));
        assertEquals(1, oneElement.nextElement());
        assertThrows(NoSuchElementException.class, oneElement::nextElement);
    }

    /** Reads exactly {@code count} elements without ever calling {@code hasMoreElements}. */
    private static List<Integer> drainWithNextElementOnly(Enumeration<Integer> e, int count) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(e.nextElement());
        }
        return result;
    }

    private static List<Integer> rangeClosed(int startInclusive, int endInclusive) {
        return IntStream.rangeClosed(startInclusive, endInclusive)
                .boxed()
                .collect(Collectors.toList());
    }
}
