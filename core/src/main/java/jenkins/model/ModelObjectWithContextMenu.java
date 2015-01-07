package jenkins.model;

import hudson.Functions;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.BallColor;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Node;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.JellyException;
import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.Script;
import org.apache.commons.jelly.XMLOutput;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
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
import java.net.URISyntaxException;
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
 * @see ModelObjectWithChildren
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

        /**
         * @see ContextMenuVisibility
         */
        public ContextMenu add(Action a) {
            if (!Functions.isContextMenuVisible(a)) {
                return this;
            }
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

        /** @since 1.504 */
        public ContextMenu add(String url, String icon, String text, boolean post) {
            if (text != null && icon != null && url != null) {
                MenuItem item = new MenuItem(url,icon,text);
                item.post = post;
                items.add(item);
            }
            return this;
        }

        /** @since 1.512 */
        public ContextMenu add(String url, String icon, String text, boolean post, boolean requiresConfirmation) {
            if (text != null && icon != null && url != null) {
                MenuItem item = new MenuItem(url,icon,text);
                item.post = post;
                item.requiresConfirmation = requiresConfirmation;
                items.add(item);
            }
            return this;
        }

        /**
         * Adds a manually constructed {@link MenuItem}
         *
         * @since 1.513
         */
        public ContextMenu add(MenuItem item) {
            items.add(item);
            return this;
        }

        /**
         * Adds a node
         *
         * @since 1.513
         */
        public ContextMenu add(Node n) {
            Computer c = n.toComputer();
            return add(new MenuItem()
                .withDisplayName(n.getDisplayName())
                .withStockIcon((c==null) ? "computer.png" : c.getIcon())
                .withContextRelativeUrl(n.getSearchUrl()));
        }

        /**
         * Adds a computer
         *
         * @since 1.513
         */
        public ContextMenu add(Computer c) {
            return add(new MenuItem()
                .withDisplayName(c.getDisplayName())
                .withStockIcon(c.getIcon())
                .withContextRelativeUrl(c.getUrl()));
        }

        /**
         * Adds a child item when rendering context menu of its parent.
         *
         * @since 1.513
         */
        public ContextMenu add(Job job) {
            return add(new MenuItem()
                .withDisplayName(job.getDisplayName())
                .withIcon(job.getIconColor())
                .withUrl(job.getSearchUrl()));
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
            return from(self,request,response,"sidepanel");
        }

        public ContextMenu from(ModelObjectWithContextMenu self, StaplerRequest request, StaplerResponse response, String view) throws JellyException, IOException {
            WebApp webApp = WebApp.getCurrent();
            final Script s = webApp.getMetaClass(self).getTearOff(JellyClassTearOff.class).findScript(view);
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
                this.addAll(((Actionable)self).getAllActions());
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
         * Icon class specification.
         */
        @Exported
        public String iconClassSpec;

        /**
         * Use pure CSS for icon rendering.
         */
        @Exported
        public boolean useCSSIconRendering = false;

        /**
         * True to make a POST request rather than GET.
         * @since 1.504
         */
        @Exported public boolean post;

        /**
         * True to require confirmation after a click.
         * @since 1.512
         */
        @Exported public boolean requiresConfirmation;

        /**
         * If this is a submenu, definition of subitems.
         */
        @Exported(inline=true)
        public ContextMenu subMenu;

        /**
         * Menu item icon definition.
         */
        private Icon iconDef;

        public MenuItem(String url, String icon, String displayName) {
            withUrl(url).withIcon(icon).withDisplayName(displayName);
        }

        public MenuItem() {
        }

        public MenuItem withUrl(String url) {
            try {
                this.url = new URI(Stapler.getCurrentRequest().getRequestURI()).resolve(new URI(url)).toString();
            } catch (URISyntaxException x) {
                throw new IllegalArgumentException("Bad URI from " + Stapler.getCurrentRequest().getRequestURI() + " vs. " + url, x);
            }
            return this;
        }

        /**
         * Sets the URL by passing in a URL relative to the context path of Jenkins
         */
        public MenuItem withContextRelativeUrl(String url) {
            if (!url.startsWith("/"))   url = '/'+url;
            this.url = Stapler.getCurrentRequest().getContextPath()+url;
            return this;
        }

        public MenuItem withIcon(String icon) {
            this.icon = icon;

            iconDef = IconSet.icons.getIconByClassSpec(icon);
            if (iconDef != null) {
                // The icon was actually specified using a CSS selector (Vs an img url).
                // Resolve the "classic" icon theme impl img URL.
                this.icon = iconDef.getQualifiedUrl();
            } else {
                iconDef = IconSet.icons.getIconByUrl(icon);
            }

            if (iconDef != null) {
                iconClassSpec = iconDef.getClassSpec();
                useCSSIconRendering = iconDef.isUseCSSRendering();
            }

            return this;
        }

        public MenuItem withIcon(BallColor color) {
            return withStockIcon(color.getImage());
        }

        /**
         * Sets the icon from core's stock icon
         *
         * @param icon
         *      String like "gear.png" that resolves to 24x24 stock icon in the core
         */
        public MenuItem withStockIcon(String icon) {
            this.icon = Stapler.getCurrentRequest().getContextPath() + Jenkins.RESOURCE_PATH + "/images/24x24/"+icon;
            return this;
        }

        public MenuItem withDisplayName(String displayName) {
            this.displayName = Util.escape(displayName);
            return this;
        }

        public MenuItem withDisplayName(ModelObject o) {
            return withDisplayName(o.getDisplayName());
        }
    }

    /**
     * Allows an action to decide whether it will be visible in a context menu.
     * @since 1.538
     */
    interface ContextMenuVisibility extends Action {

        /**
         * Determines whether to show this action right now.
         * Can always return false, for an action which should never be in the context menu;
         * or could examine {@link Stapler#getCurrentRequest}.
         * @return true to display it, false to hide
         * @see ContextMenu#add(Action)
         */
        boolean isVisible();

    }

}
