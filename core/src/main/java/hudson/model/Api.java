package hudson.model;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.Flavor;
import org.kohsuke.stapler.export.SchemaGenerator;
import org.kohsuke.stapler.export.ModelBuilder;

import javax.servlet.ServletException;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;

/**
 * Used to expose remote access API for ".../api/"
 *
 * @author Kohsuke Kawaguchi
 * @see Exported
 */
public class Api extends AbstractModelObject {
    /**
     * Model object to be exposed as XML/JSON/etc.
     */
    private final Object bean;

    public Api(Object bean) {
        this.bean = bean;
    }

    public String getDisplayName() {
        return "API";
    }

    /**
     * Exposes the bean as XML.
     */
    public void doXml(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rsp.serveExposedBean(req,bean, Flavor.XML);
    }

    /**
     * Generate schema.
     */
    public void doSchema(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rsp.setContentType("application/xml");
        StreamResult r = new StreamResult(rsp.getOutputStream());
        new SchemaGenerator(new ModelBuilder().get(bean.getClass())).generateSchema(r);
        r.getOutputStream().close();
    }

    /**
     * Exposes the bean as JSON.
     */
    public void doJson(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rsp.serveExposedBean(req,bean, Flavor.JSON);
    }
}
