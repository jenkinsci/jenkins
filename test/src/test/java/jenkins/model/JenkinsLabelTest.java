package jenkins.model;

import static org.junit.jupiter.api.Assertions.assertSame;

import hudson.model.Label;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class JenkinsLabelTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void equivalentExpressionsShareTheSameLabelInstance() {
        Label first = j.jenkins.getLabel("linux&&ssd&&!maintenance");
        Label second = j.jenkins.getLabel("linux && ssd && !maintenance");

        assertSame(first, second);
    }
}