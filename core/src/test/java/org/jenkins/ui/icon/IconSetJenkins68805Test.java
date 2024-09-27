package org.jenkins.ui.icon;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

public class IconSetJenkins68805Test {

    /**
     * Culprit: https://github.com/jenkinsci/jenkins/blob/ab0bb8495819bd807a9211ac0df3f08e420226f1/core/src/main/java/org/jenkins/ui/icon/IconSet.java#L97=
     * If the tooltip contains an ampersand symbol (&amp;), it won't be removed.
     *
     * <p>This test relies on the behavior of the symbol cache, so must be in its own class.</p>
     */
    @Test
    @Issue("JENKINS-68805")
    void getSymbol_notSettingTooltipDoesntAddTooltipAttribute_evenWithAmpersand() {
        // cache a symbol with tooltip containing ampersand:
        String symbolWithTooltip = IconSet.getSymbol("download", "Title", "With&Ampersand", "", "class1 class2", "", "id");
        assertThat(symbolWithTooltip, containsString("tooltip"));
        assertThat(symbolWithTooltip, containsString("With&"));

        // Same symbol, no tooltip
        String symbolWithoutTooltip = IconSet.getSymbol("download", "Title", "", "", "class1 class2", "", "id");

        assertThat(symbolWithoutTooltip, not(containsString("tooltip")));
    }
}
