package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Functions;
import hudson.Launcher;
import hudson.XmlFile;
import hudson.tasks.Builder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class ParametersAction2Test {

    private final LogRecorder logs = new LogRecorder().record("", Level.WARNING).capture(100);

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("SECURITY-170")
    void undefinedParameters() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(Arrays.asList(
                new StringParameterDefinition("foo", "foo"),
                new StringParameterDefinition("bar", "bar")
        )));
        ParametersCheckBuilder b = new ParametersCheckBuilder(false);
        p.getBuildersList().add(b);
        p.save();

        j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new StringParameterValue("foo", "baz"),
                new StringParameterValue("undef", "undef")
        )));
    }

    @Test
    @Issue("SECURITY-170")
    void undefinedParametersOverride() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(Arrays.asList(
                new StringParameterDefinition("foo", "foo"),
                new StringParameterDefinition("bar", "bar")
        )));
        ParametersCheckBuilder b = new ParametersCheckBuilder(true);
        p.getBuildersList().add(b);
        p.save();
        try {
            System.setProperty(ParametersAction.KEEP_UNDEFINED_PARAMETERS_SYSTEM_PROPERTY_NAME, "true");

            j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                    new StringParameterValue("foo", "baz"),
                    new StringParameterValue("undef", "undef")
            )));
        } finally {
            System.clearProperty(ParametersAction.KEEP_UNDEFINED_PARAMETERS_SYSTEM_PROPERTY_NAME);
        }
    }

    @Test
    @Issue("SECURITY-170")
    @LocalData
    void backwardCompatibility() throws Exception {
        // Local data contains a parameterized job with two parameters (FOO and BAR) and one build
        // with pre-fix format (generated with 1.609.3) with FOO, BAR and UNDEF.
        FreeStyleProject p = j.jenkins.getItemByFullName(Functions.isWindows() ? "parameterized-windows" : "parameterized", FreeStyleProject.class);

        FreeStyleBuild b1 = p.getBuildByNumber(1);
        ParametersAction pa = b1.getAction(ParametersAction.class);
        hasParameterWithName(pa, "FOO");
        hasParameterWithName(pa, "BAR");
        // legacy behaviour expected (UNDEF returned by getParameters())
        hasParameterWithName(pa, "UNDEF");

        // A new build should work as expected (undef is not published to env)
        ParametersCheckBuilder b = new ParametersCheckBuilder(false);
        p.getBuildersList().add(b);
        p.save();

        j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new StringParameterValue("foo", "baz"),
                new StringParameterValue("undef", "undef")
        )));
    }

    @Test
    @Issue("SECURITY-170")
    void parametersDefinitionChange() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(Arrays.asList(
                new StringParameterDefinition("foo", "foo"),
                new StringParameterDefinition("bar", "bar"))));

        FreeStyleBuild build = j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new StringParameterValue("foo", "baz"),
                new StringParameterValue("bar", "bar"),
                new StringParameterValue("undef", "undef")
        )));

        assertFalse(hasParameterWithName(build.getAction(ParametersAction.class), "undef"),
                "undef parameter is not listed in getParameters");

        p.removeProperty(ParametersDefinitionProperty.class);
        p.addProperty(new ParametersDefinitionProperty(Arrays.asList(
                new StringParameterDefinition("foo", "foo"),
                new StringParameterDefinition("bar", "bar"),
                new StringParameterDefinition("undef", "undef"))));

        // undef is still not listed even after being added to the job parameters definition
        assertFalse(hasParameterWithName(build.getAction(ParametersAction.class), "undef"),
                "undef parameter is not listed in getParameters");

        // remove bar and undef from parameters definition
        p.removeProperty(ParametersDefinitionProperty.class);
        p.addProperty(new ParametersDefinitionProperty(List.of(
                new StringParameterDefinition("foo", "foo"))));

        assertEquals(2, build.getAction(ParametersAction.class).getParameters().size(), "the build still have 2 parameters");

        p.removeProperty(ParametersDefinitionProperty.class);
        assertEquals(2, build.getAction(ParametersAction.class).getParameters().size(), "the build still have 2 parameters");
    }

    @Test
    @Issue("SECURITY-170")
    void whitelistedParameter() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(Arrays.asList(
                new StringParameterDefinition("foo", "foo"),
                new StringParameterDefinition("bar", "bar"))));

        try {
            System.setProperty(ParametersAction.SAFE_PARAMETERS_SYSTEM_PROPERTY_NAME, "whitelisted1,whitelisted2");
            FreeStyleBuild build = j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                    new StringParameterValue("foo", "baz"),
                    new StringParameterValue("bar", "bar"),
                    new StringParameterValue("whitelisted1", "x"),
                    new StringParameterValue("whitelisted2", "y")
            )));

            assertTrue(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted1"),
                    "whitelisted1 parameter is listed in getParameters");
            assertTrue(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted2"),
                    "whitelisted2 parameter is listed in getParameters");
        } finally {
            System.clearProperty(ParametersAction.SAFE_PARAMETERS_SYSTEM_PROPERTY_NAME);
        }
    }

    @Test
    @Issue("SECURITY-170")
    void whitelistedParameterByOverride() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        String name = p.getFullName();
        p.addProperty(new ParametersDefinitionProperty(Arrays.asList(
                new StringParameterDefinition("foo", "foo"),
                new StringParameterDefinition("bar", "bar"))));

        try {
            ParametersAction action = new ParametersAction(
                    Arrays.asList(
                        new StringParameterValue("foo", "baz"),
                        new StringParameterValue("bar", "bar"),
                        new StringParameterValue("whitelisted1", "x"),
                        new StringParameterValue("whitelisted2", "y"),
                        new StringParameterValue("whitelisted3", "y")
                                                 ),
                    Arrays.asList("whitelisted1", "whitelisted2"));
            FreeStyleBuild build = j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), action));

            assertTrue(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted1"),
                       "whitelisted1 parameter is listed in getParameters");
            assertTrue(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted2"),
                       "whitelisted2 parameter is listed in getParameters");
            assertFalse(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted3"),
                       "whitelisted3 parameter is listed in getParameters");
            j.jenkins.reload();
            //Test again after reload
            p = j.jenkins.getItemByFullName(name, FreeStyleProject.class);
            build = p.getLastBuild();
            assertTrue(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted1"),
                       "whitelisted1 parameter is listed in getParameters");
            assertTrue(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted2"),
                       "whitelisted2 parameter is listed in getParameters");
            assertFalse(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted3"),
                       "whitelisted3 parameter is listed in getParameters");
        } finally {
            System.clearProperty(ParametersAction.SAFE_PARAMETERS_SYSTEM_PROPERTY_NAME);
        }
    }

    @Test
    @Issue("SECURITY-170")
    void whitelistedParameterSameAfterChange() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        String name = p.getFullName();
        p.addProperty(new ParametersDefinitionProperty(Arrays.asList(
                new StringParameterDefinition("foo", "foo"),
                new StringParameterDefinition("bar", "bar"))));

        try {
            System.setProperty(ParametersAction.SAFE_PARAMETERS_SYSTEM_PROPERTY_NAME, "whitelisted1,whitelisted2");
            FreeStyleBuild build = j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                    new StringParameterValue("foo", "baz"),
                    new StringParameterValue("bar", "bar"),
                    new StringParameterValue("whitelisted1", "x"),
                    new StringParameterValue("whitelisted2", "y"),
                    new StringParameterValue("whitelisted3", "z"),
                    new StringParameterValue("whitelisted4", "w")
            )));
            assertTrue(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted1"),
                       "whitelisted1 parameter is listed in getParameters");
            assertTrue(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted2"),
                       "whitelisted2 parameter is listed in getParameters");
            assertFalse(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted3"),
                       "whitelisted3 parameter is listed in getParameters");
            assertFalse(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted4"),
                       "whitelisted4 parameter is listed in getParameters");

            System.setProperty(ParametersAction.SAFE_PARAMETERS_SYSTEM_PROPERTY_NAME, "whitelisted3,whitelisted4");
            j.jenkins.reload();
            p = j.jenkins.getItemByFullName(name, FreeStyleProject.class);
            build = p.getLastBuild();
            assertTrue(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted1"),
                       "whitelisted1 parameter is listed in getParameters");
            assertTrue(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted2"),
                       "whitelisted2 parameter is listed in getParameters");
            assertFalse(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted3"),
                        "whitelisted3 parameter is listed in getParameters");
            assertFalse(hasParameterWithName(build.getAction(ParametersAction.class), "whitelisted4"),
                        "whitelisted4 parameter is listed in getParameters");

        } finally {
            System.clearProperty(ParametersAction.SAFE_PARAMETERS_SYSTEM_PROPERTY_NAME);
        }
    }


    @Test
    @Issue("SECURITY-170")
    void nonParameterizedJob() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild build = j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new StringParameterValue("foo", "baz"),
                new StringParameterValue("bar", "bar")
        )));

        assertFalse(hasParameterWithName(build.getAction(ParametersAction.class), "foo"),
                "foo parameter is not listed in getParameters");
        assertFalse(hasParameterWithName(build.getAction(ParametersAction.class), "bar"),
                "bar parameter is not listed in getParameters");
    }

    @Test
    @Issue("SECURITY-170")
    void nonParameterizedJobButWhitelisted() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        try {
            System.setProperty(ParametersAction.SAFE_PARAMETERS_SYSTEM_PROPERTY_NAME, "foo,bar");
            FreeStyleBuild build2 = j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                    new StringParameterValue("foo", "baz"),
                    new StringParameterValue("bar", "bar")
            )));

            assertTrue(hasParameterWithName(build2.getAction(ParametersAction.class), "foo"),
                    "foo parameter is listed in getParameters");
            assertTrue(hasParameterWithName(build2.getAction(ParametersAction.class), "bar"),
                    "bar parameter is listed in getParameters");
        } finally {
            System.clearProperty(ParametersAction.SAFE_PARAMETERS_SYSTEM_PROPERTY_NAME);
        }
    }

    @Test
    @Issue("JENKINS-45472")
    void ensureNoListReuse() throws Exception {
        FreeStyleProject p1 = j.createFreeStyleProject();
        p1.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("foo", "")));
        FreeStyleProject p2 = j.createFreeStyleProject();
        p2.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("foo", "")));

        List<ParameterValue> params = new ArrayList<>();
        params.add(new StringParameterValue("foo", "for p1"));
        p1.scheduleBuild2(1, new ParametersAction(params));
        params.clear();
        params.add(new StringParameterValue("foo", "for p2"));
        p2.scheduleBuild2(0, new ParametersAction(params));

        j.waitUntilNoActivity();

        assertEquals(1, p1.getLastBuild().getAction(ParametersAction.class).getParameters().size());
        assertEquals(1, p2.getLastBuild().getAction(ParametersAction.class).getParameters().size());
        assertEquals("for p1", p1.getLastBuild().getAction(ParametersAction.class).getParameter("foo").getValue());
        assertEquals("for p2", p2.getLastBuild().getAction(ParametersAction.class).getParameter("foo").getValue());
    }

    @Issue("JENKINS-49573")
    @Test
    void noInnerClasses() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("key", "sensible-default")));
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("key", "value"))));
        assertThat(new XmlFile(Run.XSTREAM, new File(b.getRootDir(), "build.xml")).asString(), not(containsString("sensible-default")));
        assertEquals(Collections.emptyList(), logs.getMessages());
    }

    public static boolean hasParameterWithName(Iterable<ParameterValue> values, String name) {
        for (ParameterValue v : values) {
            if (v.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }


    public static class ParametersCheckBuilder extends Builder {

        private final boolean expectLegacyBehavior;

        ParametersCheckBuilder(boolean expectLegacyBehavior) {
            this.expectLegacyBehavior = expectLegacyBehavior;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            ParametersAction pa = build.getAction(ParametersAction.class);
            assertEquals("baz", pa.getParameter("foo").getValue(), "foo value expected changed");

            if (expectLegacyBehavior) {
                assertTrue(hasParameterWithName(pa.getParameters(), "undef"), "undef parameter is listed in getParameters");
                assertTrue(hasParameterWithName(pa, "undef"), "undef parameter is listed in iterator");
                assertTrue(build.getEnvironment(listener).containsKey("undef"), "undef in environment");
                assertTrue(build.getEnvironment(listener).containsKey("UNDEF"), "UNDEF in environment");
            } else {
                assertFalse(hasParameterWithName(pa.getParameters(), "undef"), "undef parameter is not listed in getParameters");
                assertFalse(hasParameterWithName(pa, "undef"), "undef parameter is not listed in iterator");
                assertFalse(build.getEnvironment(listener).containsKey("undef"), "undef not in environment");
                assertFalse(build.getEnvironment(listener).containsKey("UNDEF"), "UNDEF not in environment");
            }

            assertTrue(hasParameterWithName(pa.getAllParameters(), "undef"), "undef parameter is listed in getAllParameters");
            assertEquals("undef", pa.getParameter("undef").getValue(), "undef parameter direct access expected to work");

            return true;
        }
    }
}
