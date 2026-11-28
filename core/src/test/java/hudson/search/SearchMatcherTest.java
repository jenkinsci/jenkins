package hudson.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class SearchMatcherTest {

    @Test
    void hyphenInQueryMatchesSpaceInName() {
        assertTrue(SearchMatcher.of("design-library").matches("Design Library"));
    }

    @Test
    void spaceInQueryMatchesHyphenInName() {
        assertTrue(SearchMatcher.of("log highlight").matches("log-highlight-test"));
    }

    @Test
    void underscoreMatchesHyphenAndSpace() {
        assertTrue(SearchMatcher.of("my_job").matches("my-job"));
        assertTrue(SearchMatcher.of("my_job").matches("my job"));
        assertTrue(SearchMatcher.of("my job").matches("my_job"));
    }

    @Test
    void runsOfSeparatorsCountAsOne() {
        assertTrue(SearchMatcher.of("a -_ b").matches("a b"));
        assertTrue(SearchMatcher.of("a b").matches("a__--  b"));
    }

    @Test
    void leadingAndTrailingSeparatorsAreIgnored() {
        assertTrue(SearchMatcher.of("-deploy ").matches("deploy-prod"));
    }

    @Test
    void matchesPartOfName() {
        assertTrue(SearchMatcher.of("light").matches("log-highlight-test"));
    }

    @Test
    void ignoresCaseByDefault() {
        assertTrue(SearchMatcher.of("DESIGN").matches("design-library"));
        assertTrue(SearchMatcher.of("design").matches("DESIGN LIBRARY"));
    }

    @Test
    void ignoresCaseIndependentOfDefaultLocale() {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertTrue(SearchMatcher.of("INFRA").matches("infra-build"));
            assertTrue(SearchMatcher.of("integration").matches("CI-Integration"));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    void doesNotMatchDifferentText() {
        assertFalse(SearchMatcher.of("design-libraries").matches("Design Library"));
        assertFalse(SearchMatcher.of("deploy").matches("build"));
    }

    @Test
    void separatorsStillSeparateWords() {
        assertFalse(SearchMatcher.of("loghighlight").matches("log-highlight"));
    }

    @Test
    void emptyQueryMatchesEverything() {
        assertTrue(SearchMatcher.of("").matches("anything"));
        assertTrue(SearchMatcher.of(" - ").matches("anything"));
    }

    @Test
    void doesNotMatchMissingName() {
        assertFalse(SearchMatcher.of("deploy").matches(null));
    }
}
