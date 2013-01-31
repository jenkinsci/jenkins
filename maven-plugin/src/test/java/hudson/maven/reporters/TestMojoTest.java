package hudson.maven.reporters;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import hudson.maven.MojoInfo;
import hudson.maven.MojoInfoBuilder;

import java.io.File;

import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;

public class TestMojoTest {
    
    @Test
    @Bug(16573)
    public void testGetReportFilesThrowsNoException() throws ComponentConfigurationException {
        // no 'reportsDirectory' or so config value set:
        MojoInfo mojoInfo = MojoInfoBuilder.mojoBuilder("com.some", "testMojo", "test").build();
        
        MavenProject pom = mock(MavenProject.class);
        when(pom.getBasedir()).thenReturn(new File("foo"));
        
        Build build = mock(Build.class);
        when(build.getDirectory()).thenReturn("bar");
        when(pom.getBuild()).thenReturn(build);
        
        for (TestMojo testMojo : TestMojo.values()) {
            testMojo.getReportFiles(pom, mojoInfo);
        }
    }

}
