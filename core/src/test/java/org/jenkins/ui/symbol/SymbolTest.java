package org.jenkins.ui.symbol;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class SymbolTest {

    @Test
    @DisplayName("Get symbol should build the symbol with given attributes")
    void getSymbol() {
        String symbol = Symbol.get(new SymbolRequest.Builder()
                                           .withName("science")
                                           .withTitle("Title")
                                           .withTooltip("Tooltip")
                                           .withClasses("class1 class2")
                                           .withId("id")
                                           .build()
        );
        assertThat(symbol, containsString("<span class=\"jenkins-visually-hidden\">Title</span>"));
        assertThat(symbol, containsString("tooltip=\"Tooltip\""));
        assertThat(symbol, containsString("class=\"class1 class2\""));
        assertThat(symbol, containsString("id=\"id\""));
    }

    @Test
    @DisplayName("Given a cached symbol, a new request should not return attributes from the cache")
    void getSymbol_cachedSymbolDoesntReturnAttributes() {
        Symbol.get(new SymbolRequest.Builder()
                           .withName("science")
                           .withTitle("Title")
                           .withTooltip("Tooltip")
                           .withClasses("class1 class2")
                           .withId("id")
                           .build()
        );
        String symbol = Symbol.get(new SymbolRequest.Builder().withName("science").build());

        assertThat(symbol, not(containsString("<span class=\"jenkins-visually-hidden\">Title</span>")));
        assertThat(symbol, not(containsString("tooltip=\"Tooltip\"")));
        assertThat(symbol, not(containsString("class=\"class1 class2\"")));
        assertThat(symbol, not(containsString("id=\"id\"")));

    }

    @Test
    @DisplayName("Given a cached symbol, a new request can specify new attributes to use")
    void getSymbol_cachedSymbolAllowsSettingAllAttributes() {
        Symbol.get(new SymbolRequest.Builder()
                           .withName("science")
                           .withTitle("Title")
                           .withTooltip("Tooltip")
                           .withClasses("class1 class2")
                           .withId("id")
                           .build()
        );
        String symbol = Symbol.get(new SymbolRequest.Builder()
                                           .withName("science")
                                           .withTitle("Title2")
                                           .withTooltip("Tooltip2")
                                           .withClasses("class3 class4")
                                           .withId("id2")
                                           .build()
        );

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
    @DisplayName("When omitting tooltip from attributes, the symbol should not have a tooltip")
    void getSymbol_notSettingTooltipDoesntAddTooltipAttribute() {
        String symbol = Symbol.get(new SymbolRequest.Builder()
                           .withName("science")
                           .withTitle("Title")
                           .withClasses("class1 class2")
                           .withId("id")
                           .build()
        );

        assertThat(symbol, not(containsString("tooltip")));
    }

    @Test
    @DisplayName("When resolving a missing symbol, a placeholder is generated instead")
    void missingSymbolDefaultsToPlaceholder() {
        String symbol = Symbol.get(new SymbolRequest.Builder()
                                           .withName("missing-icon")
                                           .build()
        );
        assertThat(symbol, containsString(Symbol.PLACEHOLDER_MATCHER));
    }
}
