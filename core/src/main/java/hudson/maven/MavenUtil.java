package hudson.maven;

import hudson.model.TaskListener;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        ClassLoader cl = MavenUtil.class.getClassLoader();
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
     *
     * @param rel
     *      Used to compute the relative path. Pass in "" to begin.
     * @param relativePathInfo
     *      Upon the completion of this method, this variable stores the relative path
     *      from the root directory of the given {@link MavenProject} to the root directory
     *      of each of the newly parsed {@link MavenProject}.
     */
    public static void resolveModules(MavenEmbedder embedder, MavenProject project, String rel, Map<MavenProject,String> relativePathInfo) throws ProjectBuildingException {

        File basedir = project.getFile().getParentFile();
        relativePathInfo.put(project,rel);

        List<MavenProject> modules = new ArrayList<MavenProject>();

        for (String modulePath : (List<String>) project.getModules()) {
            File moduleFile = new File(new File(basedir, modulePath),"pom.xml");

            String relativePath = rel;
            if(relativePath.length()>0) relativePath+='/';
            relativePath+=modulePath;

            MavenProject child = embedder.readProject(moduleFile);
            resolveModules(embedder,child,relativePath,relativePathInfo);
            modules.add(child);
        }

        project.setCollectedProjects(modules);
    }
}
