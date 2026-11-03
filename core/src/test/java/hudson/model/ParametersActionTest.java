package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.EnvVars;
import hudson.model.queue.SubTask;
import hudson.tasks.BuildWrapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class ParametersActionTest {

    private ParametersAction baseParamsAB;
    private StringParameterValue baseA;

    @BeforeEach
    void setUp() {
        baseA = new StringParameterValue("a", "base-a");
        StringParameterValue baseB = new StringParameterValue("b", "base-b");
        baseParamsAB = new ParametersAction(baseA, baseB);
    }

    @Test
    void mergeShouldOverrideParameters() {
        StringParameterValue overrideB = new StringParameterValue("b", "override-b");
        ParametersAction extraParams = new ParametersAction(overrideB);

        ParametersAction params = baseParamsAB.merge(extraParams);

        StringParameterValue a = (StringParameterValue) params.getParameter("a");
        StringParameterValue b = (StringParameterValue) params.getParameter("b");
        assertEquals(baseA, a);
        assertEquals(overrideB, b);
    }

    @Test
    void mergeShouldCombineDisparateParameters() {
        StringParameterValue overrideB = new StringParameterValue("b", "override-b");
        ParametersAction extraParams = new ParametersAction(overrideB);

        ParametersAction params = baseParamsAB.merge(extraParams);

        StringParameterValue a = (StringParameterValue) params.getParameter("a");
        StringParameterValue b = (StringParameterValue) params.getParameter("b");
        assertEquals(baseA, a);
        assertEquals(overrideB, b);
    }

    @Test
    void mergeShouldHandleEmptyOverrides() {
        ParametersAction params = baseParamsAB.merge(new ParametersAction());

        StringParameterValue a = (StringParameterValue) params.getParameter("a");
        assertEquals(baseA, a);
    }

    @Test
    void mergeShouldHandleNullOverrides() {
        ParametersAction params = baseParamsAB.merge(null);

        StringParameterValue a = (StringParameterValue) params.getParameter("a");
        assertEquals(baseA, a);
    }

    @Test
    void mergeShouldReturnNewInstanceWithOverride() {
        StringParameterValue overrideA = new StringParameterValue("a", "override-a");
        ParametersAction overrideParams = new ParametersAction(overrideA);

        ParametersAction params = baseParamsAB.merge(overrideParams);

        assertNotSame(baseParamsAB, params);
    }

    @Test
    void createUpdatedShouldReturnNewInstanceWithNullOverride() {
        ParametersAction params = baseParamsAB.createUpdated(null);

        assertNotSame(baseParamsAB, params);
    }

    @Test
    @Issue("JENKINS-15094")
    void checkNullParameterValues() {
        SubTask subtask = mock(SubTask.class);
        Build build = mock(Build.class);

        // Prepare parameters Action
        StringParameterValue A = new StringParameterValue("A", "foo");
        StringParameterValue B = new StringParameterValue("B", "bar");
        ParametersAction parametersAction = new ParametersAction(A, null, B);
        ParametersAction parametersAction2 = new ParametersAction(A, null);

        // Non existent parameter
        assertNull(parametersAction.getParameter("C"));
        assertNull(parametersAction.getAssignedLabel(subtask));

        // Interaction with build
        EnvVars vars = new EnvVars();
        parametersAction.buildEnvironment(build, vars);
        assertEquals(2, vars.size());
        parametersAction.createVariableResolver(build);

        List<BuildWrapper> wrappers = new ArrayList<>();
        parametersAction.createBuildWrappers(build, wrappers);
        assertEquals(0, wrappers.size());

        // Merges and overrides
        assertEquals(3, parametersAction.createUpdated(parametersAction2.getParameters()).getParameters().size());
        assertEquals(3, parametersAction.merge(parametersAction2).getParameters().size());
    }

    @Test
    @Issue("JENKINS-44947")
    void getParametersShouldNotThrowOnNullParameterValueOnceAttached() {
        StringParameterValue a = new StringParameterValue("a", "foo");
        ParametersAction parametersAction = new ParametersAction(a, null);

        ParametersDefinitionProperty property = mock(ParametersDefinitionProperty.class);
        when(property.getParameterDefinitionNames()).thenReturn(List.of("a"));
        Job job = mock(Job.class);
        when(job.getProperty(ParametersDefinitionProperty.class)).thenReturn(property);
        Run run = mock(Run.class);
        when(run.getParent()).thenReturn(job);

        parametersAction.onAttached(run);

        assertEquals(1, parametersAction.getParameters().size());
    }
}
