package org.jenkins.ui.icon;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;

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

    @Test
    void getSymbol() {
        String symbol = IconSet.getSymbol("download", "Title", "Tooltip", "", "class1 class2", "", "id");

        assertThat(symbol, containsString("<span class=\"jenkins-visually-hidden\">Title</span>"));
        assertThat(symbol, containsString("tooltip=\"Tooltip\""));
        assertThat(symbol, containsString("class=\"class1 class2\""));
        assertThat(symbol, containsString("id=\"id\""));
    }

    @Test
    void getSymbol_cachedSymbolDoesntReturnAttributes() {
        IconSet.getSymbol("download", "Title", "Tooltip", "", "class1 class2", "", "id");
        String symbol = IconSet.getSymbol("download", "", "", "", "", "", "");

        assertThat(symbol, not(containsString("<span class=\"jenkins-visually-hidden\">Title</span>")));
        assertThat(symbol, not(containsString("tooltip=\"Tooltip\"")));
        assertThat(symbol, not(containsString("class=\"class1 class2\"")));
        assertThat(symbol, not(containsString("id=\"id\"")));

    }

    @Test
    void getSymbol_cachedSymbolAllowsSettingAllAttributes() {
        IconSet.getSymbol("download", "Title", "Tooltip", "", "class1 class2", "", "id");
        String symbol = IconSet.getSymbol("download", "Title2", "Tooltip2", "", "class3 class4", "", "id2");

        assertThat(symbol, not(containsString("<span class=\"jenkins-visually-hidden\">Title</span>")));
        assertThat(symbol, not(containsString("tooltip=\"Tooltip\"")));
        assertThat(symbol, not(containsString("class=\"class1 class2\"")));
        assertThat(symbol, not(containsString("id=\"id\"")));
        assertThat(symbol, containsString("<span class=\"jenkins-visually-hidden\">Title2</span>"));
        assertThat(symbol, containsString("tooltip=\"Tooltip2\""));
        assertThat(symbol, containsString("class=\"class3 class4\""));
        assertThat(symbol, containsString("id=\"id2\""));
    }

    /**
     * YUI tooltips require that the attribute not be set, otherwise a white rectangle will show on hover
     * TODO: This might be able to be removed when we move away from YUI tooltips to a better solution
     */
    @Test
    void getSymbol_notSettingTooltipDoesntAddTooltipAttribute() {
        String symbol = IconSet.getSymbol("download", "Title", "", "", "class1 class2", "", "id");

        assertThat(symbol, not(containsString("tooltip")));
    }

}
