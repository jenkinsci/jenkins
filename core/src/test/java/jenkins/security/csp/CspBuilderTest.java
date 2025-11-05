package jenkins.security.csp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

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
    void testBasics() {
        final CspBuilder builder = new CspBuilder();
        builder.initialize(FetchDirective.DEFAULT_SRC, Directive.SELF);
        assertThat(builder.build(), is("default-src 'self';"));

        builder.add(Directive.IMG_SRC, Directive.SELF);
        assertThat(builder.build(), is("default-src 'self'; img-src 'self';"));

        builder.add(Directive.DEFAULT_SRC, Directive.DATA);
        assertThat(builder.build(), is("default-src 'self' data:; img-src 'self' data:;"));
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
    void emptyDirectiveGetsNone() {
        CspBuilder builder = new CspBuilder();
        builder.initialize(FetchDirective.IMG_SRC); // No values

        assertThat(builder.build(), is("img-src 'none';"));
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

        // TODO extra space?
        assertThat(builder.build(), is("sandbox ;"));
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
