package jenkins.model.ItemCategory;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Flavor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * It is a logic representation of a set of {$Category}.
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
}
