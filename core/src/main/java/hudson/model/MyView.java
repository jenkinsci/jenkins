package hudson.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link View} that only contains projects for which the current user has access to.
 *
 * @since 1.220
 * @author Tom Huybrechts
 */
public class MyView extends View {
    private final Hudson owner;

    @DataBoundConstructor
    public MyView(String name) {
        super(name);
        this.owner = Hudson.getInstance();
    }

    @Override
    public boolean contains(TopLevelItem item) {
        return item.hasPermission(Hudson.ADMINISTER);
    }

    @Override
    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException {
        return owner.doCreateItem(req, rsp);
    }

    @Override
    public TopLevelItem getItem(String name) {
        return owner.getItem(name);
    }

    @Override
    public Collection<TopLevelItem> getItems() {
        List<TopLevelItem> items = new ArrayList<TopLevelItem>();
        for (TopLevelItem item : owner.getItems()) {
            if (item.hasPermission(Job.CONFIGURE)) {
                items.add(item);
            }
        }
        return Collections.unmodifiableList(items);
    }

    @Override
    public String getPostConstructLandingPage() {
        return ""; // there's no configuration page
    }

    @Override
    public void onJobChange(Item item, String oldName, String newName) {
        // noop
    }

    public TopLevelItem getJob(String name) {
        return getItem(name);
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
            super(MyView.class);
        }

        public String getDisplayName() {
            return "My View";
        }
    }
}
