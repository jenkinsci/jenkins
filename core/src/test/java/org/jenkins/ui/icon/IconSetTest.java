package org.jenkins.ui.icon;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class IconSetTest {

    /**
     * Tests that at least a reasonable high number of icons is there
     */
    @Test
    void testIconSetSize() {
        final Map<String, Icon> coreIcons = IconSet.icons.getCoreIcons();
        assertThat("icons", coreIcons.size(), greaterThanOrEqualTo(350));
    }
}
