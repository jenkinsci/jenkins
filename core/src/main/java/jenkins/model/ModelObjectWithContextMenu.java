package jenkins.model;

import hudson.Functions;
import hudson.model.Action;
import hudson.model.ModelObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Flavor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@link ModelObject} that has context menu in the breadcrumb
 * 
 * This is just a marker interface. It needs 
 * @author Kohsuke Kawaguchi
 */
public interface ModelObjectWithContextMenu extends ModelObject {
    public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) throws Exception;
    
    @ExportedBean
    public class ContextMenu implements HttpResponse {
        @Exported(inline=true)
        public final List<MenuItem> items = new ArrayList<MenuItem>();
        
        public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object o) throws IOException, ServletException {
            rsp.serveExposedBean(req,this,Flavor.JSON);
        }
        
        public ContextMenu add(String url, String text) {
            items.add(new MenuItem(url,null,text));
            return this;
        }

        public ContextMenu addAll(Collection<? extends Action> actions) {
            for (Action a : actions)
                add(a);
            return this;
        }
        
        public ContextMenu add(Action a) {
            StaplerRequest req = Stapler.getCurrentRequest();
            String text = a.getDisplayName();
            String base = Functions.getIconFilePath(a);
            if (base==null)     return this;
            String icon = Stapler.getCurrentRequest().getContextPath()+(base.startsWith("images/")?Functions.getResourcePath():"")+'/'+base;

            String url =  Functions.getActionUrl(req.findAncestor(ModelObject.class).getUrl(),a);

            return add(url,icon,text);
        }
        
        public ContextMenu add(String url, String icon, String text) {
            if (text != null && icon != null && url != null)
                items.add(new MenuItem(url,icon,text));
            return this;
        }
    }

    @ExportedBean
    public class MenuItem {
        @Exported
        public String url;
        @Exported
        public String displayName;
        @Exported
        public String icon;

        public MenuItem(String url, String icon, String displayName) {
            this.url = url;
            this.icon = icon;
            this.displayName = displayName;
        }
    }
}
