package hudson.model;

import hudson.Launcher;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ParametersActionTest2 {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-170")
    public void undefinedParameters() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(Arrays.asList(new ParameterDefinition[]{
                new StringParameterDefinition("foo", "foo"),
                new StringParameterDefinition("bar", "bar")
        })));
        ParametersCheckBuilder b = new ParametersCheckBuilder(false);
        p.getBuildersList().add(b);
        p.save();

        j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new StringParameterValue("foo", "baz"),
                new StringParameterValue("undef", "undef")
        )));
        if (b.t != null) {
            throw b.t;
        }
    }

    @Test
    @Issue("SECURITY-170")
    public void undefinedParametersOverride() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(Arrays.asList(new ParameterDefinition[]{
                new StringParameterDefinition("foo", "foo"),
                new StringParameterDefinition("bar", "bar")
        })));
        ParametersCheckBuilder b = new ParametersCheckBuilder(true);
        p.getBuildersList().add(b);
        p.save();
        try {
            System.setProperty(ParametersAction.KEEP_UNDEFINED_PARAMETERS_SYSTEM_PROPERTY_NAME, "true");

            j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                    new StringParameterValue("foo", "baz"),
                    new StringParameterValue("undef", "undef")
            )));
            if (b.t != null) {
                throw b.t;
            }
        } finally {
            System.clearProperty(ParametersAction.KEEP_UNDEFINED_PARAMETERS_SYSTEM_PROPERTY_NAME);
        }
    }



    public static class ParametersCheckBuilder extends Builder {

        private final boolean expectLegacyBehavior;

        public ParametersCheckBuilder(boolean expectLegacyBehavior) {
            this.expectLegacyBehavior = expectLegacyBehavior;
        }

        private Exception t;
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            try {
                ParametersAction pa = build.getAction(ParametersAction.class);
                assertEquals("foo value expected changed", "baz", pa.getParameter("foo").getValue());
//                assertEquals("bar value expected default", "bar", pa.getParameter("bar").getValue()); // haha… no…

                if (expectLegacyBehavior) {
                    assertTrue("undef parameter is listed in getParameters", hasParameterWithName(pa.getParameters(), "undef"));
                    assertTrue("undef parameter is listed in iterator", hasParameterWithName(pa, "undef"));
                    assertTrue("undef in environment", build.getEnvironment(listener).keySet().contains("undef"));
                    assertTrue("UNDEF in environment", build.getEnvironment(listener).keySet().contains("UNDEF"));
                } else {
                    assertFalse("undef parameter is not listed in getParameters", hasParameterWithName(pa.getParameters(), "undef"));
                    assertFalse("undef parameter is not listed in iterator", hasParameterWithName(pa, "undef"));
                    assertFalse("undef not in environment", build.getEnvironment(listener).keySet().contains("undef"));
                    assertFalse("UNDEF not in environment", build.getEnvironment(listener).keySet().contains("UNDEF"));
                }

                assertTrue("undef parameter is listed in getAllParameters", hasParameterWithName(pa.getAllParameters(), "undef"));
                assertEquals("undef parameter direct access expected to work", "undef", pa.getParameter("undef").getValue());
            } catch (Exception e) {
                t = e;
            }

            return true;
        }

        private boolean hasParameterWithName(Iterable<ParameterValue> values, String name) {
            for (ParameterValue v : values) {
                if (v.getName().equals(name)) {
                    return true;
                }
            }
            return false;
        }
    }
}
