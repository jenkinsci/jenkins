/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe
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

import hudson.console.AnnotatedLargeText;
import hudson.maven.*;
import hudson.maven.RedeployPublisher.WrappedArtifactRepository;
import hudson.model.*;
import hudson.model.TaskThread.ListenerAndText;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.util.Iterators;
import hudson.widgets.HistoryWidget;
import hudson.widgets.HistoryWidget.Adapter;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * UI to redeploy artifacts after the fact.
 *
 * <p>
 * There are two types &mdash; one for the module, the other for the whole project.
 * The semantics specific to these cases are defined in subtypes.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class MavenAbstractArtifactRecord<T extends AbstractBuild<?,?>> extends TaskAction implements BuildBadgeAction {
    @ExportedBean
    public final class Record {
        /**
         * Repository URL that artifacts were deployed.
         */
        @Exported
        public final String url;

        /**
         * Log file name. Relative to {@link AbstractBuild#getRootDir()}.
         */
        private final String fileName;

        /**
         * Status of this record.
         */
        private Result result;

        private final Calendar timeStamp;

        public Record(String url, String fileName) {
            this.url = url;
            this.fileName = fileName;
            timeStamp = new GregorianCalendar();
        }

        /**
         * Returns the log of this deployment record.
         */
        public AnnotatedLargeText<Record> getLog() {
            return new AnnotatedLargeText<Record>(new File(getBuild().getRootDir(),fileName), Charset.defaultCharset(), true, this);
        }

        /**
         * Result of the deployment. During the build, this value is null.
         */
        @Exported
        public Result getResult() {
            return result;
        }

        @Exported
        public int getNumber() {
            return records.indexOf(this);
        }

        @Exported
        public boolean isBuilding() {
            return result==null;
        }

        @Exported
        public Calendar getTimestamp() {
            return (Calendar) timeStamp.clone();
        }

        @Exported
        public String getBuildStatusUrl() {
            return getIconColor().getImage();
        }

        @Exported
        public BallColor getIconColor() {
            if(result==null)
                return BallColor.GREY_ANIME;
            else
                return result.color;
        }

        // TODO: Eventually provide a better UI
        public void doIndex(StaplerResponse rsp) throws IOException {
            rsp.setContentType("text/plain;charset=UTF-8");
            getLog().writeLogTo(0,rsp.getWriter());
        }
    }

    /**
     * Records of a deployment.
     */
    @Exported
    public final CopyOnWriteArrayList<Record> records = new CopyOnWriteArrayList<Record>();

    /**
     * Gets the parent build object to which this record is registered.
     */
    public abstract T getBuild();

    protected ACL getACL() {
        return getBuild().getACL();
    }

    public final String getIconFileName() {
        return "redo.gif";
    }

    public final String getDisplayName() {
        return Messages.MavenAbstractArtifactRecord_Displayname();
    }

    public final String getUrlName() {
        return "redeploy";
    }

    protected Permission getPermission() {
        return REDEPLOY;
    }

    public Api getApi() {
        return new Api(this);
    }

    public boolean hasBadge() {
        if (records != null) {
            for (final Record record : records) {
                if (Result.SUCCESS.equals(record.result)) 
                    return true;
            }
        }
        return false;
    }

    public HistoryWidgetImpl getHistoryWidget() {
        return new HistoryWidgetImpl();
    }

    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        try {
            return records.get(Integer.valueOf(token));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Performs a redeployment.
     */
    public final HttpResponse doRedeploy(
            @QueryParameter("_.id") final String id,
            @QueryParameter("_.url") final String repositoryUrl,
            @QueryParameter("_.uniqueVersion") final boolean uniqueVersion,
            @QueryParameter("_.changeBuildStatus") final boolean changeBuildStatus) throws ServletException, IOException {
        getACL().checkPermission(REDEPLOY);

        File logFile = new File(getBuild().getRootDir(),"maven-deployment."+records.size()+".log");
        final Record record = new Record(repositoryUrl, logFile.getName());
        records.add(record);

        new TaskThread(this,ListenerAndText.forFile(logFile,this)) {
            protected void perform(TaskListener listener) throws Exception {
                try {
                    MavenEmbedder embedder = MavenUtil.createEmbedder(listener,getBuild());
                    ArtifactRepositoryLayout layout =
                        embedder.lookup( ArtifactRepositoryLayout.class,"default");
                    ArtifactRepositoryFactory factory =
                        (ArtifactRepositoryFactory) embedder.lookup(ArtifactRepositoryFactory.ROLE);

                    ArtifactRepository repository = factory.createDeploymentArtifactRepository(
                            id, repositoryUrl, layout, uniqueVersion);
                    WrappedArtifactRepository repo = new WrappedArtifactRepository(repository, uniqueVersion);
                    deploy(embedder,repo,listener);

                    record.result = Result.SUCCESS;
                } catch (MavenEmbedderException e) {
                    e.printStackTrace(listener.error(e.getMessage()));
                } catch (ComponentLookupException e) {
                    e.printStackTrace(listener.error(e.getMessage()));
                } catch (ArtifactDeploymentException e) {
                    e.printStackTrace(listener.error(e.getMessage()));
                } finally {
                    if(record.result==null)
                        record.result = Result.FAILURE;
                    listener.getLogger().println("[INFO] Finished. Status: " + record.result);

                    // Update Maven build status according to redeploy status if checkBuildStatus checkbox is set
                    if (changeBuildStatus && record.result.equals(Result.SUCCESS)) {
                        // Since we can redeploy failed build due to redeploy problem or user want to redeploy specific artifact,
                        // need to calculate result from the build itself (cycle between all modules and check it's status)
                        Result buildResult = calculateBuildStatus(getBuild(), listener);
                        getBuild().setResultForce(buildResult);
                        listener.getLogger().println("[INFO] Changed build status to " + buildResult);
                    }

                    // persist the record
                    getBuild().save();
                }
            }
        }.start();

        return HttpRedirect.DOT;
    }

    private Result calculateBuildStatus(T build, TaskListener listener) {
        // check if we want to deploy multimodule build
        if (build instanceof MavenModuleSetBuild) {
            // in this case we calculate build result from all modules in the build
            MavenModuleSetBuild mavenBuild = (MavenModuleSetBuild) build;

            // get all records about all maven modules in the build
            List<MavenAbstractArtifactRecord> actions = new ArrayList<MavenAbstractArtifactRecord>();
            for (Map.Entry<MavenModule, MavenBuild> e : mavenBuild.getModuleLastBuilds().entrySet()) {
                MavenAbstractArtifactRecord a = e.getValue().getAction( MavenAbstractArtifactRecord.class );
                if (a == null) {
                    listener.getLogger().println("No artifacts are recorded for module" + e.getKey().getName() + ". Is this a Maven project?");
                } else {
                    actions.add( a );
                }
            }

            // If one of module have status ABORTED, FAILURE or UNSTABLE return this status as status of whole build
            // else return SUCCESS
            for (MavenAbstractArtifactRecord module : actions) {
                if (module.getBuild().getResult().equals(Result.ABORTED) ||
                    module.getBuild().getResult().equals(Result.FAILURE) ||
                    module.getBuild().getResult().equals(Result.UNSTABLE)
                   ) {
                    return module.getBuild().getResult();
                }
            }
            return Result.SUCCESS;
        }
        return build.getResult();
    }

    /**
     * Deploys the artifacts to the specified {@link ArtifactRepository}.
     *
     * @param embedder
     *      This component hosts all the Maven components we need to do the work.
     * @param deploymentRepository
     *      The remote repository to deploy to.
     * @param listener
     *      The status and error goes to this listener.
     */
    public abstract void deploy(MavenEmbedder embedder, ArtifactRepository deploymentRepository, TaskListener listener) throws MavenEmbedderException, IOException, ComponentLookupException, ArtifactDeploymentException;

    private final class HistoryWidgetImpl extends HistoryWidget<MavenAbstractArtifactRecord,Record> {
        private HistoryWidgetImpl() {
            super(MavenAbstractArtifactRecord.this, Iterators.reverse(records), ADAPTER);
        }

        public String getDisplayName() {
            return Messages.HistoryWidgetImpl_Displayname();
        }
    }

    private static final Adapter<MavenAbstractArtifactRecord<?>.Record> ADAPTER = new Adapter<MavenAbstractArtifactRecord<?>.Record>() {
        public int compare(MavenAbstractArtifactRecord<?>.Record record, String key) {
            return record.getNumber()-Integer.parseInt(key);
        }

        public String getKey(MavenAbstractArtifactRecord<?>.Record record) {
            return String.valueOf(record.getNumber());
        }

        public boolean isBuilding(MavenAbstractArtifactRecord<?>.Record record) {
            return record.isBuilding();
        }

        public String getNextKey(String key) {
            return String.valueOf(Integer.parseInt(key)+1);
        }
    };


    /**
     * Permission for redeploying artifacts.
     */
    public static final Permission REDEPLOY = AbstractProject.BUILD;

    /**
     * Debug probe for HUDSON-1461.
     */
    public static boolean debug = Boolean.getBoolean(MavenArtifactRecord.class.getName()+".debug");
}
