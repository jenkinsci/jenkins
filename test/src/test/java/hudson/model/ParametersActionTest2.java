package hudson.model;

import hudson.Launcher;
import hudson.tasks.Builder;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    @Test
    @Issue("SECURITY-170")
    @LocalData
    public void backwardCompatibility() throws Exception {
        // Local data contains a parameterized job with two parameters (FOO and BAR) and one build
        // with pre-fix format (generated with 1.609.3) with FOO, BAR and UNDEF.
        FreeStyleProject p = j.jenkins.getItemByFullName("parameterized", FreeStyleProject.class);
        FreeStyleBuild b1 = p.getBuildByNumber(1);
        ParametersAction pa = b1.getAction(ParametersAction.class);
        ParametersCheckBuilder.hasParameterWithName(pa, "FOO");
        ParametersCheckBuilder.hasParameterWithName(pa, "BAR");
        // legacy behaviour expected (UNDEF returned by getParameters())
        ParametersCheckBuilder.hasParameterWithName(pa, "UNDEF");

        // A new build should work as expected (undef is not published to env)
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
    public void parametersDefinitionChange() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(Arrays.asList(new ParameterDefinition[] {
                new StringParameterDefinition("foo", "foo"),
                new StringParameterDefinition("bar", "bar")
        })));

        FreeStyleBuild build = j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new StringParameterValue("foo", "baz"),
                new StringParameterValue("bar", "bar"),
                new StringParameterValue("undef", "undef")
        )));

        assertTrue("undef parameter is not listed in getParameters",
                !ParametersCheckBuilder.hasParameterWithName(build.getAction(ParametersAction.class), "undef"));

        p.removeProperty(ParametersDefinitionProperty.class);
        p.addProperty(new ParametersDefinitionProperty(Arrays.asList(new ParameterDefinition[] {
                new StringParameterDefinition("foo", "foo"),
                new StringParameterDefinition("bar", "bar"),
                new StringParameterDefinition("undef", "undef")
        })));

        // undef is still not listed even after being added to the job parameters definition
        assertTrue("undef parameter is not listed in getParameters",
                !ParametersCheckBuilder.hasParameterWithName(build.getAction(ParametersAction.class), "undef"));

        // remove bar and undef from parameters definition
        p.removeProperty(ParametersDefinitionProperty.class);
        p.addProperty(new ParametersDefinitionProperty(Arrays.asList(new ParameterDefinition[] {
                new StringParameterDefinition("foo", "foo")
        })));

        assertTrue("the build still have 2 parameters", build.getAction(ParametersAction.class).getParameters().size() == 2);
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

        public static boolean hasParameterWithName(Iterable<ParameterValue> values, String name) {
            for (ParameterValue v : values) {
                if (v.getName().equals(name)) {
                    return true;
                }
            }
            return false;
        }
    }
}
