package hudson.model;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class ParametersActionTest {

    private ParametersAction baseParamsAB;
    private StringParameterValue baseA;

    @Before
    public void setUp() {
        baseA = new StringParameterValue("a", "base-a");
        StringParameterValue baseB = new StringParameterValue("b", "base-b");
        baseParamsAB = new ParametersAction(baseA, baseB);
    }

    @Test
    public void mergeShouldOverrideParameters() {
        StringParameterValue overrideB = new StringParameterValue("b", "override-b");
        ParametersAction extraParams = new ParametersAction(overrideB);

        ParametersAction params = baseParamsAB.merge(extraParams);

        StringParameterValue a = (StringParameterValue) params.getParameter("a");
        StringParameterValue b = (StringParameterValue) params.getParameter("b");
        assertEquals(baseA, a);
        assertEquals(overrideB, b);
    }

    @Test
    public void mergeShouldCombineDisparateParameters() {
        StringParameterValue overrideB = new StringParameterValue("b", "override-b");
        ParametersAction extraParams = new ParametersAction(overrideB);

        ParametersAction params = baseParamsAB.merge(extraParams);

        StringParameterValue a = (StringParameterValue) params.getParameter("a");
        StringParameterValue b = (StringParameterValue) params.getParameter("b");
        assertEquals(baseA, a);
        assertEquals(overrideB, b);
    }

    @Test
    public void mergeShouldHandleEmptyOverrides() {
        ParametersAction params = baseParamsAB.merge(new ParametersAction());

        StringParameterValue a = (StringParameterValue) params.getParameter("a");
        assertEquals(baseA, a);
    }

    @Test
    public void mergeShouldHandleNullOverrides() {
        ParametersAction params = baseParamsAB.merge(null);

        StringParameterValue a = (StringParameterValue) params.getParameter("a");
        assertEquals(baseA, a);
    }

    @Test
    public void mergeShouldReturnNewInstanceWithOverride() {
        StringParameterValue overrideA = new StringParameterValue("a", "override-a");
        ParametersAction overrideParams = new ParametersAction(overrideA);

        ParametersAction params = baseParamsAB.merge(overrideParams);

        assertNotSame(baseParamsAB, params);
    }

    @Test
    public void createUpdatedShouldReturnNewInstanceWithNullOverride() {
        ParametersAction params = baseParamsAB.createUpdated(null);

        assertNotSame(baseParamsAB, params);
    }
}
