package org.jenkins.ui.icon;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

public class IconSetTest {

    /**
     * Tests that at least a reasonable high number of icons is there
     */
    @Test
    void testIconSetSize() {
        assertThat("icons", IconSet.icons.size(), greaterThanOrEqualTo(371));
    }
}
