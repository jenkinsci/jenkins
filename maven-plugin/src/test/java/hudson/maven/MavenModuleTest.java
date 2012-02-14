package hudson.maven;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.support.membermodification.MemberMatcher.constructor;
import static org.powermock.api.support.membermodification.MemberModifier.suppress;
import hudson.maven.MavenModuleSet.DescriptorImpl;
import hudson.model.AbstractProject;
import hudson.model.DependencyGraph;
import hudson.model.MockHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import hudson.util.Graph;
import junit.framework.Assert;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.Bug;
import org.mockito.Matchers;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.common.collect.Lists;

@RunWith(PowerMockRunner.class)
@PrepareForTest( { MavenModuleSet.class, DescriptorImpl.class, AbstractProject.class})
public class MavenModuleTest {

    private MavenModule module;

    private MavenProject project;

    @Before
    public void before() {
        suppress(constructor(AbstractProject.class));
        suppress(constructor(DescriptorImpl.class));

        this.module = mock(MavenModule.class);
        basicMocking(this.module);

        this.project = new MavenProject();
        project.setGroupId("test");
        project.setArtifactId("testmodule");
        project.setVersion("2.0-SNAPSHOT");
        project.setPackaging("jar");

        this.module.reconfigure(new PomInfo(project, null, "relPath"));
        this.module.doSetName("test$testmodule");
    }

    /**
     * Tests that a {@link MavenModule} which builds a plugin is recognized as a snapshot
     * dependency in another module using that plugin.
     */
    @Test
    @Bug(10530)
    public void testMavenModuleAsPluginDependency() {
        MavenModule pluginModule = createPluginProject();

        addModuleAsPluginDependency(this.module, pluginModule);

        when(this.module.getAllMavenModules()).thenReturn(Lists.newArrayList(this.module, pluginModule));

        DependencyGraph graph = MockHelper.mockDependencyGraph(
                Lists.<AbstractProject<?,?>>newArrayList(this.module, pluginModule));
        graph.build();

        @SuppressWarnings("rawtypes")
        List<AbstractProject> downstream = graph.getDownstream(pluginModule);
        Assert.assertEquals(1, downstream.size());
        Assert.assertSame(this.module, downstream.get(0));
    }

    private static void addModuleAsPluginDependency(MavenModule module, MavenModule pluginModule) {
        Build build = new Build();
        Plugin plugin = new Plugin();
        plugin.setGroupId(pluginModule.getModuleName().groupId);
        plugin.setArtifactId(pluginModule.getModuleName().artifactId);
        plugin.setVersion(pluginModule.getVersion());
        build.setPlugins(Collections.singletonList(plugin));

        MavenProject project = new MavenProject();
        project.setGroupId(module.getModuleName().groupId);
        project.setArtifactId(module.getModuleName().artifactId);
        project.setVersion(module.getVersion());
        project.setPackaging("jar");
        project.setBuild(build);

        module.reconfigure(new PomInfo(project, null, "relPath"));
    }

    private static MavenModule createPluginProject() {
        MavenModule pluginModule = mock(MavenModule.class);
        basicMocking(pluginModule);

        MavenProject proj = new MavenProject();
        proj.setGroupId("test");
        proj.setArtifactId("pluginmodule");
        proj.setVersion("1.0-SNAPSHOT");
        proj.setPackaging("maven-plugin");
        PomInfo info = new PomInfo(proj, null, "relPath");
        pluginModule.reconfigure(info);
        pluginModule.doSetName("test$pluginmodule");

        return pluginModule;
    }

    private static void basicMocking(MavenModule mock) {
        when(mock.isBuildable()).thenReturn(Boolean.TRUE);
        doCallRealMethod().when(mock).reconfigure(Matchers.any(PomInfo.class));
        doCallRealMethod().when(mock).buildDependencyGraph(Matchers.any(DependencyGraph.class));
        when(mock.asDependency()).thenCallRealMethod();
        doCallRealMethod().when(mock).doSetName(Matchers.anyString());
        when(mock.getModuleName()).thenCallRealMethod();
        when(mock.getVersion()).thenCallRealMethod();

        MavenModuleSet parent = mock(MavenModuleSet.class);
        when(parent.isAggregatorStyleBuild()).thenReturn(Boolean.FALSE);
        when(mock.getParent()).thenReturn(parent);

        when(parent.getModules()).thenReturn(Collections.singleton(mock));
    }

    /**
     * This test is a standard project that has a versioned dependency.
     */
    @Test
    public void testSimpleVersion() {
        TestComponents testComponents = createTestComponents("1.0.1-SNAPSHOT");

        DependencyGraph graph = testComponents.graph;
        MavenModule appMavenModule = testComponents.applicationMavenModule;
        MavenModule libMavenModule = testComponents.libraryMavenModule;

        graph.build();

        List<AbstractProject> appDownstream = graph.getDownstream(appMavenModule);
        List<AbstractProject> appUpstream = graph.getUpstream(appMavenModule);
        List<AbstractProject> libDownstream = graph.getDownstream(libMavenModule);
        List<AbstractProject> libUpstream = graph.getUpstream(libMavenModule);

        assertEquals(0, appDownstream.size());
        assertEquals(1, appUpstream.size());
        assertEquals(1, libDownstream.size());
        assertEquals(0, libUpstream.size());
    }

    /**
     * This tests that a version range declaration in the dependency of a top level project
     * resolves the up and downstream correctly.
     */
    @Test
    public void testSimpleVersionRange() {
        TestComponents testComponents = createTestComponents("[1.0.0, )");

        DependencyGraph graph = testComponents.graph;
        MavenModule appMavenModule = testComponents.applicationMavenModule;
        MavenModule libMavenModule = testComponents.libraryMavenModule;

        graph.build();

        List<AbstractProject> appDownstream = graph.getDownstream(appMavenModule);
        List<AbstractProject> appUpstream = graph.getUpstream(appMavenModule);
        List<AbstractProject> libDownstream = graph.getDownstream(libMavenModule);
        List<AbstractProject> libUpstream = graph.getUpstream(libMavenModule);

        assertEquals(0, appDownstream.size());
        assertEquals(1, appUpstream.size());
        assertEquals(1, libDownstream.size());
        assertEquals(0, libUpstream.size());
    }

    private TestComponents createTestComponents(String libraryVersion) {
        MavenProject appProject = createApplication();
        Dependency dependency = createDependency(libraryVersion);
        appProject.getDependencies().add(dependency);

        PomInfo appInfo = new PomInfo(appProject, null, "relPath");
        MavenModule appMavenModule = mock(MavenModule.class);
        basicMocking(appMavenModule);
        appMavenModule.doSetName("test$application");
        appMavenModule.reconfigure(appInfo);

        MavenProject libProject = createLibrary();
        PomInfo libInfo = new PomInfo(libProject, null, "relPath");
        MavenModule libMavenModule = mock(MavenModule.class);
        basicMocking(libMavenModule);
        libMavenModule.doSetName("test$library");
        libMavenModule.reconfigure(libInfo);

        MavenModuleSet parent = mock(MavenModuleSet.class);
        when(parent.isAggregatorStyleBuild()).thenReturn(Boolean.FALSE);
        when(appMavenModule.getParent()).thenReturn(parent);
        when(libMavenModule.getParent()).thenReturn(parent);

        Collection<MavenModule> projects = Lists.newArrayList(appMavenModule, libMavenModule);
        when(parent.getModules()).thenReturn(projects);
        when(appMavenModule.getAllMavenModules()).thenReturn(projects);
        when(libMavenModule.getAllMavenModules()).thenReturn(projects);

        DependencyGraph graph = MockHelper.mockDependencyGraph(Lists.<AbstractProject<?,?>>newArrayList(appMavenModule, libMavenModule));
        doCallRealMethod().when(graph).getDownstream(Matchers.any(AbstractProject.class));
        doCallRealMethod().when(graph).getUpstream(Matchers.any(AbstractProject.class));

        TestComponents testComponents = new TestComponents();
        testComponents.graph = graph;
        testComponents.applicationMavenModule = appMavenModule;
        testComponents.libraryMavenModule = libMavenModule;

        return testComponents;
    }

    private static MavenProject createApplication() {
        MavenProject proj = new MavenProject();
        proj.setName("testapp");
        proj.setGroupId("test");
        proj.setArtifactId("application");
        proj.setVersion("1.0-SNAPSHOT");
        proj.setPackaging("jar");

        return proj;
    }

    private static Dependency createDependency(String version) {
        Dependency dependency = new Dependency();
        dependency.setGroupId("test");
        dependency.setArtifactId("library");
        dependency.setVersion(version);
        return dependency;
    }

    private static MavenProject createLibrary() {
        MavenProject proj = new MavenProject();
        proj.setName("testlib");
        proj.setGroupId("test");
        proj.setArtifactId("library");
        proj.setVersion("1.0.1-SNAPSHOT");
        proj.setPackaging("jar");

        Dependency dependency = new Dependency();
        dependency.setArtifactId("log4j");
        dependency.setGroupId("log4j");
        dependency.setVersion("1.6.15");
        proj.getDependencies().add(dependency);
        return proj;
    }

    private static class TestComponents {
        public DependencyGraph graph;
        public MavenModule applicationMavenModule;
        public MavenModule libraryMavenModule;
    }
}
