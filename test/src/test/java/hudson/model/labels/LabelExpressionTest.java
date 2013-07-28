/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.model.labels;

import antlr.ANTLRException;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.FreeStyleProject.DescriptorImpl;
import hudson.model.Label;
import hudson.model.Node.Mode;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SequenceLock;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * @author Kohsuke Kawaguchi
 */
public class LabelExpressionTest extends HudsonTestCase {
    /**
     * Verifies the queueing behavior in the presence of the expression.
     */
    public void testQueueBehavior() throws Exception {
        DumbSlave w32 = createSlave("win 32bit",null);
        DumbSlave w64 = createSlave("win 64bit",null);
        createSlave("linux 32bit",null);

        final SequenceLock seq = new SequenceLock();

        FreeStyleProject p1 = createFreeStyleProject();
        p1.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                seq.phase(0); // first, make sure the w32 slave is occupied
                seq.phase(2);
                seq.done();
                return true;
            }
        });
        p1.setAssignedLabel(jenkins.getLabel("win && 32bit"));

        FreeStyleProject p2 = createFreeStyleProject();
        p2.setAssignedLabel(jenkins.getLabel("win && 32bit"));

        FreeStyleProject p3 = createFreeStyleProject();
        p3.setAssignedLabel(jenkins.getLabel("win"));

        Future<FreeStyleBuild> f1 = p1.scheduleBuild2(0);

        seq.phase(1); // we schedule p2 build after w32 slave is occupied
        Future<FreeStyleBuild> f2 = p2.scheduleBuild2(0);

        Thread.sleep(1000); // time window to ensure queue has tried to assign f2 build

        // p3 is tied to 'win', so even though p1 is busy, this should still go ahead and complete
        FreeStyleBuild b3 = assertBuildStatusSuccess(p3.scheduleBuild2(0));
        assertSame(w64,b3.getBuiltOn());

        seq.phase(3);   // once we confirm that p3 build is over, we let p1 proceed

        // p1 should have been built on w32
        FreeStyleBuild b1 = assertBuildStatusSuccess(f1);
        assertSame(w32,b1.getBuiltOn());

        // and so is p2
        FreeStyleBuild b2 = assertBuildStatusSuccess(f2);
        assertSame(w32,b2.getBuiltOn());
    }

    /**
     * Push the build around to different nodes via the assignment
     * to make sure it gets where we need it to.
     */
    public void testQueueBehavior2() throws Exception {
        DumbSlave s = createSlave("win",null);

        FreeStyleProject p = createFreeStyleProject();

        p.setAssignedLabel(jenkins.getLabel("!win"));
        FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertSame(jenkins,b.getBuiltOn());

        p.setAssignedLabel(jenkins.getLabel("win"));
        b = assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertSame(s,b.getBuiltOn());

        p.setAssignedLabel(jenkins.getLabel("!win"));
        b = assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertSame(jenkins,b.getBuiltOn());
    }

    /**
     * Make sure we can reset the label of an existing slave.
     */
    public void testSetLabelString() throws Exception {
        DumbSlave s = createSlave("foo","",null);

        assertSame(s.getLabelString(), "");
        
        s.setLabelString("bar");

        assertSame(s.getLabelString(), "bar");

    }

    /**
     * Tests the expression parser.
     */
    public void testParser() throws Exception {
        parseAndVerify("foo", "foo");
        parseAndVerify("32bit.dot", "32bit.dot");
        parseAndVerify("foo||bar", "foo || bar");

        // user-given parenthesis is preserved
        parseAndVerify("foo||bar&&zot", "foo||bar&&zot");
        parseAndVerify("foo||(bar&&zot)", "foo||(bar&&zot)");

        parseAndVerify("(foo||bar)&&zot", "(foo||bar)&&zot");
        parseAndVerify("foo->bar", "foo ->\tbar");
        parseAndVerify("!foo<->bar", "!foo <-> bar");
    }

    @Bug(8537)
    public void testParser2() throws Exception {
        parseAndVerify("aaa&&bbb&&ccc","aaa&&bbb&&ccc");
    }

    private void parseAndVerify(String expected, String expr) throws ANTLRException {
        assertEquals(expected, LabelExpression.parseExpression(expr).getName());
    }

    public void testParserError() throws Exception {
        parseShouldFail("foo bar");
        parseShouldFail("foo (bar)");
    }

    public void testLaxParsing() {
        // this should parse as an atom
        LabelAtom l = (LabelAtom) jenkins.getLabel("lucene.zones.apache.org (Solaris 10)");
        assertEquals(l.getName(),"lucene.zones.apache.org (Solaris 10)");
        assertEquals(l.getExpression(),"\"lucene.zones.apache.org (Solaris 10)\"");
    }

    public void testDataCompatibilityWithHostNameWithWhitespace() throws Exception {
        DumbSlave slave = new DumbSlave("abc def (xyz) : test", "dummy",
                createTmpDir().getPath(), "1", Mode.NORMAL, "", createComputerLauncher(null), RetentionStrategy.NOOP, Collections.EMPTY_LIST);
        jenkins.addNode(slave);


        FreeStyleProject p = createFreeStyleProject();
        p.setAssignedLabel(jenkins.getLabel("abc def"));
        assertEquals("abc def",p.getAssignedLabel().getName());
        assertEquals("\"abc def\"",p.getAssignedLabel().getExpression());

        // expression should be persisted, not the name
        Field f = AbstractProject.class.getDeclaredField("assignedNode");
        f.setAccessible(true);
        assertEquals("\"abc def\"",f.get(p));

        // but if the name is set, we'd still like to parse it
        f.set(p,"a:b c");
        assertEquals("a:b c",p.getAssignedLabel().getName());
    }

    public void testQuote() {
        Label l = jenkins.getLabel("\"abc\\\\\\\"def\"");
        assertEquals("abc\\\"def",l.getName());
        
        l = jenkins.getLabel("label1||label2"); // create label expression
        l = jenkins.getLabel("\"label1||label2\"");
        assertEquals("label1||label2",l.getName());
    }

    /**
     * The name should have parenthesis at the right place to preserve the tree structure.
     */
    public void testComposite() {
        LabelAtom x = jenkins.getLabelAtom("x");
        assertEquals("!!x",x.not().not().getName());
        assertEquals("(x||x)&&x",x.or(x).and(x).getName());
        assertEquals("x&&x||x",x.and(x).or(x).getName());
    }

    public void testDash() {
        jenkins.getLabelAtom("solaris-x86");
    }

    private void parseShouldFail(String expr) {
        try {
            LabelExpression.parseExpression(expr);
            fail(expr + " should fail to parse");
        } catch (ANTLRException e) {
            // expected
        }
    }

    public void testFormValidation() throws Exception {
        executeOnServer(new Callable<Object>() {
            public Object call() throws Exception {
                DescriptorImpl d = jenkins.getDescriptorByType(DescriptorImpl.class);

                Label l = jenkins.getLabel("foo");
                DumbSlave s = createSlave(l);
                String msg = d.doCheckAssignedLabelString("goo").renderHtml();
                assertTrue(msg.contains("foo"));
                assertTrue(msg.contains("goo"));

                msg = d.doCheckAssignedLabelString("master && goo").renderHtml();
                assertTrue(msg.contains("foo"));
                assertTrue(msg.contains("goo"));
                return null;
            }
        });
    }
}
