package org.jenkins.ui.icon;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class IconSetWithAmpersandTest {

    /**
     * Culprit: https://github.com/Kevin-CB/jenkins/blob/49c4cda2d41719d66a4eb4d6f9c31ba8298f2fbf/core/src/main/java/org/jenkins/ui/icon/IconSet.java#L94
     * If the tooltip contains an ampersand symbol (&amp;), it won't be removed.
     */
    @Test
    @Disabled("TODO see JENKINS-68805")
    void getSymbol_notSettingTooltipDoesntAddTooltipAttribute_evenWithAmpersand() {
        String symbolWithTooltip = IconSet.getSymbol("download", "Title", "With&Ampersand", "class1 class2", "", "id");
        String symbolWithoutTooltip = IconSet.getSymbol("download", "Title", "", "class1 class2", "", "id");

        assertThat(symbolWithoutTooltip, not(containsString("tooltip")));
    }
}
