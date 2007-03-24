package hudson.maven;

import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.remoting.VirtualChannel;
import hudson.util.IOException2;
import hudson.tasks.test.AbstractTestResultAction;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * {@link Build} for {@link MavenModuleSet}.
 *
 * <p>
 * A "build" of {@link MavenModuleSet} consists of:
 *
 * <ol>
 * <li>Update the workspace.
 * <li>Parse POMs
 * <li>Trigger module builds.
 * </ol>
 *
 * This object remembers the changelog and what {@link MavenBuild}s are done
 * on this.
 *  
 * @author Kohsuke Kawaguchi
 */
public final class MavenModuleSetBuild extends AbstractBuild<MavenModuleSet,MavenModuleSetBuild> {

    public MavenModuleSetBuild(MavenModuleSet job) throws IOException {
        super(job);
    }

    MavenModuleSetBuild(MavenModuleSet project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    @Override
    public AbstractTestResultAction getTestResultAction() {
        // TODO
        return null;
    }

    /**
     * Displays the combined status of all modules. 
     */
    @Override
    public Result getResult() {
        Result r = super.getResult();

        for (List<MavenBuild> list : getModuleBuilds().values())
            for (MavenBuild build : list)
                r = r.combine(build.getResult());

        return r;
    }

    /**
     * Computes the module builds that correspond to this build.
     * <p>
     * A module may be built multiple times (by the user action),
     * so the value is a list.
     */
    public Map<MavenModule,List<MavenBuild>> getModuleBuilds() {
        Collection<MavenModule> mods = getParent().getModules();

        // identify the build number range. [start,end)
        int start = getNumber();
        int end;
        MavenModuleSetBuild nb = getNextBuild();
        end = nb!=null ? nb.getNumber() : Integer.MAX_VALUE;

        // preserve the order by using LinkedHashMap
        Map<MavenModule,List<MavenBuild>> r = new LinkedHashMap<MavenModule,List<MavenBuild>>(mods.size());

        for (MavenModule m : mods) {
            List<MavenBuild> builds = new ArrayList<MavenBuild>();
            MavenBuild b = m.getNearestBuild(start);
            while(b!=null && b.getNumber()<end) {
                builds.add(b);
                b = b.getNextBuild();
            }
            r.put(m,builds);
        }

        return r;
    }

    public void run() {
        run(new RunnerImpl());
    }

    /**
     * The sole job of the {@link MavenModuleSet} build is to update SCM
     * and triggers module builds.
     */
    private class RunnerImpl extends AbstractRunner {
        protected Result doRun(final BuildListener listener) throws Exception {
            try {
                listener.getLogger().println("Parsing POMs");
                List<PomInfo> poms = project.getModuleRoot().act(new PomParser(listener,project.getRootPOM()));

                // update the module list
                Map<ModuleName,MavenModule> modules = project.modules;
                synchronized(modules) {
                    Map<ModuleName,MavenModule> old = new HashMap<ModuleName, MavenModule>(modules);

                    modules.clear();
                    project.reconfigure(poms.get(0));
                    for (PomInfo pom : poms) {
                        MavenModule mm = old.get(pom.name);
                        if(mm!=null) {// found an existing matching module
                            mm.reconfigure(pom);
                            modules.put(pom.name,mm);
                        } else {// this looks like a new module
                            listener.getLogger().println("Discovered a new module "+pom.name+" "+pom.displayName);
                            mm = new MavenModule(project,pom,getNumber());
                            modules.put(mm.getModuleName(),mm);
                        }
                        mm.save();
                    }

                    // remaining modules are no longer active.
                    old.keySet().removeAll(modules.keySet());
                    for (MavenModule om : old.values())
                        om.disable();
                    modules.putAll(old);
                }

                // we might have added new modules
                Hudson.getInstance().rebuildDependencyGraph();

                // start the build
                listener.getLogger().println("Triggering "+project.getRootModule().getModuleName());
                project.getRootModule().scheduleBuild();
                
                return null;
            } catch (AbortException e) {
                // error should have been already reported.
                return Result.FAILURE;
            } catch (IOException e) {
                e.printStackTrace(listener.error("Failed to parse POMs"));
                return Result.FAILURE;
            } catch (InterruptedException e) {
                e.printStackTrace(listener.error("Aborted"));
                return Result.FAILURE;
            } catch (RuntimeException e) {
                // bug in the code.
                e.printStackTrace(listener.error("Processing failed due to a bug in the code. Please report thus to users@hudson.dev.java.net"));
                throw e;
            }
        }

        public void post(BuildListener listener) {
        }
    }

    private static final class PomParser implements FileCallable<List<PomInfo>> {
        private final BuildListener listener;
        private final String rootPOM;

        public PomParser(BuildListener listener, String rootPOM) {
            this.listener = listener;
            this.rootPOM = rootPOM;
        }

        /**
         * Computes the path of {@link #rootPOM}.
         *
         * Returns "abc" if rootPOM="abc/pom.xml"
         * If rootPOM="pom.xml", this method returns "".
         */
        private String getRootPath() {
            int idx = Math.max(rootPOM.lastIndexOf('/'), rootPOM.lastIndexOf('\\'));
            if(idx==-1) return "";
            return rootPOM.substring(0,idx);
        }

        public List<PomInfo> invoke(File ws, VirtualChannel channel) throws IOException {
            File pom = new File(ws,rootPOM);

            if(!pom.exists()) {
                listener.getLogger().println("No such file "+pom);
                listener.getLogger().println("Perhaps you need to specify the correct POM file path in the project configuration?");
                throw new AbortException();
            }

            try {
                MavenEmbedder embedder = MavenUtil.createEmbedder(listener);
                MavenProject mp = embedder.readProject(pom);
                Map<MavenProject,String> relPath = new HashMap<MavenProject,String>();
                MavenUtil.resolveModules(embedder,mp,getRootPath(),relPath);

                List<PomInfo> infos = new ArrayList<PomInfo>();
                toPomInfo(mp,null,relPath,infos);

                for (PomInfo pi : infos)
                    pi.cutCycle();

                return infos;
            } catch (MavenEmbedderException e) {
                // TODO: better error handling needed
                throw new IOException2(e);
            } catch (ProjectBuildingException e) {
                throw new IOException2(e);
            }
        }

        private void toPomInfo(MavenProject mp, PomInfo parent, Map<MavenProject,String> relPath, List<PomInfo> infos) {
            PomInfo pi = new PomInfo(mp, parent, relPath.get(mp));
            infos.add(pi);
            for (MavenProject child : (List<MavenProject>)mp.getCollectedProjects())
                toPomInfo(child,pi,relPath,infos);
        }

        private static final long serialVersionUID = 1L;
    }

    private static class AbortException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
