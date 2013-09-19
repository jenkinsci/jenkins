package hudson.maven;

import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.support.membermodification.MemberMatcher.constructor;
import static org.powermock.api.support.membermodification.MemberModifier.suppress;
import hudson.maven.MavenModuleSet.DescriptorImpl;
import hudson.model.AbstractProject;
import hudson.model.DependencyGraph;
import hudson.model.MockHelper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
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

        Assert.assertEquals(0, appDownstream.size());
        Assert.assertEquals(1, appUpstream.size());
        Assert.assertEquals(1, libDownstream.size());
        Assert.assertEquals(0, libUpstream.size());
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

        Assert.assertEquals(0, appDownstream.size());
        Assert.assertEquals(1, appUpstream.size());
        Assert.assertEquals(1, libDownstream.size());
        Assert.assertEquals(0, libUpstream.size());
    }

    /**
     * Test multiple projects with dependencies on differing library versions declared with
     * multiple version definitions.
     */
    @Test
    public void testMultipleDependencies() {

        MavenProject projectA = createMavenProject("ProjectA", "test", "projectA", "1.0-SNAPSHOT", "jar");
        Dependency dependencyA = createDependency("test", "library", "[1.0, 2.0)");
        projectA.getDependencies().add(dependencyA);

        MavenProject projectB = createMavenProject("ProjectB", "test", "projectB", "2.0-SNAPSHOT", "jar");
        Dependency dependencyB = createDependency("test", "library", "[1.1, 2.1]");
        projectB.getDependencies().add(dependencyB);

        MavenProject dependX = createMavenProject("DependX-1.1", "test", "library", "1.1.3-SNAPSHOT", "jar");
        MavenProject dependY = createMavenProject("DependX-1.2", "test", "library", "1.2.1-SNAPSHOT", "jar");
        MavenProject dependZ = createMavenProject("DependX-2.0", "test", "library", "2.0.1-SNAPSHOT", "jar");

        MavenModuleSet parent = mock(MavenModuleSet.class);
        when(parent.isAggregatorStyleBuild()).thenReturn(Boolean.FALSE);

        //Now create maven modules for all the projects
        MavenModule mavenModuleA = mockMavenModule(projectA);
        MavenModule mavenModuleB = mockMavenModule(projectB);
        MavenModule mavenModuleX = mockMavenModule(dependX);
        MavenModule mavenModuleY = mockMavenModule(dependY);
        MavenModule mavenModuleZ = mockMavenModule(dependZ);

        Collection<AbstractProject<?,?>> allModules = Lists.<AbstractProject<?,?>>newArrayList(mavenModuleA,
                mavenModuleB, mavenModuleX, mavenModuleY, mavenModuleZ);

        for (AbstractProject<?, ?> module : allModules) {
            MavenModule mm = (MavenModule) module;
            enhanceMavenModuleMock(mm, parent, allModules);
        }

        DependencyGraph graph = MockHelper.mockDependencyGraph(allModules);
        doCallRealMethod().when(graph).getDownstream(Matchers.any(AbstractProject.class));
        doCallRealMethod().when(graph).getUpstream(Matchers.any(AbstractProject.class));
        doCallRealMethod().when(graph).compare(Matchers.<AbstractProject>any(), Matchers.<AbstractProject>any());
        graph.build();

        List<AbstractProject> downstreamA = graph.getDownstream(mavenModuleA);
        List<AbstractProject> upstreamA = graph.getUpstream(mavenModuleA);

        Assert.assertEquals(0, downstreamA.size());
        Assert.assertEquals(1, upstreamA.size());
        Assert.assertSame(dependY.getVersion(), ((MavenModule) upstreamA.get(0)).getVersion());

        List<AbstractProject> downstreamB = graph.getDownstream(mavenModuleB);
        List<AbstractProject> upstreamB = graph.getUpstream(mavenModuleB);

        Assert.assertEquals(0, downstreamB.size());
        Assert.assertEquals(1, upstreamA.size());
        Assert.assertSame(dependZ.getVersion(), ((MavenModule) upstreamB.get(0)).getVersion());
    }

    /**
     * This tests a project that has a dependency on a specific version of X.
     * The project X has moved on and so should not have any dependencies on ProjectA.
     */
    @Test
    public void testProjectWithSpecifiedVersionAndNoDependencies() {
        MavenProject projectA = createMavenProject("ProjectA", "test", "projectA", "1.0-SNAPSHOT", "jar");
        Dependency dependencyA = createDependency("test", "library", "1.0");
        projectA.getDependencies().add(dependencyA);

        MavenProject dependX = createMavenProject("DependX-1.1", "test", "library", "1.2-SNAPSHOT", "jar");

        MavenModuleSet parent = mock(MavenModuleSet.class);
        when(parent.isAggregatorStyleBuild()).thenReturn(Boolean.FALSE);

        //Now create maven modules for all the projects
        MavenModule mavenModuleA = mockMavenModule(projectA);
        MavenModule mavenModuleX = mockMavenModule(dependX);

        Collection<AbstractProject<?,?>> allModules = Lists.<AbstractProject<?,?>>newArrayList(mavenModuleA,
                mavenModuleX);

        for (AbstractProject<?, ?> module : allModules) {
            MavenModule mm = (MavenModule) module;
            enhanceMavenModuleMock(mm, parent, allModules);
        }

        DependencyGraph graph = MockHelper.mockDependencyGraph(allModules);
        doCallRealMethod().when(graph).getDownstream(Matchers.any(AbstractProject.class));
        doCallRealMethod().when(graph).getUpstream(Matchers.any(AbstractProject.class));
        doCallRealMethod().when(graph).compare(Matchers.<AbstractProject>any(), Matchers.<AbstractProject>any());
        graph.build();

        List<AbstractProject> downstreamA = graph.getDownstream(mavenModuleA);
        List<AbstractProject> upstreamA = graph.getUpstream(mavenModuleA);

        Assert.assertEquals(0, downstreamA.size());
        Assert.assertEquals(0, upstreamA.size());

        List<AbstractProject> downstreamX = graph.getDownstream(mavenModuleX);
        List<AbstractProject> upstreamX = graph.getUpstream(mavenModuleX);

        Assert.assertEquals(0, downstreamX.size());
        Assert.assertEquals(0, upstreamX.size());
    }

    private TestComponents createTestComponents(String libraryVersion) {
        MavenProject appProject = createMavenProject("testapp", "test", "application", "1.0-SNAPSHOT", "jar");
        Dependency dependency = createDependency("test", "library", libraryVersion);
        appProject.getDependencies().add(dependency);

        MavenModule appMavenModule = mockMavenModule(appProject);

        MavenProject libProject = createLibrary();
        MavenModule libMavenModule = mockMavenModule(libProject);

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

    private static void enhanceMavenModuleMock(MavenModule module,
                                                      MavenModuleSet parent,
                                                      Collection allProjects) {
        when(module.getParent()).thenReturn(parent);
        when(module.getAllMavenModules()).thenReturn(allProjects);
    }

    private static MavenModule mockMavenModule(MavenProject project) {
        MavenModule mavenModule = mock(MavenModule.class);
        when(mavenModule.getName()).thenReturn(project.getName());
        basicMocking(mavenModule);
        mavenModule.doSetName(project.getGroupId() + '$' + project.getArtifactId());

        PomInfo pomInfo = new PomInfo(project, null, "relPath");
        mavenModule.reconfigure(pomInfo);

        return mavenModule;
    }

    private static MavenProject createMavenProject(String name,
                                                   String groupId,
                                                   String artifactId,
                                                   String version,
                                                   String packaging) {
        MavenProject proj = new MavenProject();
        proj.setName(name);
        proj.setGroupId(groupId);
        proj.setArtifactId(artifactId);
        proj.setVersion(version);
        proj.setPackaging(packaging);

        return proj;
    }

    private static Dependency createDependency(String groupId, String artifactId, String version) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        return dependency;
    }

    private static MavenProject createLibrary() {
        MavenProject proj = createMavenProject("testlib", "test", "library", "1.0.1-SNAPSHOT", "jar");

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
