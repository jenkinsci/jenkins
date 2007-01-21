package hudson.maven;

import hudson.model.TaskListener;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
class MavenUtil {
    /**
     * Creates a fresh {@link MavenEmbedder} instance.
     *
     * @param listener
     *      This is where the log messages from Maven will be recorded.
     */
    public static MavenEmbedder createEmbedder(TaskListener listener) throws MavenEmbedderException {
        MavenEmbedder maven = new MavenEmbedder();

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        maven.setClassLoader(cl);
        maven.setLogger( new EmbedderLoggerImpl(listener) );
        // if we let Plexus find components, there's no guaranteed ordering,
        // so Plexus may well find the DefaultPluginManager from maven.jar instead of
        // our override. So use this mechanism to make sure ours are loaded first
        // before Plexus goes service loader discovery.
        maven.setOverridingComponentsXml(cl.getResource("META-INF/plexus/hudson-components.xml"));

        maven.start();

        return maven;
    }

    /**
     * Recursively resolves module POMs that are referenced from
     * the given {@link MavenProject} and parses them into
     * {@link MavenProject}s.
     */
    public static void resolveModules(MavenEmbedder embedder, MavenProject project) throws ProjectBuildingException {

        File basedir = project.getFile().getParentFile();

        List<MavenProject> modules = new ArrayList<MavenProject>();

        for (String modulePath : (List<String>) project.getModules()) {
            File moduleFile = new File(new File(basedir, modulePath),"pom.xml");

            MavenProject child = embedder.readProject(moduleFile);
            resolveModules(embedder,child);
            modules.add(child);
        }

        project.setCollectedProjects(modules);
    }
}
