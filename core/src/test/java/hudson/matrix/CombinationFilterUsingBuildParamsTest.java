/*
 * The MIT License
 *
 * Copyright (c) 2012, RedHat Inc.
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
package hudson.matrix;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import hudson.ExtensionList;
import hudson.matrix.MatrixBuild.MatrixBuildExecution;
import hudson.matrix.listeners.MatrixBuildListener;
import hudson.model.AbstractItem;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Node;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import jenkins.model.Jenkins;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.verification.VerificationMode;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Make sure that the combination filter schedules correct builds in correct order
 *
 * @author ogondza
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {Jenkins.class, MatrixBuildListener.class, MatrixProject.class, AbstractItem.class})
public class CombinationFilterUsingBuildParamsTest {

    /**
     * Execute releases: experimental, stable, beta, devel
     *
     *     x s b d
     * 0.1
     * 0.9 * * * *
     *   1   * * *
     *   2     * *
     *   3       *
     */
    private static final String filter =
            String.format(
            "(%s) || (%s) || (%s)",
            "RELEASE == 'stable' && VERSION == '1'",
            "RELEASE == 'beta'   && VERSION >= '1' && VERSION <= '2'",
            "RELEASE == 'devel'  && VERSION >= '1' && VERSION <= '3'"
    );

    private static final String touchstoneFilter = "VERSION == '0.9'";

    private static final List<String> releases = Arrays.asList(
            "stable", "beta", "devel", "experimental"
    );

    private final Map<String, MatrixConfiguration> confs = new HashMap<String, MatrixConfiguration>();
    private final MatrixExecutionStrategy strategy = new DefaultMatrixExecutionStrategyImpl (
            true, touchstoneFilter, Result.SUCCESS, new NoopMatrixConfigurationSorter()
    );

    private MatrixProject project;
    @Mock private MatrixBuildExecution execution;
    @Mock private MatrixBuild build;
    @Mock private MatrixRun run;
    @Mock private BuildListener listener;

    @Mock private Jenkins jenkins;
    @Mock private ExtensionList<MatrixBuildListener> extensions;

    @Before
    public void setUp() throws Exception {

        MockitoAnnotations.initMocks(this);

        usingDummyJenkins();
        usingNoListeners();
        usingDummyProject();
        usingDummyExecution();
        withReleaseAxis(releases);
    }

    @Test
    public void testCombinationFilterV01() throws InterruptedException, IOException {

        givenTheVersionIs("0.1");

        strategy.run(execution);

        wasNotBuilt(confs.get("devel"));
        wasNotBuilt(confs.get("beta"));
        wasNotBuilt(confs.get("stable"));
        wasNotBuilt(confs.get("experimental"));
    }

    @Test
    public void testCombinationFilterV09() throws InterruptedException, IOException {

        givenTheVersionIs("0.9");

        strategy.run(execution);

        wasBuilt(confs.get("devel"));
        wasBuilt(confs.get("beta"));
        wasBuilt(confs.get("stable"));
        wasBuilt(confs.get("experimental"));
    }

    @Test
    public void testCombinationFilterV1() throws InterruptedException, IOException {

        givenTheVersionIs("1");

        strategy.run(execution);

        wasBuilt(confs.get("devel"));
        wasBuilt(confs.get("beta"));
        wasBuilt(confs.get("stable"));
        wasNotBuilt(confs.get("experimental"));
    }

    @Test
    public void testCombinationFilterV2() throws InterruptedException, IOException {

        givenTheVersionIs("2");

        strategy.run(execution);

        wasBuilt(confs.get("devel"));
        wasBuilt(confs.get("beta"));
        wasNotBuilt(confs.get("stable"));
        wasNotBuilt(confs.get("experimental"));
    }

    @Test
    public void testCombinationFilterV3() throws InterruptedException, IOException {

        givenTheVersionIs("3");

        strategy.run(execution);

        wasBuilt(confs.get("devel"));
        wasNotBuilt(confs.get("beta"));
        wasNotBuilt(confs.get("stable"));
        wasNotBuilt(confs.get("experimental"));
    }

    private void usingDummyProject() {

        project = PowerMockito.mock(MatrixProject.class);

        PowerMockito.when(build.getParent()).thenReturn(project);
        PowerMockito.when(project.getUrl()).thenReturn("/my/project/");

        when(project.getAxes()).thenReturn(new AxisList(new Axis("RELEASE", releases)));
        when(project.getCombinationFilter()).thenReturn(filter);
    }

    private void usingDummyExecution() {

        when(execution.getProject()).thenReturn(project);
        when(execution.getBuild()).thenReturn(build);
        when(execution.getListener()).thenReturn(listener);

        // throw away logs
        when(listener.getLogger()).thenReturn(new PrintStream(
                new ByteArrayOutputStream()
        ));

        // Succeed immediately
        when(run.isBuilding()).thenReturn(false);
        when(run.getResult()).thenReturn(Result.SUCCESS);
    }

    private void usingDummyJenkins() {

        PowerMockito.mockStatic(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(jenkins);
        when(jenkins.getNodes()).thenReturn(new ArrayList<Node>());
    }

    private void usingNoListeners() {

        when(extensions.iterator()).thenReturn(new ArrayList<MatrixBuildListener>().iterator());
        when(MatrixBuildListener.all()).thenReturn(extensions);
    }

    private void withReleaseAxis(final List<String> releases) {

        for(final String release: releases) {

          confs.put(release, getConfiguration("RELEASE=" + release));
        }

        when(execution.getActiveConfigurations()).thenReturn(
                new HashSet<MatrixConfiguration>(confs.values())
        );
    }

    private MatrixConfiguration getConfiguration (final String axis) {

        final MatrixConfiguration conf = mock(MatrixConfiguration.class);
        when(conf.getParent()).thenReturn(project);
        when(conf.getCombination()).thenReturn(Combination.fromString(axis));
        when(conf.getDisplayName()).thenReturn(axis);
        when(conf.getUrl()).thenReturn(axis);
        when(conf.getBuildByNumber(anyInt())).thenReturn(run);

        return conf;
    }

    private void givenTheVersionIs(final String version) {

        final ParametersAction parametersAction = new ParametersAction(
                new StringParameterValue("VERSION", version)
        );

        when(build.getAction(ParametersAction.class))
            .thenReturn(parametersAction)
        ;
    }

    private void wasBuilt(final MatrixConfiguration conf) {

        wasBuildTimes(conf, times(1));
    }

    private void wasNotBuilt(final MatrixConfiguration conf) {

        wasBuildTimes(conf, never());
    }

    private void wasBuildTimes(
            final MatrixConfiguration conf, final VerificationMode mode
    ) {

        verify(conf, mode).scheduleBuild(
                new ArrayList<MatrixChildAction>(),
                new Cause.UpstreamCause((Run<?, ?>) build)
        );
    }
}
