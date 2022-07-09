package org.jenkins.ui.icon;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class IconSetWithAmpersandTest {

    /**
     * Culprit: https://github.com/jenkinsci/jenkins/blob/ab0bb8495819bd807a9211ac0df3f08e420226f1/core/src/main/java/org/jenkins/ui/icon/IconSet.java#L97=
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
