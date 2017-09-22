package hudson.model;

import hudson.EnvVars;
import org.junit.Test;


import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class EnvironmentContributingActionTest {
    class OverrideRun extends InvisibleAction implements EnvironmentContributingAction {
        private boolean wasCalled = false;

        @Override
        public void buildEnvironment(Run<?, ?> run, EnvVars env) {
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
        public void buildEnvironment(Run<?, ?> run, EnvVars env) {
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
        overrideRun.buildEnvironment(run, envVars);

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

        OverrideAbstractBuild action = new OverrideAbstractBuild();
        action.buildEnvironment(abstractBuild, envVars);

        assertTrue(action.wasDeprecatedMethodCalled());
    }

    /**
     * {@link Run} should not execute method that was overridden for {@link AbstractBuild}.
     * @throws Exception if happens.
     */
    @Test
    public void testOverrideAbstractBuildAndCallNewMethodWithRun() throws Exception {
        Run run = mock(Run.class);

        OverrideAbstractBuild action = new OverrideAbstractBuild();
        action.buildEnvironment(run, envVars);

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

        OverrideBoth overrideRun = new OverrideBoth();
        overrideRun.buildEnvironment(run, envVars);

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