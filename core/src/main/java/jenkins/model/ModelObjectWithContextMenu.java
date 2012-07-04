package jenkins.model;

import hudson.Functions;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.ModelObject;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.JellyException;
import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.Script;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Flavor;
import org.kohsuke.stapler.jelly.JellyClassTearOff;
import org.kohsuke.stapler.jelly.JellyFacet;
import org.xml.sax.helpers.DefaultHandler;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@link ModelObject} that has context menu in the breadcrumb.
 * 
 * <p>
 * When the user is visiting a particular page, all the ancestor objects that has {@link ModelObject}
 * appears in the breadcrumbs. Among those which that also implements {@link ModelObjectWithContextMenu}
 * shows the drop-down menu for providing quicker access to the actions to those objects.
 *     
 * @author Kohsuke Kawaguchi
 */
public interface ModelObjectWithContextMenu extends ModelObject {
    /**
     * Generates the context menu.
     * 
     * The typical implementation is {@code return new ContextMenu().from(this,request,response);},
     * which implements the default behaviour. See {@link ContextMenu#from(ModelObjectWithContextMenu, StaplerRequest, StaplerResponse)}
     * for more details of what it does. This should suit most implementations.
     */
    public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) throws Exception;

    /**
     * Data object that represents the context menu.
     * 
     * Via {@link HttpResponse}, this class is capable of converting itself to JSON that &lt;l:breadcrumb/> understands.
     */
    @ExportedBean
    public class ContextMenu implements HttpResponse {
        /**
         * The actual contents of the menu.
         */
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

        /**
         * Default implementation of the context menu generation.
         * 
         * <p>
         * This method uses {@code sidepanel.groovy} to run the side panel generation, captures
         * the use of &lt;l:task> tags, and then converts those into {@link MenuItem}s. This is
         * supposed to make this work with most existing {@link ModelObject}s that follow the standard
         * convention.
         * 
         * <p>
         * Unconventional {@link ModelObject} implementations that do not use {@code sidepanel.groovy}
         * can override {@link ModelObjectWithContextMenu#doContextMenu(StaplerRequest, StaplerResponse)}
         * directly to provide alternative semantics.
         */
        public ContextMenu from(ModelObjectWithContextMenu self, StaplerRequest request, StaplerResponse response) throws JellyException, IOException {
            WebApp webApp = WebApp.getCurrent();
            final Script s = webApp.getMetaClass(self).getTearOff(JellyClassTearOff.class).findScript("sidepanel");
            if (s!=null) {
                JellyFacet facet = webApp.getFacet(JellyFacet.class);
                request.setAttribute("taskTags",this); // <l:task> will look for this variable and populate us
                request.setAttribute("mode","side-panel");
                // run sidepanel but ignore generated HTML
                facet.scriptInvoker.invokeScript(request,response,new Script() {
                    public Script compile() throws JellyException {
                        return this;
                    }

                    public void run(JellyContext context, XMLOutput output) throws JellyTagException {
                        Functions.initPageVariables(context);
                        s.run(context,output);
                    }
                },self,new XMLOutput(new DefaultHandler()));
            } else
            if (self instanceof Actionable) {
                // fallback
                this.addAll(((Actionable)self).getActions());
            }
    
            return this;
        }
    }

    /**
     * Menu item in {@link ContextMenu}
     */
    @ExportedBean
    public class MenuItem {
        /**
         * Target of the link.
         *
         * This can start with '/', but it must not be a relative URL, since
         * you cannot really tell which page this context menu is used.
         */
        @Exported
        public String url;

        /**
         * Human readable caption of the menu item. Do not use HTML.
         */
        @Exported
        public String displayName;

        /**
         * Optional URL to the icon image. Rendered as 24x24.
         */
        @Exported
        public String icon;

        /**
         * If this is a submenu, definition of subitems.
         */
        @Exported(inline=true)
        public ContextMenu subMenu;

        public MenuItem(String url, String icon, String displayName) {
            this.url = URI.create(Stapler.getCurrentRequest().getRequestURI()).resolve(url).toString();
            this.icon = icon;
            this.displayName = Util.escape(displayName);
        }
    }
}
