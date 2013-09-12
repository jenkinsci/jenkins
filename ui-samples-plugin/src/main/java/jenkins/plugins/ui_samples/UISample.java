package jenkins.plugins.ui_samples;

import static org.apache.commons.io.IOUtils.copy;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Action;
import hudson.model.Describable;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class UISample implements ExtensionPoint, Action, Describable<UISample> {
    public String getIconFileName() {
        return "gear.png";
    }

    public String getUrlName() {
        return getClass().getSimpleName();
    }

    /**
     * Default display name.
     */
    public String getDisplayName() {
        return getClass().getSimpleName();
    }

    /**
     * Source files associated with this sample.
     */
    public List<SourceFile> getSourceFiles() {
        List<SourceFile> r = new ArrayList<SourceFile>();

        r.add(new SourceFile(getClass().getSimpleName()+".java"));
        for (String name : new String[]{"index.jelly","index.groovy"}) {
            SourceFile s = new SourceFile(name);
            if (s.resolve()!=null)
                r.add(s);
        }
        return r;
    }

    /**
     * Binds {@link SourceFile}s into URL.
     */
    public void doSourceFile(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String name = req.getRestOfPath().substring(1); // Remove leading /
        for (SourceFile sf : getSourceFiles())
            if (sf.name.equals(name)) {
                sf.doIndex(rsp);
                return;
            }
        rsp.sendError(rsp.SC_NOT_FOUND);
    }

    /**
     * Returns a paragraph of natural text that describes this sample.
     * Interpreted as HTML.
     */
    public abstract String getDescription();

    public UISampleDescriptor getDescriptor() {
        return (UISampleDescriptor) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Returns all the registered {@link UISample}s.
     */
    public static ExtensionList<UISample> all() {
        return Jenkins.getInstance().getExtensionList(UISample.class);
    }

    public static List<UISample> getGroovySamples() {
        List<UISample> r = new ArrayList<UISample>();
        for (UISample uiSample : UISample.all()) {
            for (SourceFile src : uiSample.getSourceFiles()) {
                if (src.name.contains("groovy")) {
                    r.add(uiSample);
                    break;
                }
            }
        }
        return r;
    }

    public static List<UISample> getOtherSamples() {
        List<UISample> r = new ArrayList<UISample>();
        OUTER:
        for (UISample uiSample : UISample.all()) {
            for (SourceFile src : uiSample.getSourceFiles()) {
                if (src.name.contains("groovy")) {
                    continue OUTER;
                }
            }
            r.add(uiSample);
        }
        return r;
    }

    /**
     * @author Kohsuke Kawaguchi
     */
    public class SourceFile {
        public final String name;

        public SourceFile(String name) {
            this.name = name;
        }

        public URL resolve() {
            return UISample.this.getClass().getResource(
                (name.endsWith(".jelly") || name.endsWith(".groovy")) ? UISample.this.getClass().getSimpleName()+"/"+name : name);
        }

        /**
         * Serves this source file.
         */
        public void doIndex(StaplerResponse rsp) throws IOException {
            rsp.setContentType("text/plain;charset=UTF-8");
            copy(resolve().openStream(),rsp.getOutputStream());
        }
    }

}
