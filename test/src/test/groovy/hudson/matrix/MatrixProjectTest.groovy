/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., CloudBees, Inc.
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
package hudson.matrix

import hudson.model.Cause
import hudson.model.Result
import hudson.tasks.Ant
import hudson.tasks.ArtifactArchiver
import hudson.tasks.Fingerprinter
import hudson.tasks.Maven
import hudson.tasks.Shell
import hudson.tasks.BatchFile
import org.jvnet.hudson.test.Email
import org.jvnet.hudson.test.HudsonTestCase
import org.jvnet.hudson.test.SingleFileSCM
import org.jvnet.hudson.test.UnstableBuilder
import com.gargoylesoftware.htmlunit.html.HtmlTable
import org.jvnet.hudson.test.Bug
import org.jvnet.hudson.test.TestBuilder
import hudson.model.AbstractBuild
import hudson.Launcher
import hudson.model.BuildListener
import hudson.util.OneShotEvent
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import hudson.model.JDK
import hudson.model.Slave
import hudson.Functions
import hudson.model.ParametersDefinitionProperty
import hudson.model.FileParameterDefinition
import hudson.model.Cause.LegacyCodeCause
import hudson.model.ParametersAction
import hudson.model.FileParameterValue

import java.util.concurrent.CountDownLatch

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixProjectTest extends HudsonTestCase {
    /**
     * Tests that axes are available as build variables in the Ant builds.
     */
    public void testBuildAxisInAnt() throws Exception {
        MatrixProject p = createMatrixProject();
        Ant.AntInstallation ant = configureDefaultAnt();
        p.getBuildersList().add(new Ant('-Dprop=${db} test',ant.getName(),null,null,null));

        // we need a dummy build script that echos back our property
        p.setScm(new SingleFileSCM("build.xml",'<project default="test"><target name="test"><echo>assertion ${prop}=${db}</echo></target></project>'));

        MatrixBuild build = p.scheduleBuild2(0, new Cause.UserCause()).get();
        List<MatrixRun> runs = build.getRuns();
        assertEquals(4,runs.size());
        for (MatrixRun run : runs) {
            assertBuildStatus(Result.SUCCESS, run);
            String expectedDb = run.getParent().getCombination().get("db");
            assertLogContains("assertion "+expectedDb+"="+expectedDb, run);
        }
    }

    /**
     * Tests that axes are available as build variables in the Maven builds.
     */
    public void testBuildAxisInMaven() throws Exception {
        MatrixProject p = createMatrixProject();
        Maven.MavenInstallation maven = configureDefaultMaven();
        p.getBuildersList().add(new Maven('-Dprop=${db} validate',maven.getName()));

        // we need a dummy build script that echos back our property
        p.setScm(new SingleFileSCM("pom.xml",getClass().getResource("echo-property.pom")));

        MatrixBuild build = p.scheduleBuild2(0).get();
        List<MatrixRun> runs = build.getRuns();
        assertEquals(4,runs.size());
        for (MatrixRun run : runs) {
            assertBuildStatus(Result.SUCCESS, run);
            String expectedDb = run.getParent().getCombination().get("db");
            System.out.println(run.getLog());
            assertLogContains("assertion "+expectedDb+"="+expectedDb, run);
            // also make sure that the variables are expanded at the command line level.
            assertFalse(run.getLog().contains('-Dprop=${db}'));
        }
    }

    /**
     * Test that configuration filters work
     */
    public void testConfigurationFilter() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setCombinationFilter("db==\"mysql\"");
        MatrixBuild build = p.scheduleBuild2(0).get();
        assertEquals(2, build.getRuns().size());
    }

    /**
     * Test that touch stone builds  work
     */
    public void testTouchStone() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setTouchStoneCombinationFilter("db==\"mysql\"");
        p.setTouchStoneResultCondition(Result.SUCCESS);
        MatrixBuild build = p.scheduleBuild2(0).get();
        assertEquals(4, build.getRuns().size());

        p.getBuildersList().add(new UnstableBuilder());
        build = p.scheduleBuild2(0).get();
        assertEquals(2, build.exactRuns.size());
    }

    @Override
    protected MatrixProject createMatrixProject() throws IOException {
        MatrixProject p = super.createMatrixProject();

        // set up 2x2 matrix
        AxisList axes = new AxisList();
        axes.add(new TextAxis("db","mysql","oracle"));
        axes.add(new TextAxis("direction","north","south"));
        p.setAxes(axes);

        return p;
    }

    /**
     * Fingerprinter failed to work on the matrix project.
     */
    @Email("http://www.nabble.com/1.286-version-and-fingerprints-option-broken-.-td22236618.html")
    public void testFingerprinting() throws Exception {
        MatrixProject p = createMatrixProject();
        if (Functions.isWindows()) 
           p.getBuildersList().add(new BatchFile("echo \"\" > p"));
        else 
           p.getBuildersList().add(new Shell("touch p"));
        
        p.getPublishersList().add(new ArtifactArchiver("p",null,false));
        p.getPublishersList().add(new Fingerprinter("",true));
        buildAndAssertSuccess(p);
    }

    void assertRectangleTable(MatrixProject p) {
        def html = createWebClient().getPage(p);
        HtmlTable table = html.selectSingleNode("id('matrix')/table")

        // remember cells that are extended from rows above.
        def rowSpans = [:];
        def masterWidth = null
        for (r in table.rows) {
            int width = r.cells*.columnSpan.sum() + rowSpans.values().sum(0);
            if (masterWidth==null)
                masterWidth = width;
            else
                assertEquals(masterWidth,width);

            for (c in r.cells)
                rowSpans[c.rowSpan] = (rowSpans[c.rowSpan]?:0)+c.columnSpan
            // shift rowSpans by one
            def nrs =[:]
            rowSpans.each { k,v -> if(k>1) nrs[k-1]=v }
            rowSpans = nrs
        }
    }

    @Bug(4245)
    void testLayout1() {
        // 5*5*5*5*5 matrix
        def p = createMatrixProject();
        p.axes = new AxisList(
            ['a','b','c','d','e'].collect { name -> new TextAxis(name, (1..4)*.toString() ) }
        );
        assertRectangleTable(p)
    }

    @Bug(4245)
    void testLayout2() {
        // 2*3*4*5*6 matrix
        def p = createMatrixProject();
        p.axes = new AxisList(
            (2..6).collect { n -> new TextAxis("axis${n}", (1..n)*.toString() ) }
        );
        assertRectangleTable(p)
    }

    /**
     * Makes sure that the configuration correctly roundtrips.
     */
    public void testConfigRoundtrip() {
        jenkins.getJDKs().addAll([
                new JDK("jdk1.7","somewhere"),
                new JDK("jdk1.6","here"),
                new JDK("jdk1.5","there")]);

        List<Slave> slaves = (0..2).collect { createSlave() }

        def p = createMatrixProject();
        p.axes.add(new JDKAxis(["jdk1.6","jdk1.5"]));
        p.axes.add(new LabelAxis("label1",[slaves[0].nodeName, slaves[1].nodeName]));
        p.axes.add(new LabelAxis("label2",[slaves[2].nodeName])); // make sure single value handling works OK
        def o = new AxisList(p.axes);
        configRoundtrip(p);
        def n = p.axes;

        assertEquals(o.size(),n.size());
        (0 ..< (o.size())).each { i ->
            def oi = o[i];
            def ni = n[i];
            assertSame(oi.class,ni.class);
            assertEquals(oi.name,ni.name);
            assertEquals(oi.values,ni.values);
        }


        def before = new DefaultMatrixExecutionStrategyImpl(true, "foo", Result.UNSTABLE, null)
        p.executionStrategy = before;
        configRoundtrip(p);
        assertEqualDataBoundBeans(p.executionStrategy,before);

        before = new DefaultMatrixExecutionStrategyImpl(false, null, null, null)
        p.executionStrategy = before;
        configRoundtrip(p);
        assertEqualDataBoundBeans(p.executionStrategy,before);
    }

    public void testLabelAxes() {
        def p = createMatrixProject();

        List<Slave> slaves = (0..<4).collect { createSlave() }

        p.axes.add(new LabelAxis("label1",[slaves[0].nodeName, slaves[1].nodeName]));
        p.axes.add(new LabelAxis("label2",[slaves[2].nodeName, slaves[3].nodeName]));

        System.out.println(p.labels);
        assertEquals(4,p.labels.size());
        assertTrue(p.labels.contains(jenkins.getLabel("slave0&&slave2")));
        assertTrue(p.labels.contains(jenkins.getLabel("slave1&&slave2")));
        assertTrue(p.labels.contains(jenkins.getLabel("slave0&&slave3")));
        assertTrue(p.labels.contains(jenkins.getLabel("slave1&&slave3")));
    }

    /**
     * Quiettng down Hudson causes a dead lock if the parent is running but children is in the queue
     */
    @Bug(4873)
    void testQuietDownDeadlock() {
        def p = createMatrixProject();
        p.axes = new AxisList(new TextAxis("foo","1","2"));
        p.runSequentially = true; // so that we can put the 2nd one in the queue

        OneShotEvent firstStarted = new OneShotEvent();
        OneShotEvent buildCanProceed = new OneShotEvent();

        p.getBuildersList().add( [perform:{ AbstractBuild build, Launcher launcher, BuildListener listener ->
            firstStarted.signal();
            buildCanProceed.block();
            return true;
        }] as TestBuilder );
        Future f = p.scheduleBuild2(0)

        // have foo=1 block to make sure the 2nd configuration is in the queue
        firstStarted.block();
        // enter into the quiet down while foo=2 is still in the queue
        jenkins.doQuietDown();
        buildCanProceed.signal();

        // make sure foo=2 still completes. use time out to avoid hang
        assertBuildStatusSuccess(f.get(10,TimeUnit.SECONDS));

        // MatrixProject scheduled after the quiet down shouldn't start
        try {
            Future g = p.scheduleBuild2(0)
            g.get(3,TimeUnit.SECONDS)
            fail()
        } catch (TimeoutException e) {
            // expected
        }        
    }

    @Bug(9009)
    void testTrickyNodeName() {
        def names = [ createSlave("Sean's Workstation",null), createSlave("John\"s Workstation",null) ]*.nodeName;
        def p = createMatrixProject();
        p.setAxes(new AxisList([new LabelAxis("label",names)]));
        configRoundtrip(p);

        LabelAxis a = p.axes.find("label");
        assertEquals(a.values as Set,names as Set);
    }

    @Bug(10108)
    void testTwoFileParams() {
        def p = createMatrixProject();
        p.axes = new AxisList(new TextAxis("foo","1","2","3","4"));
        p.addProperty(new ParametersDefinitionProperty(
            new FileParameterDefinition("a.txt",""),
            new FileParameterDefinition("b.txt",""),
        ));

        def dir = createTmpDir()
        def f = p.scheduleBuild2(0,new LegacyCodeCause(),new ParametersAction(
            ["aaa","bbb"].collect { it ->
                def v = new FileParameterValue(it+".txt",File.createTempFile(it,"", dir),it)
                v.setLocation(it)
                return v;
            }
        ))
        
        assertBuildStatusSuccess(f.get(10,TimeUnit.SECONDS));
    }

    /**
     * Verifies that the concurrent build feature works, and makes sure
     * that each gets its own unique workspace.
     */
    void testConcurrentBuild() {
        jenkins.numExecutors = 10
        jenkins.updateComputerList()

        def p = createMatrixProject()
        p.axes = new AxisList(new TextAxis("foo","1","2"))
        p.concurrentBuild = true;
        def latch = new CountDownLatch(4)
        def dirs = Collections.synchronizedSet(new HashSet())
        
        p.buildersList.add(new TestBuilder() {
            boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                dirs << build.workspace.getRemote()
                def marker = build.workspace.child("file")
                def name = build.fullDisplayName
                marker.write(name,"UTF-8")
                latch.countDown()
                latch.await()
                assertEquals(name,marker.readToString())
                return true
            }
        })

        // should have gotten all unique names
        def f1 = p.scheduleBuild2(0)
        // get one going
        Thread.sleep(1000)
        def f2 = p.scheduleBuild2(0)
        [f1,f2]*.get().each{ assertBuildStatusSuccess(it)}

        assertEquals 4, dirs.size()
    }
}
