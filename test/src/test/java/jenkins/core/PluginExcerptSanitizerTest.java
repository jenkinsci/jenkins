package jenkins.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class PluginExcerptSanitizerTest {
    @Test
    public void testSanitize(JenkinsRule j) throws Exception {
        final PluginExcerptSanitizer sanitizer = j.jenkins.getCoreLibrary(PluginExcerptSanitizer.class);
        assertNotNull(sanitizer);
        assertThat(sanitizer.sanitize("<a href='foo' target='bar'>label</a>"), equalTo("<a href=\"foo\" target=\"_blank\" rel=\"nofollow noopener noreferrer\">label</a>"));
        assertThat(sanitizer.sanitize("<a href='foo' target='_new'>label</a>"), equalTo("<a href=\"foo\" target=\"_blank\" rel=\"nofollow noopener noreferrer\">label</a>"));
        assertThat(sanitizer.sanitize("<a href='foo' rel='noopener'>label</a>"), equalTo("<a href=\"foo\" target=\"_blank\" rel=\"nofollow noopener noreferrer\">label</a>"));
        assertThat(sanitizer.sanitize("<a href='foo' target='bar' rel='baz'>label</a>"), equalTo("<a href=\"foo\" target=\"_blank\" rel=\"nofollow noopener noreferrer\">label</a>"));
    }
}
