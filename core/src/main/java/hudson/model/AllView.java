package hudson.model;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;

import hudson.model.Descriptor.FormException;

/**
 * {@link View} that contains everything.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.269
 */
public class AllView extends View {
    @DataBoundConstructor
    public AllView(String name) {
        super(name);
    }

    @Override
    public boolean contains(TopLevelItem item) {
        return true;
    }

    @Override
    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException {
        return Hudson.getInstance().doCreateItem(req, rsp);
    }

    @Override
    public Collection<TopLevelItem> getItems() {
        return Hudson.getInstance().getItems();
    }

    @Override
    public String getPostConstructLandingPage() {
        return ""; // there's no configuration page
    }

    @Override
    public void onJobRenamed(Item item, String oldName, String newName) {
        // noop
    }

    @Override
    protected void submit(StaplerRequest req) throws IOException, ServletException, FormException {
        // noop
    }

    public ViewDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    static {
        LIST.add(DESCRIPTOR);
    }

    public static final class DescriptorImpl extends ViewDescriptor {
        private DescriptorImpl() {
            super(AllView.class);
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }

        public String getDisplayName() {
            return Messages.Hudson_ViewName();
        }
    }
}
