package hudson.maven.reporters;

import hudson.maven.MavenModule;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MojoInfo;
import hudson.maven.MavenBuild;
import hudson.maven.MavenBuildProxy.BuildCallable;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;

import java.io.IOException;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 * @author Kohsuke Kawaguchi
 */
public class BuildInfoRecorder extends MavenReporter {

    private static final Set<String> keys = new HashSet<String>(Arrays.asList(
        "maven-jar-plugin:jar",
        "maven-jar-plugin:test-jar",
        "maven-war-plugin:war",
        "maven-ear-plugin:ear"
    ));

    public boolean preExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener) throws InterruptedException, IOException {
        if(mojo.pluginName.groupId.equals("org.apache.maven.plugins")
        && keys.contains(mojo.pluginName.artifactId+':'+mojo.getGoal())) {
            // touch <archive><manifestEntries><Build-Numer>#n

            Map<String,String> props = build.execute(new BuildCallable<Map<String,String>,IOException>() {
                public Map<String,String> call(MavenBuild build) throws IOException, InterruptedException {
                    Map<String,String> r = new HashMap<String, String>();
                    r.put("Hudson-Build-Number",String.valueOf(build.getNumber()));
                    r.put("Hudson-Project",build.getParent().getParent().getName());
                    r.put("Hudson-Version",Hudson.VERSION);
                    return r;
                }
            });

            PlexusConfiguration archive = mojo.configuration.getChild("archive");
            PlexusConfiguration manifestEntries = archive.getChild("manifestEntries",true);
            for (Entry<String,String> e : props.entrySet()) {
                if(manifestEntries.getChild(e.getKey(),false)!=null)
                    continue; // if the configuration is already given, use that. 
                XmlPlexusConfiguration configuration = new XmlPlexusConfiguration(e.getKey());
                configuration.setValue(e.getValue());
                manifestEntries.addChild(configuration);
            }
        }

        return super.preExecute(build, pom, mojo, listener);
    }

    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    public static final class DescriptorImpl extends MavenReporterDescriptor {
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        private DescriptorImpl() {
            super(BuildInfoRecorder.class);
        }

        public String getDisplayName() {
            return Messages.BuildInfoRecorder_DisplayName();
        }


        public BuildInfoRecorder newAutoInstance(MavenModule module) {
            return new BuildInfoRecorder();
        }
    }

    private static final long serialVersionUID = 1L;
}
