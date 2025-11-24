package jenkins.security.csp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.util.RingBufferLogHandler;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CspBuilderTest {
    private RingBufferLogHandler logHandler;
    private Logger logger;
    private List<LogRecord> logRecords;

    @BeforeEach
    void setUp() {
        logHandler = new RingBufferLogHandler(50);
        logger = Logger.getLogger(CspBuilder.class.getName());
        logger.addHandler(logHandler);
        logger.setLevel(Level.CONFIG);
        logRecords = logHandler.getView();
    }

    @AfterEach
    void tearDown() {
        logger.removeHandler(logHandler);
    }

    @Test
    void nothingInitializedFallsBackToDefaultSrc() {
        final CspBuilder builder = new CspBuilder();
        builder.add(Directive.DEFAULT_SRC, Directive.SELF);
        assertThat(builder.build(), is("default-src 'self';"));

        builder.add(Directive.IMG_SRC, Directive.SELF);
        assertThat(builder.build(), is("default-src 'self'; img-src 'self';"));

        builder.add(Directive.DEFAULT_SRC, Directive.DATA);
        assertThat(builder.build(), is("default-src 'self' data:; img-src 'self' data:;"));
    }

    @Test
    void testInitializedDirectiveDoesNotInherit() {
        CspBuilder builder = new CspBuilder();
        builder.initialize(FetchDirective.DEFAULT_SRC, Directive.SELF);
        builder.initialize(FetchDirective.IMG_SRC);

        // img-src was initialized, so it should NOT inherit from default-src
        assertThat(builder.build(), is("default-src 'self'; img-src 'none';"));
    }

    @Test
    void testSimpleFallback() {
        final CspBuilder builder = new CspBuilder();
        builder.add(Directive.DEFAULT_SRC, Directive.SELF);
        builder.add(Directive.IMG_SRC);

        // This seems a little weird, but it's harmless and allows #initialize to reuse #add
        assertThat(builder.build(), is("default-src 'self'; img-src 'self';"));
    }

    @Test
    void testFallbackComposition() {
        CspBuilder builder = new CspBuilder();
        builder.initialize(FetchDirective.SCRIPT_SRC, Directive.SELF);
        builder.add(Directive.SCRIPT_SRC_ELEM, Directive.UNSAFE_INLINE);

        // script-src-elem should inherit from script-src (not default-src)
        assertThat(builder.build(), is("script-src 'self'; script-src-elem 'self' 'unsafe-inline';"));
    }

    @Test
    void fallbackToNone() {
        final CspBuilder builder = new CspBuilder();
        builder.add(Directive.DEFAULT_SRC);
        assertThat(builder.build(), is("default-src 'none';"));

        // Add img-src directive, but inherit from default-src
        builder.add(Directive.IMG_SRC);
        assertThat(builder.build(), is("default-src 'none'; img-src 'none';"));

        // Still inherit (no-op right now), but have a new value
        builder.add(Directive.IMG_SRC, Directive.SELF);
        assertThat(builder.build(), is("default-src 'none'; img-src 'self';"));

        // Now actually inherit something
        builder.add(Directive.DEFAULT_SRC, Directive.DATA);
        assertThat(builder.build(), is("default-src data:; img-src 'self' data:;"));

        // Initialized empty value means we don't inherit and are 'none' valued
        builder.initialize(FetchDirective.SCRIPT_SRC);
        assertThat(builder.build(), is("default-src data:; img-src 'self' data:; script-src 'none';"));
    }

    @Test
    void emptyAddThenInit() {
        CspBuilder builder = new CspBuilder();
        builder.initialize(FetchDirective.DEFAULT_SRC, Directive.SELF);
        assertThat(builder.build(), is("default-src 'self';"));

        builder.add(Directive.IMG_SRC);
        assertThat(builder.build(), is("default-src 'self'; img-src 'self';"));

        builder.initialize(FetchDirective.IMG_SRC);
        assertThat(builder.build(), is("default-src 'self'; img-src 'none';"));
    }

    @Test
    void testRemoveDirective() {
        CspBuilder builder = new CspBuilder();
        builder.initialize(FetchDirective.DEFAULT_SRC, Directive.SELF);
        builder.initialize(FetchDirective.IMG_SRC, Directive.DATA);
        builder.remove(Directive.IMG_SRC);

        // After removal, img-src should be gone entirely
        assertThat(builder.build(), is("default-src 'self';"));
    }

    @Test
    void nonFetchEmptyTest() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.SANDBOX);

        assertThat(builder.build(), is("sandbox;"));
    }

    @Test
    void nonFetchNonEmptyTestFrameAncestors() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.FRAME_ANCESTORS);

        assertThat(builder.build(), is("frame-ancestors 'none';"));
    }

    @Test
    void nonFetchNonEmptyTestFormAction() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.FORM_ACTION);

        assertThat(builder.build(), is("form-action 'none';"));
    }

    @Test
    void testProhibitedDirective_reportUri() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.REPORT_URI, "https://example.com/csp-report");
        assertThat(logRecords, hasItem(logMessageContainsString("Directive report-uri cannot be set manually")));
        String csp = builder.build();
        assertThat(csp, is(""));
    }

    @Test
    void testProhibitedDirective_reportTo() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.REPORT_TO, "csp-endpoint");
        assertThat(logRecords, hasItem(logMessageContainsString("Directive report-to cannot be set manually")));
        String csp = builder.build();
        assertThat(csp, is(""));
    }

    @Test
    void testExplicitNoneValue_isSkipped() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.DEFAULT_SRC, Directive.SELF);
        builder.add(Directive.DEFAULT_SRC, Directive.NONE);
        assertThat(logRecords, hasItem(logMessageContainsString("Cannot explicitly add 'none'")));
        assertThat(logRecords, hasItem(logMessageContainsString(Directive.class.getName() + "#NONE Javadoc")));
        String csp = builder.build();
        assertThat(csp, is("default-src 'self';"));
    }

    @Test
    void testExplicitNoneValue_mixedWithValidValues() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.SCRIPT_SRC, Directive.SELF, Directive.NONE, Directive.UNSAFE_INLINE);
        assertThat(logRecords, hasItem(logMessageContainsString("Cannot explicitly add 'none'")));
        String csp = builder.build();
        assertThat(csp, containsString("script-src"));
        assertThat(csp, containsString("'self'"));
        assertThat(csp, containsString("'unsafe-inline'"));
    }

    @Test
    void testBuilderReturnsThis_whenProhibitedDirectiveUsed() {
        CspBuilder builder = new CspBuilder();

        // Should return builder for chaining even when directive is prohibited
        CspBuilder result = builder.add(Directive.REPORT_URI, "https://example.com");
        assertThat(result, is(builder)); // Same instance
    }

    @Test
    void testNoLogging_whenValidDirectivesUsed() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.DEFAULT_SRC, Directive.SELF);
        builder.add(Directive.SCRIPT_SRC, Directive.UNSAFE_INLINE);
        assertThat(logRecords.isEmpty(), is(true));
    }

    @Test
    void testRemoveMultipleValuesFromDirective() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.DEFAULT_SRC, Directive.SELF, Directive.DATA, Directive.BLOB);
        assertThat(builder.build(), is("default-src 'self' blob: data:;"));

        // Remove specific values
        builder.remove(Directive.DEFAULT_SRC, Directive.DATA);
        assertThat(builder.build(), is("default-src 'self' blob:;"));

        // Remove another value
        builder.remove(Directive.DEFAULT_SRC, Directive.BLOB);
        assertThat(builder.build(), is("default-src 'self';"));
    }

    @Test
    void testRemoveMultipleValuesAtOnce() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.SCRIPT_SRC, Directive.SELF, Directive.UNSAFE_INLINE, Directive.UNSAFE_EVAL, Directive.DATA);
        assertThat(builder.build(), is("script-src 'self' 'unsafe-eval' 'unsafe-inline' data:;"));

        // Remove multiple values at once
        builder.remove(Directive.SCRIPT_SRC, Directive.UNSAFE_INLINE, Directive.UNSAFE_EVAL);
        assertThat(builder.build(), is("script-src 'self' data:;"));
    }

    @Test
    void testRemoveNonExistentValue() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.DEFAULT_SRC, Directive.SELF);
        assertThat(builder.build(), is("default-src 'self';"));

        // Try to remove a value that doesn't exist - should be no-op
        builder.remove(Directive.DEFAULT_SRC, Directive.DATA);
        assertThat(builder.build(), is("default-src 'self';"));
    }

    @Test
    void testRemoveValuesFromNonExistentDirective() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.DEFAULT_SRC, Directive.SELF);

        // Try to remove values from a directive that was never added - should be no-op
        builder.remove(Directive.IMG_SRC, Directive.DATA);
        assertThat(builder.build(), is("default-src 'self';"));
    }

    @Test
    void testRemoveValuesFromNonFetchDirective() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.FORM_ACTION, Directive.SELF, "https://example.com");
        assertThat(builder.build(), containsString("form-action"));

        // Remove specific value from non-fetch directive
        builder.remove(Directive.FORM_ACTION, "https://example.com");
        assertThat(builder.build(), is("form-action 'self';"));
    }

    @Test
    void testRemoveEntireNonFetchDirective() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.FORM_ACTION, Directive.SELF);
        builder.add(Directive.FRAME_ANCESTORS, Directive.SELF);
        assertThat(builder.build(), containsString("form-action"));
        assertThat(builder.build(), containsString("frame-ancestors"));

        // Remove entire non-fetch directive
        builder.remove(Directive.FORM_ACTION);
        assertThat(builder.build(), is("frame-ancestors 'self';"));
    }

    @Test
    void testGetMergedDirectivesReturnsInheritanceInfo() {
        CspBuilder builder = new CspBuilder();
        builder.initialize(FetchDirective.DEFAULT_SRC, Directive.SELF);
        builder.add(Directive.IMG_SRC, Directive.DATA);
        builder.initialize(FetchDirective.SCRIPT_SRC, Directive.UNSAFE_INLINE);

        List<Directive> merged = builder.getMergedDirectives();

        // Find each directive and check inheritance flag
        Directive defaultSrc = merged.stream()
                .filter(d -> d.name().equals(Directive.DEFAULT_SRC))
                .findFirst()
                .orElse(null);
        assertThat(defaultSrc, is(notNullValue()));
        assertThat(defaultSrc.inheriting(), is(false)); // initialized

        Directive imgSrc = merged.stream()
                .filter(d -> d.name().equals(Directive.IMG_SRC))
                .findFirst()
                .orElse(null);
        assertThat(imgSrc, is(notNullValue()));
        assertThat(imgSrc.inheriting(), is(true)); // not initialized, inherits from default-src

        Directive scriptSrc = merged.stream()
                .filter(d -> d.name().equals(Directive.SCRIPT_SRC))
                .findFirst()
                .orElse(null);
        assertThat(scriptSrc, is(notNullValue()));
        assertThat(scriptSrc.inheriting(), is(false)); // initialized
    }

    @Test
    void testGetMergedDirectivesValuesAreImmutable() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.DEFAULT_SRC, Directive.SELF);

        List<Directive> merged = builder.getMergedDirectives();
        Directive directive = merged.get(0);

        assertThrows(UnsupportedOperationException.class, () -> directive.values().add("should-fail"));
    }

    @Test
    void testMultipleInitializeSameDirective() {
        CspBuilder builder = new CspBuilder();
        builder.initialize(FetchDirective.DEFAULT_SRC, Directive.SELF);
        builder.initialize(FetchDirective.DEFAULT_SRC, Directive.DATA);

        // Both values should be present
        assertThat(builder.build(), is("default-src 'self' data:;"));
    }

    @Test
    void testInitializeAfterAdd() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.IMG_SRC, Directive.SELF);
        builder.add(Directive.DEFAULT_SRC, Directive.BLOB);

        // At this point, img-src inherits from default-src
        assertThat(builder.build(), is("default-src blob:; img-src 'self' blob:;"));

        builder.initialize(FetchDirective.IMG_SRC, Directive.DATA);

        // Now img-src is initialized and no longer inherits blob: from default-src
        assertThat(builder.build(), is("default-src blob:; img-src 'self' data:;"));
    }

    @Test
    void testRemoveDirectiveRemovesInitializationFlag() {
        CspBuilder builder = new CspBuilder();
        builder.initialize(FetchDirective.IMG_SRC, Directive.DATA);
        assertThat(builder.build(), is("img-src data:;"));

        // Remove the directive entirely
        builder.remove(Directive.IMG_SRC);

        // Add default-src
        builder.add(Directive.DEFAULT_SRC, Directive.SELF);

        // Add img-src again - should now inherit because initialization flag was cleared
        builder.add(Directive.IMG_SRC, Directive.DATA);
        assertThat(builder.build(), is("default-src 'self'; img-src 'self' data:;"));
    }

    @Test
    void testFallbackToUninitializedDefaultSrc() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.DEFAULT_SRC, Directive.SELF);
        builder.add(Directive.IMG_SRC, Directive.DATA);

        // Both should be present, img-src should inherit from default-src
        assertThat(builder.build(), is("default-src 'self'; img-src 'self' data:;"));
    }

    @Test
    void testNullValue() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.IMG_SRC, (String) null);
        assertThat(builder.build(), is(""));
    }

    @Test
    void testNullValueInheriting() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.DEFAULT_SRC, Directive.SELF);
        builder.add(Directive.IMG_SRC, (String) null);
        assertThat(builder.build(), is("default-src 'self';"));
    }

    @Test
    void testNoValueInheriting() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.DEFAULT_SRC, Directive.SELF);
        builder.add(Directive.IMG_SRC);
        assertThat(builder.build(), is("default-src 'self'; img-src 'self';"));
    }

    @Test
    void testNoValueNoninheriting() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.DEFAULT_SRC, Directive.SELF);
        builder.initialize(FetchDirective.IMG_SRC);
        assertThat(builder.build(), is("default-src 'self'; img-src 'none';"));

        builder.add(Directive.IMG_SRC, "example.org");
        assertThat(builder.build(), is("default-src 'self'; img-src example.org;"));
    }

    @Test
    void testNullValueNoninheriting() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.DEFAULT_SRC, Directive.SELF);
        builder.initialize(FetchDirective.IMG_SRC, (String) null);
        assertThat(builder.build(), is("default-src 'self';"));
        builder.add(Directive.IMG_SRC, "example.org");
        // Initialization was ignored
        assertThat(builder.build(), is("default-src 'self'; img-src 'self' example.org;"));
        assertThat(logRecords, hasItem(logMessageContainsString("Ignoring initialization call with no-op null values list for img-src")));
    }

    @Test
    void testNullValueAddition() {
        CspBuilder builder = new CspBuilder();
        builder.add(Directive.IMG_SRC, "example.org");
        builder.add(Directive.IMG_SRC, (String) null);
        assertThat(builder.build(), is("img-src example.org;"));
    }

    private static Matcher<LogRecord> logMessageContainsString(String needle) {
        return new LogMessageContainsString(containsString(needle));
    }

    private static final class LogMessageContainsString extends TypeSafeMatcher<LogRecord> {
        private final Matcher<String> stringMatcher;

        LogMessageContainsString(Matcher<String> stringMatcher) {
            this.stringMatcher = stringMatcher;
        }

        @Override
        protected boolean matchesSafely(LogRecord item) {
            return stringMatcher.matches(item.getMessage());
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("a LogRecord with a message matching ");
            stringMatcher.describeTo(description);
        }
    }

}
