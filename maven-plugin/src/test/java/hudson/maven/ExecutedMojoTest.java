package hudson.maven;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;

/**
 * Unit test for {@link ExecutedMojo}.
 * 
 * @author kutzi
 */
public class ExecutedMojoTest {
    
    private MojoDescriptor mojoDescriptor;
    private Level oldLevel;
    
    @Before
    public void before() {
        PluginDescriptor pluginDescriptor = new PluginDescriptor();
        pluginDescriptor.setGroupId("com.test");
        pluginDescriptor.setArtifactId("testPlugin");
        pluginDescriptor.setVersion("1.0");
        
        ClassRealm classRealm = new ClassRealm(null, "test", getClass().getClassLoader());
        pluginDescriptor.setClassRealm(classRealm);
        
        MojoDescriptor descriptor = new MojoDescriptor();
        descriptor.setPluginDescriptor(pluginDescriptor);
        this.mojoDescriptor = descriptor;
        
        // suppress the WARNING logs we expect
        this.oldLevel = Logger.getLogger(ExecutedMojo.class.getName()).getLevel();
        Logger.getLogger(ExecutedMojo.class.getName()).setLevel(Level.SEVERE);
    }
    
    @After
    public void after() {
        Logger.getLogger(ExecutedMojo.class.getName()).setLevel(oldLevel);
    }
    
    @Test
    public void testMojoFromJarFile() throws IOException, InterruptedException {
        // Faking JUnit's Assert to be the plugin class
        this.mojoDescriptor.setImplementation(Assert.class.getName());
        MojoExecution execution = new MojoExecution(this.mojoDescriptor);
        MojoInfo info = new MojoInfo(execution, null, null, null, -1);
        
        ExecutedMojo executedMojo = new ExecutedMojo(info, 1L);
        
        Assert.assertNotNull(executedMojo.digest);
    }
    
    @Test
    @Bug(5044)
    public void testMojoFromClassesDirectory() throws IOException, InterruptedException {
        // Faking this class as the mojo impl:
        this.mojoDescriptor.setImplementation(getClass().getName());
        MojoExecution execution = new MojoExecution(this.mojoDescriptor);
        MojoInfo info = new MojoInfo(execution, null, null, null, -1);
        
        ExecutedMojo executedMojo = new ExecutedMojo(info, 1L);
        
        Assert.assertEquals("com.test", executedMojo.groupId);
    }
}
