package org.jenkins.ui.symbol;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

public class SymbolTest {
    public static final String SCIENCE_PATH;

    public static final String IMAGES_SYMBOLS_SCIENCE_PATH_XML = "/images/symbols/science.path.xml";

    static {
        try {
            try (InputStream resourceAsStream = SymbolTest.class.getResourceAsStream(IMAGES_SYMBOLS_SCIENCE_PATH_XML)) {
                if (resourceAsStream == null) {
                    throw new IllegalStateException("Could not find resource" + IMAGES_SYMBOLS_SCIENCE_PATH_XML);
                }
                SCIENCE_PATH = IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

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
        assertThat(symbol, containsString(SCIENCE_PATH));
        assertThat(symbol, containsString("<span class=\"jenkins-visually-hidden\">Title</span>"));
        assertThat(symbol, containsString("tooltip=\"Tooltip\""));
        assertThat(symbol, containsString("class=\"class1 class2\""));
        assertThat(symbol, containsString("id=\"id\""));
    }

    @Test
    @DisplayName("HTML tooltip overrides tooltip")
    void htmlTooltipOverridesTooltip() {
        String symbol = Symbol.get(new SymbolRequest.Builder()
                .withName("science")
                .withTooltip("Tooltip")
                .withHtmlTooltip("<p>Some HTML Tooltip</p>")
                .build()
        );
        assertThat(symbol, containsString(SCIENCE_PATH));
        assertThat(symbol, not(containsString("tooltip=\"Tooltip\"")));
        assertThat(symbol, containsString("data-html-tooltip=\"&lt;p&gt;Some HTML Tooltip&lt;/p&gt;\""));
    }

    @Test
    @DisplayName("Invalid strings should throw IllegalArgumentException")
    void invalidRawString() {
        assertThrows(IllegalArgumentException.class, () -> new SymbolRequest.Builder().build());
        assertThrows(IllegalArgumentException.class, () -> new SymbolRequest.Builder().withRaw("").build());
        assertThrows(IllegalArgumentException.class, () -> new SymbolRequest.Builder().withRaw("foobar").build());
        assertThrows(IllegalArgumentException.class, () -> new SymbolRequest.Builder().withRaw("plugin-foo").build());
        assertThrows(IllegalArgumentException.class, () -> new SymbolRequest.Builder().withRaw("symbol-foo plugin-bar someclass").build());
    }

    @Test
    @DisplayName("Given a raw string it can be parsed to a name")
    void rawStringCore() {
        SymbolRequest symbol = new SymbolRequest.Builder().withRaw("symbol-gear").build();
        assertNull(symbol.getPluginName());
        assertEquals("gear", symbol.getName());
    }

    @Test
    @DisplayName("Given a raw string it can be parsed to a name and plugin")
    void rawStringPlugin() {
        SymbolRequest symbol = new SymbolRequest.Builder().withRaw("symbol-science plugin-foo").build();
        assertEquals("foo", symbol.getPluginName());
        assertEquals("science", symbol.getName());
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

        assertThat(symbol, containsString(SCIENCE_PATH));
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

        assertThat(symbol, containsString(SCIENCE_PATH));
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

        assertThat(symbol, containsString(SCIENCE_PATH));
        assertThat(symbol, not(containsString("tooltip")));
    }

    @Test
    @DisplayName("When resolving a missing symbol, a placeholder is generated instead")
    void missingSymbolDefaultsToPlaceholder() {
        String symbol = Symbol.get(new SymbolRequest.Builder()
                                           .withName("missing-icon")
                                           .build()
        );
        assertThat(symbol, not(containsString(SCIENCE_PATH)));
        assertThat(symbol, containsString(Symbol.PLACEHOLDER_MATCHER));
    }

    @Test
    @DisplayName("If tooltip is not provided symbol should never have a tooltip")
    void getSymbol_notSettingTooltipDoesntAddTooltipAttribute_evenWithAmpersand() {
        SymbolRequest.Builder builder = new SymbolRequest.Builder()
                .withName("science")
                .withTitle("Title")
                .withTooltip("With&Ampersand")
                .withClasses("class1 class2")
                .withId("id");
        String symbol = Symbol.get(builder.build());
        assertThat(symbol, containsString(SCIENCE_PATH));
        assertThat(symbol, containsString("tooltip"));
        // Remove tooltip
        builder.withTooltip(null);
        symbol = Symbol.get(builder.build());
        assertThat(symbol, containsString(SCIENCE_PATH));
        assertThat(symbol, not(containsString("tooltip")));
    }

    @Test
    @DisplayName("IDs in symbol should not be removed")
    @Issue("JENKINS-70730")
    void getSymbol_idInSymbolIsPresent() {
        String symbol = Symbol.get(new SymbolRequest.Builder()
                .withId("some-random-id")
                .withName("with-id").build());

        assertThat(symbol, containsString("id=\"a\""));
    }
}
