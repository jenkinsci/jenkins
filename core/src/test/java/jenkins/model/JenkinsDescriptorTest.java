/*
 * The MIT License
 *
 * Copyright (c) 2014 Daniel Beck
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
package jenkins.model;

import hudson.util.FormValidation;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
public class JenkinsDescriptorTest {

    @Mock
    Jenkins jenkins;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    private FormValidation checkBuildsDir(String buildsDir) {
        return Jenkins.DescriptorImpl.INSTANCE.doCheckRawBuildsDir(buildsDir);
    }

    private boolean isOK(String buildsDir) {
        return checkBuildsDir(buildsDir).kind == FormValidation.Kind.OK;
    }

    private boolean isError(String buildsDir) {
        return checkBuildsDir(buildsDir).kind == FormValidation.Kind.ERROR;
    }

    @Test
    @PrepareForTest(Jenkins.class)
    public void testBuildDirValidation() {
        PowerMockito.mockStatic(Jenkins.class);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);
        PowerMockito.when(Jenkins.expandVariablesForDirectory(anyString(), anyString(), anyString())).thenCallRealMethod();
        when(jenkins.getRootDir()).thenReturn(new File(".").getAbsoluteFile());

        assertTrue(isOK("$JENKINS_HOME/foo/$ITEM_FULL_NAME"));
        assertTrue(isOK("${ITEM_ROOTDIR}/builds"));

        assertTrue(isError("$JENKINS_HOME"));
        assertTrue(isError("$JENKINS_HOME/builds"));
        assertTrue(isError("$ITEM_FULL_NAME"));
        assertTrue(isError("/path/to/builds"));
        assertTrue(isError("/invalid/$JENKINS_HOME"));
        assertTrue(isError("relative/ITEM_FULL_NAME"));

        // TODO test literal absolute paths (e.g. /foo/$ITEM_FULL_NAME), ITEM_FULLNAME
    }

}