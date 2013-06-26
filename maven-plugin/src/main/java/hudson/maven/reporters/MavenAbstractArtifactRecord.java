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
import hudson.maven.MavenEmbedder;
import hudson.maven.MavenEmbedderException;
import hudson.maven.MavenUtil;
import hudson.maven.RedeployPublisher.WrappedArtifactRepository;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Api;
import hudson.model.BallColor;
import hudson.model.BuildBadgeAction;
import hudson.model.Result;
import hudson.model.TaskAction;
import hudson.model.TaskListener;
import hudson.model.TaskThread;
import hudson.model.TaskThread.ListenerAndText;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.util.Iterators;
import hudson.widgets.HistoryWidget;
import hudson.widgets.HistoryWidget.Adapter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.servlet.ServletException;

import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

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
        return "redo.png";
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
        return records.get(Integer.valueOf(token));
    }

    /**
     * Performs a redeployment.
     */
    @RequirePOST
    public final HttpResponse doRedeploy(
            @QueryParameter("_.id") final String id,
            @QueryParameter("_.url") final String repositoryUrl,
            @QueryParameter("_.uniqueVersion") final boolean uniqueVersion) throws ServletException, IOException {
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
                } finally {
                    if(record.result==null)
                        record.result = Result.FAILURE;
                    // persist the record
                    getBuild().save();
                }
            }
        }.start();

        return HttpRedirect.DOT;
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
