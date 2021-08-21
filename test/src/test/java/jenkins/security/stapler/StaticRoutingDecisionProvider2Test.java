/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
package jenkins.security.stapler;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Due to the fact we are using a @ClassRule for the other tests to improve performance, 
 * we cannot use @LocalData to test the loading of the whitelist as that annotation seem to not work with @ClassRule.
 */
@Issue("SECURITY-400")
@For(StaticRoutingDecisionProvider.class)
public class StaticRoutingDecisionProvider2Test {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    @LocalData("whitelist_empty")
    public void userControlledWhitelist_empty_Loading() throws Exception {
        StaticRoutingDecisionProvider wl = new StaticRoutingDecisionProvider();
        assertThat(
                wl.decide("public java.lang.Object jenkins.security.stapler.StaticRoutingDecisionProviderTest$ContentProvider.getObjectCustom()"),
                is(RoutingDecisionProvider.Decision.UNKNOWN)
        );
        assertThat(
                wl.decide("blabla"),
                is(RoutingDecisionProvider.Decision.UNKNOWN)
        );
    }
    
    @Test
    @LocalData("whitelist_monoline")
    public void userControlledWhitelist_monoline_Loading() throws Exception {
        StaticRoutingDecisionProvider wl = new StaticRoutingDecisionProvider();
        assertThat(
                wl.decide("method jenkins.security.stapler.StaticRoutingDecisionProviderTest$ContentProvider getObjectCustom"),
                is(RoutingDecisionProvider.Decision.ACCEPTED)
        );
        assertThat(
                wl.decide("blabla"),
                is(RoutingDecisionProvider.Decision.UNKNOWN)
        );
    }
    
    @Test
    @LocalData("whitelist_multiline")
    public void userControlledWhitelist_multiline_Loading() throws Exception {
        StaticRoutingDecisionProvider wl = new StaticRoutingDecisionProvider();
        assertThat(
                wl.decide("method jenkins.security.stapler.StaticRoutingDecisionProviderTest$ContentProvider getObjectCustom"),
                is(RoutingDecisionProvider.Decision.ACCEPTED)
        );
        assertThat(
                wl.decide("method jenkins.security.stapler.StaticRoutingDecisionProviderTest$ContentProvider getObjectCustom2"),
                is(RoutingDecisionProvider.Decision.ACCEPTED)
        );
        assertThat(
                wl.decide("blabla"),
                is(RoutingDecisionProvider.Decision.UNKNOWN)
        );
    }
    
    @Test
    @LocalData("comment_ignored")
    public void userControlledWhitelist_commentsAreIgnored() throws Exception {
        StaticRoutingDecisionProvider wl = new StaticRoutingDecisionProvider();
        assertThat(wl.decide("this line is not read"), is(RoutingDecisionProvider.Decision.UNKNOWN));
        assertThat(wl.decide("not-this-one"), is(RoutingDecisionProvider.Decision.UNKNOWN));
        assertThat(wl.decide("neither"), is(RoutingDecisionProvider.Decision.UNKNOWN));
        assertThat(wl.decide("finally-not"), is(RoutingDecisionProvider.Decision.UNKNOWN));
        
        assertThat(wl.decide("this-one-is"), is(RoutingDecisionProvider.Decision.ACCEPTED));
        assertThat(wl.decide("this-one-also"), is(RoutingDecisionProvider.Decision.ACCEPTED));
    }
    
    @Test
    @LocalData("whitelist_emptyline")
    public void userControlledWhitelist_emptyLinesAreIgnored() throws Exception {
        StaticRoutingDecisionProvider wl = new StaticRoutingDecisionProvider();
        assertThat(wl.decide("signature-1"), is(RoutingDecisionProvider.Decision.ACCEPTED));
        assertThat(wl.decide("signature-2"), is(RoutingDecisionProvider.Decision.ACCEPTED));
        assertThat(wl.decide("signature-3"), is(RoutingDecisionProvider.Decision.ACCEPTED));
        // neither the empty line or an exclamation mark followed by nothing or spaces are not considered
        assertThat(wl.decide(""), is(RoutingDecisionProvider.Decision.UNKNOWN));
    }
    
    @Test
    @LocalData("greylist_multiline")
    public void userControlledWhitelist_whiteAndBlack() throws Exception {
        StaticRoutingDecisionProvider wl = new StaticRoutingDecisionProvider();
        assertThat(wl.decide("signature-1-ok"), is(RoutingDecisionProvider.Decision.ACCEPTED));
        assertThat(wl.decide("signature-3-ok"), is(RoutingDecisionProvider.Decision.ACCEPTED));
        
        assertThat(wl.decide("signature-2-not-ok"), is(RoutingDecisionProvider.Decision.REJECTED));
        assertThat(wl.decide("signature-4-not-ok"), is(RoutingDecisionProvider.Decision.REJECTED));
        
        // the exclamation mark is not used
        assertThat(wl.decide("!signature-2-not-ok"), is(RoutingDecisionProvider.Decision.UNKNOWN));
    }
    
    @Test
    public void defaultList() throws Exception {
        StaticRoutingDecisionProvider wl = new StaticRoutingDecisionProvider();
        
        assertThat(
                wl.decide("method io.jenkins.blueocean.service.embedded.rest.AbstractRunImpl getLog"),
                is(RoutingDecisionProvider.Decision.ACCEPTED)
        );
        assertThat(
                wl.decide("method io.jenkins.blueocean.rest.impl.pipeline.PipelineNodeImpl getLog"),
                is(RoutingDecisionProvider.Decision.ACCEPTED)
        );
        assertThat(
                wl.decide("method io.jenkins.blueocean.rest.impl.pipeline.PipelineStepImpl getLog"),
                is(RoutingDecisionProvider.Decision.ACCEPTED)
        );
        
        assertThat(wl.decide("method jenkins.security.stapler.StaticRoutingDecisionProviderTest$ContentProvider getObjectCustom"),
                is(RoutingDecisionProvider.Decision.UNKNOWN)
        );
        assertThat(wl.decide("blabla"),
                is(RoutingDecisionProvider.Decision.UNKNOWN)
        );
    }
    
    @Test
    public void userControlledWhitelist_savedCorrectly() throws Exception {
        File whitelistUserControlledList = new File(j.jenkins.getRootDir(), "stapler-whitelist.txt");
        
        assertFalse(whitelistUserControlledList.exists());
        
        StaticRoutingDecisionProvider wl = new StaticRoutingDecisionProvider();
        
        assertFalse(whitelistUserControlledList.exists());
        
        assertThat(wl.decide("nothing"), is(RoutingDecisionProvider.Decision.UNKNOWN));
        
        wl.save();
        assertTrue(whitelistUserControlledList.exists());
        assertThat(FileUtils.readFileToString(whitelistUserControlledList), is(""));
        
        wl.add("white-1");
        
        assertThat(wl.decide("white-1"), is(RoutingDecisionProvider.Decision.ACCEPTED));
        
        assertTrue(whitelistUserControlledList.exists());
        assertThat(FileUtils.readFileToString(whitelistUserControlledList), containsString("white-1"));
        {
            StaticRoutingDecisionProvider temp = new StaticRoutingDecisionProvider();
            assertThat(temp.decide("white-1"), is(RoutingDecisionProvider.Decision.ACCEPTED));
        }
        
        wl.addBlacklistSignature("black-2");
        
        assertThat(wl.decide("white-1"), is(RoutingDecisionProvider.Decision.ACCEPTED));
        assertThat(wl.decide("black-2"), is(RoutingDecisionProvider.Decision.REJECTED));
        assertThat(FileUtils.readFileToString(whitelistUserControlledList), allOf(
                containsString("white-1"),
                containsString("!black-2")
        ));
        
        {
            StaticRoutingDecisionProvider temp = new StaticRoutingDecisionProvider();
            assertThat(temp.decide("white-1"), is(RoutingDecisionProvider.Decision.ACCEPTED));
            assertThat(temp.decide("black-2"), is(RoutingDecisionProvider.Decision.REJECTED));
        }
        
        wl.removeBlacklistSignature("black-2");
        
        assertThat(wl.decide("white-1"), is(RoutingDecisionProvider.Decision.ACCEPTED));
        assertThat(wl.decide("black-2"), is(RoutingDecisionProvider.Decision.UNKNOWN));
        assertThat(FileUtils.readFileToString(whitelistUserControlledList), allOf(
                containsString("white-1"),
                not(containsString("black-2"))
        ));
        
        {
            StaticRoutingDecisionProvider temp = new StaticRoutingDecisionProvider();
            assertThat(temp.decide("white-1"), is(RoutingDecisionProvider.Decision.ACCEPTED));
            assertThat(temp.decide("black-2"), is(RoutingDecisionProvider.Decision.UNKNOWN));
        }
        
        wl.remove("white-1");
        
        assertThat(wl.decide("white-1"), is(RoutingDecisionProvider.Decision.UNKNOWN));
        assertThat(wl.decide("black-2"), is(RoutingDecisionProvider.Decision.UNKNOWN));
        assertThat(FileUtils.readFileToString(whitelistUserControlledList), allOf(
                not(containsString("white-1")),
                not(containsString("black-2"))
        ));
        
        {
            StaticRoutingDecisionProvider temp = new StaticRoutingDecisionProvider();
            assertThat(temp.decide("white-1"), is(RoutingDecisionProvider.Decision.UNKNOWN));
            assertThat(temp.decide("black-2"), is(RoutingDecisionProvider.Decision.UNKNOWN));
        }
    }
}
