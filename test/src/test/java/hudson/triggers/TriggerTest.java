package hudson.triggers;

/*
 * The MIT License
 *
 * Copyright (c) 2016 Felix Belzunce Arcos
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

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.Item;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.ByteArrayInputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class TriggerTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Issue("JENKINS-36748")
    @Test
    public void testNoNPE() throws Exception {
        jenkinsRule.getInstance().createProjectFromXML("whatever", new ByteArrayInputStream(("<project>\n  <builders/>\n  <publishers/>\n  <buildWrappers/>\n" + triggersSection() + "</project>").getBytes()));
        final Calendar cal = new GregorianCalendar();
        Trigger.checkTriggers(cal);
    }

    private String triggersSection() {
        String tagname = MockTrigger.class.getName().replace("$", "_-");
        return "<triggers> \n <" + tagname + ">\n </" + tagname + ">\n  </triggers>\n";
    }

    public static class MockTrigger extends Trigger<Item> {
        @Extension
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        @DataBoundConstructor
        public MockTrigger(String cron) throws ANTLRException {
            super(cron);
        }

        @Override
        public DescriptorImpl getDescriptor() {
            return DESCRIPTOR;
        }

        @Override
        public Object readResolve() {
            return this;
        }

        public static class DescriptorImpl extends TriggerDescriptor {
            @Override public boolean isApplicable(Item item) {
                return true;
            }

            public DescriptorImpl() {
                load();
                save();
            }
        }
    }
}


