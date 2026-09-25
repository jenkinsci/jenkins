package org.jenkins.ui.symbol;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

class SymbolJenkinsTest {

    @RegisterExtension
    private final RealJenkinsExtension rjr = new RealJenkinsExtension()
            .addPlugins("plugins/design-library.jpi", "plugins/prism-api.jpi", "plugins/bootstrap5-api.jpi");

    @Test
    @Issue("JENKINS-73243")
    @DisplayName("When resolving a symbol where the tooltip contains '$' no error is thrown")
    void dollarInToolTipSucceeds() throws Throwable {
        rjr.then(SymbolJenkinsTest::_dollarInTooltipSucceeds);
    }

    private static void _dollarInTooltipSucceeds(JenkinsRule j) {
        String symbol = Symbol.get(new SymbolRequest.Builder()
                .withName("add")
                .withTooltip("$test")
                .build()
        );
        assertThat(symbol, containsString("tooltip=\"$test\""));
    }

    @Test
    @DisplayName("When resolving a symbol from a missing plugin, the placeholder is generated instead")
    void missingSymbolFromPluginDefaultsToPlaceholder() throws Throwable {
        rjr.then(SymbolJenkinsTest::_missingSymbolFromPluginDefaultsToPlaceholder);
    }

    private static void _missingSymbolFromPluginDefaultsToPlaceholder(JenkinsRule j) {
        String symbol = Symbol.get(new SymbolRequest.Builder()
                                           .withName("science")
                                           .withPluginName("missing-plugin")
                                           .build()
        );
        assertThat(symbol, containsString(Symbol.PLACEHOLDER_MATCHER));
    }

    @Test
    @DisplayName("Resolving a valid symbol from an installed plugin does not return the placeholder")
    void resolvingSymbolFromPlugin() throws Throwable {
        rjr.then(SymbolJenkinsTest::_resolvingSymbolFromPlugin);
    }

    private static void _resolvingSymbolFromPlugin(JenkinsRule j) {
        String symbol = Symbol.get(new SymbolRequest.Builder()
                                           .withName("app-bar")
                                           .withPluginName("design-library")
                                           .build()
        );
        assertThat(symbol, not(containsString(Symbol.PLACEHOLDER_MATCHER)));
    }
}
