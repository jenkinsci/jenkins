package jenkins.model.lazy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Run;
import hudson.util.RunList;
import hudson.util.RunListInterruptedException;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BuildReferenceMapAdapterTest {

    @Test
    void sizeStopsWhileSkippingUnloadableBuilds() {
        NavigableMap<Integer, BuildReference<Run>> references = new TreeMap<>();
        for (int number = 1; number <= 3; number++) {
            references.put(number, new BuildReference<>(Integer.toString(number)));
        }
        AtomicInteger resolved = new AtomicInteger();
        BuildReferenceMapAdapter.BuildReferenceResolver<Run> resolver = mock(BuildReferenceMapAdapter.BuildReferenceResolver.class);
        when(resolver.resolveBuildRef(any())).thenAnswer(invocation -> {
            if (resolved.incrementAndGet() == 1) {
                Thread.currentThread().interrupt();
            }
            return null;
        });
        BuildReferenceMapAdapter<Run> adapter = new BuildReferenceMapAdapter<>(references, resolver, mock(BuildReferenceMapAdapter.BuildTypeDescriptor.class));
        RunList<Run> runs = RunList.fromRuns(adapter.values());
        try {
            assertThrows(RunListInterruptedException.class, runs::size);
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, resolved.get());
            assertFalse(adapter.values().iterator().hasNext());
            assertEquals(4, resolved.get());
        } finally {
            Thread.interrupted();
        }
        assertEquals(0, runs.size());
        assertEquals(7, resolved.get());
    }
}
