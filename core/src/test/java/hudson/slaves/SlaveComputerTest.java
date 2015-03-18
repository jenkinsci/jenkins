package hudson.slaves;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

/**
 * @author Stephen Connolly
 */
public class SlaveComputerTest {
    @Test
    public void isRelativePathUnix() {
        assertThat("/", not(aRelativePath()));
        assertThat("/foo/bar", not(aRelativePath()));
        assertThat("/foo/../bar", not(aRelativePath()));
        assertThat("", aRelativePath());
        assertThat(".", aRelativePath());
        assertThat("..", aRelativePath());
        assertThat("./foo", aRelativePath());
        assertThat("./foo/bar", aRelativePath());
        assertThat("./foo/bar/", aRelativePath());
    }

    @Test
    public void isRelativePathWindows() {
        assertThat("\\", aRelativePath());
        assertThat("\\foo\\bar", aRelativePath());
        assertThat("\\foo\\..\\bar", aRelativePath());
        assertThat("", aRelativePath());
        assertThat(".", aRelativePath());
        assertThat(".\\foo", aRelativePath());
        assertThat(".\\foo\\bar", aRelativePath());
        assertThat(".\\foo\\bar\\", aRelativePath());
        assertThat("\\\\foo", aRelativePath());
        assertThat("\\\\foo\\", not(aRelativePath()));
        assertThat("\\\\foo\\c", not(aRelativePath()));
        assertThat("C:", aRelativePath());
        assertThat("z:", aRelativePath());
        assertThat("0:", aRelativePath());
        assertThat("c:.", aRelativePath());
        assertThat("c:\\", not(aRelativePath()));
        assertThat("c:/", not(aRelativePath()));
    }

    private static RelativePathMatcher aRelativePath() {
        return new RelativePathMatcher();
    }

    private static class RelativePathMatcher extends BaseMatcher<String> {

        @Override
        public boolean matches(Object item) {
            return SlaveComputer.isRelativePath((String)item);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("a relative path");
        }
    }
}
