package hudson.model;

import hudson.EnvVars;
import org.junit.Test;

import javax.annotation.CheckForNull;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class EnvironmentContributingActionTest {
    class OverrideRun extends InvisibleAction implements EnvironmentContributingAction {
        private boolean wasCalled = false;

        @Override
        public void buildEnvVars(Run<?, ?> run, EnvVars env, @CheckForNull Node node) {
            wasCalled = true;
        }

        boolean wasNewMethodCalled() {
            return wasCalled;
        }
    }

    class OverrideAbstractBuild extends InvisibleAction implements EnvironmentContributingAction {
        private boolean wasCalled = false;

        @Override
        @SuppressWarnings("deprecation")
        public void buildEnvVars(AbstractBuild<?, ?> abstractBuild, EnvVars envVars) {
            wasCalled = true;
        }

        boolean wasDeprecatedMethodCalled() {
            return wasCalled;
        }
    }

    class OverrideBoth extends InvisibleAction implements EnvironmentContributingAction {
        private boolean wasCalledAstractBuild = false;
        private boolean wasCalledRun = false;

        @SuppressWarnings("deprecation")
        @Override
        public void buildEnvVars(AbstractBuild<?, ?> abstractBuild, EnvVars envVars) {
            wasCalledAstractBuild = true;
        }

        @Override
        public void buildEnvVars(Run<?, ?> run, EnvVars env, @CheckForNull Node node) {
            wasCalledRun = true;
        }

        boolean wasDeprecatedMethodCalled() {
            return wasCalledAstractBuild;
        }

        boolean wasRunCalled() {
            return wasCalledRun;
        }
    }

    private final EnvVars envVars = mock(EnvVars.class);

    @Test
    public void testOverrideRunMethodAndCallNewMethod() throws Exception {
        Run run = mock(Run.class);
        Node node = mock(Node.class);

        OverrideRun overrideRun = new OverrideRun();
        overrideRun.buildEnvVars(run, envVars, node);

        assertTrue(overrideRun.wasNewMethodCalled());
    }

    /**
     * If only non-deprecated method was overridden it would be executed even if someone would call deprecated method.
     * @throws Exception if happens.
     */
    @Test
    @SuppressWarnings("deprecation")
    public void testOverrideRunMethodAndCallDeprecatedMethod() throws Exception {
        AbstractBuild abstractBuild = mock(AbstractBuild.class);
        when(abstractBuild.getBuiltOn()).thenReturn(mock(Node.class));

        OverrideRun overrideRun = new OverrideRun();
        overrideRun.buildEnvVars(abstractBuild, envVars);

        assertTrue(overrideRun.wasNewMethodCalled());
    }

    /**
     * {@link AbstractBuild} should work as before.
     * @throws Exception if happens.
     */
    @Test
    public void testOverrideAbstractBuildAndCallNewMethodWithAbstractBuild() throws Exception {
        AbstractBuild abstractBuild = mock(AbstractBuild.class);
        Node node = mock(Node.class);

        OverrideAbstractBuild action = new OverrideAbstractBuild();
        action.buildEnvVars(abstractBuild, envVars, node);

        assertTrue(action.wasDeprecatedMethodCalled());
    }

    /**
     * {@link Run} should not execute method that was overridden for {@link AbstractBuild}.
     * @throws Exception if happens.
     */
    @Test
    public void testOverrideAbstractBuildAndCallNewMethodWithRun() throws Exception {
        Run run = mock(Run.class);
        Node node = mock(Node.class);

        OverrideAbstractBuild action = new OverrideAbstractBuild();
        action.buildEnvVars(run, envVars, node);

        assertFalse(action.wasDeprecatedMethodCalled());
    }

    /**
     * If someone wants to use overridden deprecated method, it would still work.
     * @throws Exception if happens.
     */
    @Test
    public void testOverrideAbstractBuildAndCallDeprecatedMethod() throws Exception {
        AbstractBuild abstractBuild = mock(AbstractBuild.class);

        OverrideAbstractBuild overrideRun = new OverrideAbstractBuild();
        overrideRun.buildEnvVars(abstractBuild, envVars);

        assertTrue(overrideRun.wasDeprecatedMethodCalled());
    }

    @Test
    public void testOverrideBothAndCallNewMethod() throws Exception {
        Run run = mock(Run.class);
        Node node = mock(Node.class);

        OverrideBoth overrideRun = new OverrideBoth();
        overrideRun.buildEnvVars(run, envVars, node);

        assertTrue(overrideRun.wasRunCalled());
    }

    @Test
    public void testOverrideBothAndCallDeprecatedMethod() throws Exception {
        AbstractBuild abstractBuild = mock(AbstractBuild.class);

        OverrideBoth overrideRun = new OverrideBoth();
        overrideRun.buildEnvVars(abstractBuild, envVars);

        assertTrue(overrideRun.wasDeprecatedMethodCalled());
    }
}