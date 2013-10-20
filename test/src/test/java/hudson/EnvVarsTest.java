/*
 * The MIT License
 * 
 * Copyright (c) 2013 IKEDA Yasuyuki
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson;

import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.slaves.DumbSlave;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.HudsonTestCase;

public class EnvVarsTest extends HudsonTestCase {
    private void resetEnvVarsCache() throws Exception {
        Field f = EnvVars.class.getDeclaredField("shouldNotCaseSensitive");
        f.setAccessible(true);
        f.set(null, null);
        f.setAccessible(false);
    }
    
    public void testCaseSensitiveOnMaster() throws Exception {
        assertNull(System.getProperty(EnvVars.PROP_CASE_INSENSITIVE));
        resetEnvVarsCache();
        
        FreeStyleProject p = createFreeStyleProject();
        CaptureEnvironmentBuilder ceb = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(ceb);
        assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserCause(), Arrays.asList(
                new ParametersAction(
                        new StringParameterValue("var1", "value1"),
                        new StringParameterValue("Var1", "value2")
                )
        )));
        assertEquals("value1", ceb.getEnvVars().get("var1"));
        assertEquals("value2", ceb.getEnvVars().get("Var1"));
        assertNull(ceb.getEnvVars().get("VAR1"));
    }
    
    public void testCaseSensitiveOnSlave() throws Exception {
        assertNull(System.getProperty(EnvVars.PROP_CASE_INSENSITIVE));
        resetEnvVarsCache();
        
        DumbSlave slave = createOnlineSlave();
        FreeStyleProject p = createFreeStyleProject();
        p.setAssignedNode(slave);
        CaptureEnvironmentBuilder ceb = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(ceb);
        assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserCause(), Arrays.asList(
                new ParametersAction(
                        new StringParameterValue("var1", "value1"),
                        new StringParameterValue("Var1", "value2")
                )
        )));
        assertEquals("value1", ceb.getEnvVars().get("var1"));
        assertEquals("value2", ceb.getEnvVars().get("Var1"));
        assertNull(ceb.getEnvVars().get("VAR1"));
    }
    
    public void testCaseInsensitiveOnMaster() throws Exception {
        assertNull(System.getProperty(EnvVars.PROP_CASE_INSENSITIVE));
        try {
            System.setProperty(EnvVars.PROP_CASE_INSENSITIVE, "true");
            resetEnvVarsCache();
            
            FreeStyleProject p = createFreeStyleProject();
            CaptureEnvironmentBuilder ceb = new CaptureEnvironmentBuilder();
            p.getBuildersList().add(ceb);
            assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserCause(), Arrays.asList(
                    new ParametersAction(
                            new StringParameterValue("var1", "value1"),
                            new StringParameterValue("Var1", "value2")
                    )
            )));
            String expected = ceb.getEnvVars().get("var1");
            assertEquals(expected, ceb.getEnvVars().get("Var1"));
            assertEquals(expected, ceb.getEnvVars().get("VAR1"));
        } finally {
            System.getProperties().remove(EnvVars.PROP_CASE_INSENSITIVE);
        }
    }
    
    public void testCaseInsensitiveOnSlave() throws Exception {
        assertNull(System.getProperty(EnvVars.PROP_CASE_INSENSITIVE));
        try {
            System.setProperty(EnvVars.PROP_CASE_INSENSITIVE, "true");
            resetEnvVarsCache();
            
            DumbSlave slave = createOnlineSlave();
            FreeStyleProject p = createFreeStyleProject();
            p.setAssignedNode(slave);
            CaptureEnvironmentBuilder ceb = new CaptureEnvironmentBuilder();
            p.getBuildersList().add(ceb);
            assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserCause(), Arrays.asList(
                    new ParametersAction(
                            new StringParameterValue("var1", "value1"),
                            new StringParameterValue("Var1", "value2")
                    )
            )));
            String expected = ceb.getEnvVars().get("var1");
            assertEquals(expected, ceb.getEnvVars().get("Var1"));
            assertEquals(expected, ceb.getEnvVars().get("VAR1"));
        } finally {
            System.getProperties().remove(EnvVars.PROP_CASE_INSENSITIVE);
        }
    }
    
}
