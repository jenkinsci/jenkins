package jenkins.scm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.User;
import hudson.scm.ChangeLogSet;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

class RunWithSCMTest {

    /**
     * Regression test for corrupted UnmodifiableCollection
     * Set.copyOf() throws NPE
     * This was a regression introduced when migrating from Collections.unmodifiableSet to Set.copyOf.
     */
    @Test
    void getCulprits_shouldHandleCorruptedCollectionThatThrowsNPE() {
        RunWithSCM run = new RunWithSCM() {
            @Override
            public boolean shouldCalculateCulprits() {
                return false;
            }

            @Override
            public @NonNull List<ChangeLogSet<? extends ChangeLogSet.Entry>> getChangeSets() {
                return List.of();
            }

            @Override
            public Set<String> getCulpritIds() {
                // Simulates corrupted UnmodifiableCollection
                return new AbstractSet<>() {
                    @Override
                    public Iterator<String> iterator() {
                        return Collections.emptyIterator();
                    }

                    @Override
                    public int size() {
                        return 0;
                    }

                    @Override
                    public boolean isEmpty() {
                        throw new NullPointerException("c is null");
                    }
                };
            }
        };

        // Should not throw NPE, should return empty set
        Set<User> culprits = run.getCulprits();

        assertNotNull(culprits);
        assertEquals(0, culprits.size());
    }

    /**
     * Tests that null return from getCulpritIds() is handled gracefully.
     */
    @Test
    void getCulprits_shouldHandleNullCulpritIds() {
        RunWithSCM run = new RunWithSCM() {
            @Override
            public boolean shouldCalculateCulprits() {
                return false;
            }

            @Override
            public @NonNull List<ChangeLogSet<? extends ChangeLogSet.Entry>> getChangeSets() {
                return List.of();
            }

            @Override
            public Set<String> getCulpritIds() {
                return null;
            }
        };

        Set<User> culprits = run.getCulprits();

        assertNotNull(culprits);
        assertEquals(0, culprits.size());
    }
}
