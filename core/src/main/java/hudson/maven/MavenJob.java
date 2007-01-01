package hudson.maven;

import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.JobDescriptor;
import hudson.model.RunMap;
import hudson.model.RunMap.Constructor;

import java.io.File;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public final class MavenJob extends AbstractProject<MavenJob,MavenBuild> {
    public MavenJob(Hudson parent, String name) {
        super(parent, name);
    }

    protected void onLoad(Hudson root, String name) throws IOException {
        super.onLoad(root, name);

        this.builds = new RunMap<MavenBuild>();
        this.builds.load(this,new Constructor<MavenBuild>() {
            public MavenBuild create(File dir) throws IOException {
                return new MavenBuild(MavenJob.this,dir);
            }
        });
    }

    @Override
    public MavenBuild newBuild() throws IOException {
        MavenBuild lastBuild = new MavenBuild(this);
        builds.put(lastBuild);
        return lastBuild;
    }

    public JobDescriptor<MavenJob,MavenBuild> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final JobDescriptor<MavenJob,MavenBuild> DESCRIPTOR = new JobDescriptor<MavenJob,MavenBuild>(MavenJob.class) {
        public String getDisplayName() {
            return "Building Maven2 project (alpha)";
        }

        public MavenJob newInstance(String name) {
            return new MavenJob(Hudson.getInstance(),name);
        }
    };
}
