package hudson.tasks.junit;

import static org.junit.Assert.*;

import hudson.Extension;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.JenkinsRule;

public class TestNameTransformerTest {

    private static final String UNIQUE_NAME_FOR_TEST = "unique-name-to-test-name-transformer";
    @Rule public JenkinsRule j = new JenkinsRule();

    @Extension
    public static class TestTransformer extends TestNameTransformer {
        @Override
        public String transformName(String name) {
            if (UNIQUE_NAME_FOR_TEST.equals(name)) {
                return name + "-transformed";
            }
            return name;
        }
    }

    @Test
    public void testNameIsTransformed() {
        assertEquals(UNIQUE_NAME_FOR_TEST + "-transformed", TestNameTransformer.getTransformedName(UNIQUE_NAME_FOR_TEST));
    }

}

