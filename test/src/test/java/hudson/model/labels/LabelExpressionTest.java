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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import antlr.ANTLRException;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SequenceLock;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class LabelExpressionTest {

    @TempDir
    private File tempFolder;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Verifies the queueing behavior in the presence of the expression.
     */
    @Test
    void queueBehavior1() throws Exception {
        DumbSlave w32 = j.createSlave("win 32bit", null);
        DumbSlave w64 = j.createSlave("win 64bit", null);
        j.createSlave("linux 32bit", null);

        final SequenceLock seq = new SequenceLock();

        FreeStyleProject p1 = j.createFreeStyleProject();
        p1.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
                seq.phase(0); // first, make sure the w32 agent is occupied
                seq.phase(2);
                seq.done();
                return true;
            }
        });
        p1.setAssignedLabel(j.jenkins.getLabel("win && 32bit"));

        FreeStyleProject p2 = j.createFreeStyleProject();
        p2.setAssignedLabel(j.jenkins.getLabel("win && 32bit"));

        FreeStyleProject p3 = j.createFreeStyleProject();
        p3.setAssignedLabel(j.jenkins.getLabel("win"));

        Future<FreeStyleBuild> f1 = p1.scheduleBuild2(0);

        seq.phase(1); // we schedule p2 build after w32 agent is occupied
        Future<FreeStyleBuild> f2 = p2.scheduleBuild2(0);

        Thread.sleep(1000); // time window to ensure queue has tried to assign f2 build

        // p3 is tied to 'win', so even though p1 is busy, this should still go ahead and complete
        FreeStyleBuild b3 = j.buildAndAssertSuccess(p3);
        assertSame(w64, b3.getBuiltOn());

        seq.phase(3);   // once we confirm that p3 build is over, we let p1 proceed

        // p1 should have been built on w32
        FreeStyleBuild b1 = j.assertBuildStatusSuccess(f1);
        assertSame(w32, b1.getBuiltOn());

        // and so is p2
        FreeStyleBuild b2 = j.assertBuildStatusSuccess(f2);
        assertSame(w32, b2.getBuiltOn());
    }

    /**
     * Push the build around to different nodes via the assignment
     * to make sure it gets where we need it to.
     */
    @Test
    void queueBehavior2() throws Exception {
        DumbSlave s = j.createSlave("win", null);

        FreeStyleProject p = j.createFreeStyleProject();

        p.setAssignedLabel(j.jenkins.getLabel("!win"));
        FreeStyleBuild b = j.buildAndAssertSuccess(p);
        assertSame(j.jenkins, b.getBuiltOn());

        p.setAssignedLabel(j.jenkins.getLabel("win"));
        b = j.buildAndAssertSuccess(p);
        assertSame(s, b.getBuiltOn());

        p.setAssignedLabel(j.jenkins.getLabel("!win"));
        b = j.buildAndAssertSuccess(p);
        assertSame(j.jenkins, b.getBuiltOn());
    }

    /**
     * Make sure we can reset the label of an existing agent.
     */
    @Test
    void setLabelString() throws Exception {
        DumbSlave s = j.createSlave("foo", "", null);

        assertSame("", s.getLabelString());

        s.setLabelString("bar");

        assertSame("bar", s.getLabelString());
    }

    /**
     * Tests the expression parser.
     */
    @Test
    void parser1() {
        parseAndVerify("foo", "foo");
        parseAndVerify("32bit.dot", "32bit.dot");
        parseAndVerify("foo||bar", "foo || bar");

        // user-given parenthesis is preserved
        parseAndVerify("foo||bar&&zot", "foo||bar&&zot");
        parseAndVerify("foo||(bar&&zot)", "foo||(bar&&zot)");

        parseAndVerify("(foo||bar)&&zot", "(foo||bar)&&zot");
        parseAndVerify("(foo||bar)&&zot", "  ( foo || bar )  && zot");
        parseAndVerify("foo->bar", "foo ->\tbar");
        parseAndVerify("foo->bar", "foo -> bar");
        parseAndVerify("foo->bar", "   foo \t\t ->   bar \t ");
        parseAndVerify("!foo<->bar", "!foo <-> bar");
    }

    @Issue("JENKINS-8537")
    @Test
    void parser2() {
        parseAndVerify("aaa&&bbb&&ccc", "aaa&&bbb&&ccc");
    }

    private void parseAndVerify(String expected, String expr) {
        assertEquals(expected, Label.parseExpression(expr).getName());
    }

    @Test
    void parserError() {
        parseShouldFail("foo bar", "line 1:4: extraneous input 'bar' expecting <EOF>");
        parseShouldFail("foo (bar)", "line 1:4: mismatched input '(' expecting {<EOF>, '&&', '||', '->', '<->'}");
        parseShouldFail("foo(bar)", "line 1:3: mismatched input '(' expecting {<EOF>, '&&', '||', '->', '<->'}");
        parseShouldFail("a <- b", "line 1:2: token recognition error at: '<- '");
        parseShouldFail("a -< b", "line 1:3: token recognition error at: '< '");
        parseShouldFail("a - b", "line 1:2: mismatched input '-' expecting {<EOF>, '&&', '||', '->', '<->'}");
        parseShouldFail("->", "line 1:0: mismatched input '->' expecting {'!', '(', ATOM, STRINGLITERAL}");
        parseShouldFail("-<", "line 1:1: token recognition error at: '<'");
        parseShouldFail("-!", "line 1:1: extraneous input '!' expecting <EOF>");
    }

    @Test
    void laxParsing() {
        // this should parse as an atom
        LabelAtom l = (LabelAtom) j.jenkins.getLabel("lucene.zones.apache.org (Solaris 10)");
        assertEquals("lucene.zones.apache.org (Solaris 10)", l.getName());
        assertEquals("\"lucene.zones.apache.org (Solaris 10)\"", l.getExpression());
    }

    @Test
    void dataCompatibilityWithHostNameWithWhitespace() throws Exception {
        assumeFalse(Functions.isWindows(), "Windows can't have paths with colons, skipping");
        DumbSlave slave = new DumbSlave("abc def (xyz) test", newFolder(tempFolder, "junit").getPath(), j.createComputerLauncher(null));
        slave.setRetentionStrategy(RetentionStrategy.NOOP);
        slave.setNodeDescription("dummy");
        slave.setNodeProperties(Collections.emptyList());
        j.jenkins.addNode(slave);


        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedLabel(j.jenkins.getLabel("abc def"));
        assertEquals("abc def", p.getAssignedLabel().getName());
        assertEquals("\"abc def\"", p.getAssignedLabel().getExpression());

        // expression should be persisted, not the name
        Field f = AbstractProject.class.getDeclaredField("assignedNode");
        f.setAccessible(true);
        assertEquals("\"abc def\"", f.get(p));

        // but if the name is set, we'd still like to parse it
        f.set(p, "a:b c");
        assertEquals("a:b c", p.getAssignedLabel().getName());
    }

    @Test
    void quote() {
        Label l = j.jenkins.getLabel("\"abc\\\\\\\"def\"");
        assertEquals("abc\\\"def", l.getName());

        l = j.jenkins.getLabel("label1||label2"); // create label expression
        l = j.jenkins.getLabel("\"label1||label2\"");
        assertEquals("label1||label2", l.getName());
    }

    /**
     * The name should have parenthesis at the right place to preserve the tree structure.
     */
    @Test
    void composite() {
        LabelAtom x = j.jenkins.getLabelAtom("x");
        assertEquals("!!x", x.not().not().getName());
        assertEquals("(x||x)&&x", x.or(x).and(x).getName());
        assertEquals("x&&x||x", x.and(x).or(x).getName());
    }

    @Test
    void dash() {
        j.jenkins.getLabelAtom("solaris-x86");
    }

    @Test
    void expression_atom_simple() {
        Label label = Label.parseExpression("a");
        assertThat(label, instanceOf(LabelAtom.class));
    }

    @Test
    void expression_atom_simpleLonger() {
        Label label = Label.parseExpression("abc123def");
        assertThat(label, instanceOf(LabelAtom.class));
    }

    @Test
    void expression_atom_withDash() {
        Label label = Label.parseExpression("a-b");
        assertThat(label, instanceOf(LabelAtom.class));
    }

    @Test
    @Issue("JENKINS-66613")
    void expression_atom_withDashes() {
        Label label = Label.parseExpression("--a----b-c-");
        assertThat(label, instanceOf(LabelAtom.class));
    }

    @Test
    @Issue("JENKINS-66613")
    void expression_atom_doubleDash() {
        assertEquals(new LabelAtom("--"), Label.parseExpression("--"));
    }

    @Test
    @Issue("JENKINS-66613")
    void expression_atom_dashBeforeImplies() {
        assertEquals(new LabelAtom("a-").implies(new LabelAtom("b")), Label.parseExpression("a-->b"));
    }

    @Test
    @Issue("JENKINS-66613")
    void expression_atom_dashAfterImplies() {
        assertEquals(new LabelAtom("a").implies(new LabelAtom("-b")), Label.parseExpression("a->-b"));
    }

    @Test
    @Issue("JENKINS-66613")
    void expression_atom_justDash() {
        assertEquals(new LabelAtom("-"), Label.parseExpression("-"));
    }

    @Test
    @Issue("JENKINS-66613")
    void expression_atom_dashBefore() {
        assertEquals(new LabelAtom("-1"), Label.parseExpression("-1"));
    }

    @Test
    @Issue("JENKINS-66613")
    void expression_atom_dashAround() {
        assertEquals(new LabelAtom("-abc-"), Label.parseExpression("-abc-"));
    }

    @Test
    void expression_implies() {
        Label label = Label.parseExpression("a -> b");
        assertThat(label, instanceOf(LabelExpression.Implies.class));
    }

    @Test
    @Issue("JENKINS-66613")
    void expression_implies_withoutSpaces() {
        Label label = Label.parseExpression("a->b");
        assertThat(label, instanceOf(LabelExpression.Implies.class));
    }

    @Test
    void expression_and() {
        Label label = Label.parseExpression("a && b");
        assertThat(label, instanceOf(LabelExpression.And.class));
    }

    @Test
    void expression_and_withoutSpaces() {
        Label label = Label.parseExpression("a&&b");
        assertThat(label, instanceOf(LabelExpression.And.class));
    }

    private void parseShouldFail(String expr, String message) {
        ANTLRException e = assertThrows(
                ANTLRException.class,
                () -> Label.parseExpression(expr),
                expr + " should fail to parse");
        assertThat(e, instanceOf(IllegalArgumentException.class));
        assertEquals(message, e.getMessage());
    }

    @Test
    void formValidation() throws Exception {
        j.executeOnServer(() -> {
            Label l = j.jenkins.getLabel("foo");
            DumbSlave s = j.createSlave(l);
            String msg = LabelExpression.validate("goo").renderHtml();
            assertTrue(msg.contains("foo"));
            assertTrue(msg.contains("goo"));

            msg = LabelExpression.validate("built-in && goo").renderHtml();
            assertTrue(msg.contains("foo"));
            assertTrue(msg.contains("goo"));
            return null;
        });
    }

    @Test
    void parseLabel() {
        Set<LabelAtom> result = Label.parse("one two three");
        String[] expected = {"one", "two", "three"};

        for (String e : expected) {
            assertTrue(result.contains(new LabelAtom(e)));
        }

        assertEquals(result.size(), expected.length);
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}
