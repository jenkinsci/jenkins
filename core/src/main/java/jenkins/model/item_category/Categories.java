package jenkins.model.item_category;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Flavor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * It is a logic representation of a set of {@link Category}.
 *
 * This class is not thread-safe.
 */
@ExportedBean
public class Categories implements HttpResponse, Serializable {

    private List<Category> items;

    public Categories() {
        items = new ArrayList<Category>();
    }

    @Exported(name = "categories")
    public List<Category> getItems() {
        return items;
    }

    @Override
    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
        rsp.serveExposedBean(req, this, Flavor.JSON);
    }

    @CheckForNull
    public Category getItem(@Nonnull String id) {
        for (Category category : items) {
            if (category.getId().equals(id)) {
                return category;
            }
        }
        return null;
    }

}
