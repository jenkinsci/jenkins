/*
 * The MIT License
 * 
 * Copyright 2010 Jesse Glick.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package hudson.tasks.junit;

import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.tasks.test.AbstractTestResultAction;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import junit.framework.TestCase;

import org.jvnet.localizer.LocaleProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.stub;

public class CaseResultTest extends TestCase {

    public CaseResultTest(String name) {
        super(name);
    }

    // @Bug(6824)
    public void testLocalizationOfStatus() throws Exception {
        LocaleProvider old = LocaleProvider.getProvider();
        try {
            final AtomicReference<Locale> locale = new AtomicReference<Locale>();
            LocaleProvider.setProvider(new LocaleProvider() {
                public @Override
                Locale get() {
                    return locale.get();
                }
            });
            locale.set(Locale.GERMANY);
            assertEquals("Erfolg", CaseResult.Status.PASSED.getMessage());
            locale.set(Locale.US);
            assertEquals("Passed", CaseResult.Status.PASSED.getMessage());
        } finally {
            LocaleProvider.setProvider(old);
        }
    }

    // @Bug(15634)
    public void testCalculateFailureAgeAfterAbort() throws Exception {

        int buildsNumber = 7;
        String resultsPath = "JENKINS-15634" + File.separator;
        
        AbstractBuild[] buildArray = new AbstractBuild[buildsNumber];
        SuiteResult[] suiteResultArray = new SuiteResult[buildsNumber];
        
        for (int i = 0; i < buildArray.length; i++) {
            
            // Mocking builds
            buildArray[i] = mock(AbstractBuild.class);
            stub(buildArray[i].getPreviousBuild()).toReturn((i != 0) ? buildArray[i - 1] : null);
            stub(buildArray[i].getNumber()).toReturn(i);
            
            if (i == 2 || i == 4 || i == 5) {
                stub(buildArray[i].getResult()).toReturn(Result.ABORTED);
            } else {
                // Parsing results
                File file = getDataFile(resultsPath + "junit-report-15634-" + i + ".xml");
                TestResult testResult = new TestResult();
                testResult.parse(file);
                
                suiteResultArray[i] = ((List<SuiteResult>) testResult.getSuites()).get(0);
                suiteResultArray[i].setParent(testResult);
                
                TestResultActionImpostor testResultAction = new TestResultActionImpostor(buildArray[i]);
                testResultAction.setResult(testResult);
                
                stub(buildArray[i].getResult()).toReturn(Result.UNSTABLE);
                stub(buildArray[i].getTestResultAction()).toReturn(testResultAction);
            }
        }

        assertEquals(3, suiteResultArray[3].getCase("testCase").getAge());
        assertEquals(6, suiteResultArray[6].getCase("testCase").getAge());
    }

    private File getDataFile(String name) throws URISyntaxException {
        return new File(SuiteResultTest.class.getResource(name).toURI());
    }

    private final class TestResultActionImpostor extends AbstractTestResultAction {
        
        private TestResult testResult = null;

        public TestResultActionImpostor(AbstractBuild owner) {
            super(owner);
        }
        
        public TestResult getResult() {
            return testResult;
        }

        public void setResult(TestResult testResult) {
            this.testResult = testResult;
            testResult.setParentAction(this);
        }

        public int getTotalCount() {
            return 0;
        }

        public int getFailCount() {
            return 0;
        }
    }
}
