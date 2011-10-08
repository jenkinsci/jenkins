/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.maven.reporters;

import hudson.maven.MavenModule;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MojoInfo;
import hudson.maven.MavenBuild;
import hudson.maven.MavenBuildProxy.BuildCallable;
import hudson.model.BuildListener;
import jenkins.model.Jenkins;
import hudson.Extension;
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
                private static final long serialVersionUID = 7810179928341972415L;

                public Map<String,String> call(MavenBuild build) throws IOException, InterruptedException {
                    Map<String,String> r = new HashMap<String, String>();
                    // leave Hudson for backward comp
                    r.put("Hudson-Build-Number",String.valueOf(build.getNumber()));
                    r.put("Hudson-Project",build.getParent().getParent().getName());
                    r.put("Hudson-Version", Jenkins.VERSION);
                    r.put("Jenkins-Build-Number",String.valueOf(build.getNumber()));
                    r.put("Jenkins-Project",build.getParent().getParent().getName());
                    r.put("Jenkins-Version", Jenkins.VERSION);
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

    @Extension
    public static final class DescriptorImpl extends MavenReporterDescriptor {
        public String getDisplayName() {
            return Messages.BuildInfoRecorder_DisplayName();
        }


        public BuildInfoRecorder newAutoInstance(MavenModule module) {
            return new BuildInfoRecorder();
        }
    }

    private static final long serialVersionUID = 1L;
}
