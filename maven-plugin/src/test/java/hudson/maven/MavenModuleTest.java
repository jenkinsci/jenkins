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

import java.util.Collections;
import java.util.List;

import junit.framework.Assert;

import org.apache.maven.model.Build;
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
    
    private MavenModuleSet parent;
    
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
}
