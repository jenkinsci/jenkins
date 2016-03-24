/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

package hudson.model;

import java.util.Locale;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.junit.Test;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;

public class ParametersDefinitionPropertyTest {

    private static final Logger logger = Logger.getLogger(Descriptor.class.getName());
    @BeforeClass
    public static void logging() {
        logger.setLevel(Level.ALL);
        Handler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
    }

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-31458")
    @Test
    public void customNewInstance() throws Exception {
        KrazyParameterDefinition kpd = new KrazyParameterDefinition("kpd", "desc", "KrAzY");
        FreeStyleProject p = r.createFreeStyleProject();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(kpd);
        p.addProperty(pdp);
        r.configRoundtrip((Item)p);
        pdp = p.getProperty(ParametersDefinitionProperty.class);
        kpd = (KrazyParameterDefinition) pdp.getParameterDefinition("kpd");
        assertEquals("desc", kpd.getDescription());
        assertEquals("krazy", kpd.field);
    }

    public static class KrazyParameterDefinition extends ParameterDefinition {

        public final String field;

        // not @DataBoundConstructor
        public KrazyParameterDefinition(String name, String description, String field) {
            super(name, description);
            this.field = field;
        }

        @Override
        public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ParameterValue createValue(StaplerRequest req) {
            throw new UnsupportedOperationException();
        }

        @TestExtension("customNewInstance")
        public static class DescriptorImpl extends ParameterDescriptor {

            @Override
            public ParameterDefinition newInstance(StaplerRequest req, JSONObject formData) throws FormException {
                return new KrazyParameterDefinition(formData.getString("name"), formData.getString("description"), formData.getString("field").toLowerCase(Locale.ENGLISH));
            }

        }

    }

}
