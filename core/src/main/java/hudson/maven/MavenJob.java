package hudson.maven;

import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.JobDescriptor;
import hudson.model.RunMap;
import hudson.model.RunMap.Constructor;
import hudson.model.TaskListener;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderException;

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

    /**
     * Creates a fresh {@link MavenEmbedder} instance.
     *
     * @param listener
     *      This is where the log messages from Maven will be recorded.
     */
    public MavenEmbedder createEmbedder(TaskListener listener) throws MavenEmbedderException {
        MavenEmbedder maven = new MavenEmbedder();

        maven.setClassLoader(Thread.currentThread().getContextClassLoader());
        maven.setLogger( new EmbedderLoggerImpl(listener) );

        maven.start();

        return maven;
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

    static {
        XSTREAM.alias("maven2", MavenJob.class);
    }
}
