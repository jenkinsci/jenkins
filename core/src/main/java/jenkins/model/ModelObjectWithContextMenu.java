package jenkins.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Functions;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.BallColor;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Node;
import hudson.slaves.Cloud;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jenkins.management.Badge;
import jenkins.security.stapler.StaplerNotDispatchable;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.JellyException;
import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.Script;
import org.apache.commons.jelly.XMLOutput;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.symbol.Symbol;
import org.jenkins.ui.symbol.SymbolRequest;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Flavor;
import org.kohsuke.stapler.jelly.JellyClassTearOff;
import org.kohsuke.stapler.jelly.JellyFacet;
import org.xml.sax.helpers.DefaultHandler;

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
     * which implements the default behaviour. See {@link ContextMenu#from(ModelObjectWithContextMenu, StaplerRequest2, StaplerResponse2)}
     * for more details of what it does. This should suit most implementations.
     */
    default ContextMenu doContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        if (Util.isOverridden(ModelObjectWithContextMenu.class, getClass(), "doContextMenu", StaplerRequest.class, StaplerResponse.class)) {
            return doContextMenu(StaplerRequest.fromStaplerRequest2(request), StaplerResponse.fromStaplerResponse2(response));
        } else {
            throw new AbstractMethodError("The class " + getClass().getName() + " must override at least one of the "
                    + ModelObjectWithContextMenu.class.getSimpleName() + ".doContextMenu methods");
        }
    }

    /**
     * @deprecated use {@link #doContextMenu(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    default ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        if (Util.isOverridden(ModelObjectWithContextMenu.class, getClass(), "doContextMenu", StaplerRequest2.class, StaplerResponse2.class)) {
            return doContextMenu(StaplerRequest.toStaplerRequest2(request), StaplerResponse.toStaplerResponse2(response));
        } else {
            throw new AbstractMethodError("The class " + getClass().getName() + " must override at least one of the "
                    + ModelObjectWithContextMenu.class.getSimpleName() + ".doContextMenu methods");
        }
    }

    /**
     * Data object that represents the context menu.
     *
     * Via {@link HttpResponse}, this class is capable of converting itself to JSON that {@code <l:breadcrumb/>} understands.
     */
    @ExportedBean
    class ContextMenu implements HttpResponse {
        /**
         * The actual contents of the menu.
         */
        @Exported(inline = true)
        public final List<MenuItem> items = new ArrayList<>();

        @Override
        public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object o) throws IOException, ServletException {
            rsp.serveExposedBean(req, this, Flavor.JSON);
        }

        public ContextMenu add(String url, String text) {
            items.add(new MenuItem(url, null, text));
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
            StaplerRequest2 req = Stapler.getCurrentRequest2();
            String text = a.getDisplayName();
            String base = Functions.getIconFilePath(a);
            if (base == null)     return this;
            String url =  Functions.getActionUrl(req.findAncestor(ModelObject.class).getUrl(), a);

            if (base.startsWith("symbol-")) {
                Icon icon = Functions.tryGetIcon(base);
                return add(url, icon.getClassSpec(), text);
            } else {
                String icon = Stapler.getCurrentRequest2().getContextPath() + (base.startsWith("images/") ? Functions.getResourcePath() : "") + '/' + base;
                return add(url, icon, text);
            }
        }

        public ContextMenu add(String url, String icon, String text) {
            if (text != null && icon != null && url != null)
                items.add(new MenuItem(url, icon, text));
            return this;
        }

        /** @since 1.504 */
        public ContextMenu add(String url, String icon, String text, boolean post) {
            if (text != null && icon != null && url != null) {
                MenuItem item = new MenuItem(url, icon, text);
                item.post = post;
                items.add(item);
            }
            return this;
        }

        /** @since 1.512 */
        public ContextMenu add(String url, String icon, String text, boolean post, boolean requiresConfirmation) {
            if (text != null && icon != null && url != null) {
                MenuItem item = new MenuItem(url, icon, text);
                item.post = post;
                item.requiresConfirmation = requiresConfirmation;
                items.add(item);
            }
            return this;
        }

        /** @since 2.335 */
        public ContextMenu add(String url, String icon, String iconXml, String text, boolean post, boolean requiresConfirmation) {
            if (text != null && icon != null && url != null) {
                MenuItem item = new MenuItem(url, icon, text);
                item.iconXml = iconXml;
                item.post = post;
                item.requiresConfirmation = requiresConfirmation;
                items.add(item);
            }
            return this;
        }

        /** @since 2.401 */
        public ContextMenu add(String url, String icon, String iconXml, String text, boolean post, boolean requiresConfirmation, Badge badge) {
            if (text != null && icon != null && url != null) {
                MenuItem item = new MenuItem(url, icon, text);
                item.iconXml = iconXml;
                item.post = post;
                item.requiresConfirmation = requiresConfirmation;
                item.badge = badge;
                items.add(item);
            }
            return this;
        }

        /** @since 2.415 */
        public ContextMenu add(String url, String icon, String iconXml, String text, boolean post, boolean requiresConfirmation, Badge badge, String message) {
            if (text != null && icon != null && url != null) {
                MenuItem item = new MenuItem(url, icon, text);
                item.iconXml = iconXml;
                item.post = post;
                item.requiresConfirmation = requiresConfirmation;
                item.badge = badge;
                item.message = message;
                items.add(item);
            }
            return this;
        }

        /**
         * Add a header row (no icon, no URL, rendered in header style).
         *
         * @since 2.231
         */
        @Restricted(DoNotUse.class) // manage.jelly only
        public ContextMenu addHeader(String title) {
            final MenuItem item = new MenuItem().withDisplayName(title);
            item.type = MenuItemType.HEADER;
            return add(item);
        }

        /**
         * Add a separator row (no icon, no URL, no text).
         *
         * @since 2.340
         */
        public ContextMenu addSeparator() {
            final MenuItem item = new MenuItem();
            item.type = MenuItemType.SEPARATOR;
            return add(item);
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
                .withStockIcon(c == null ? "computer.svg" : c.getIcon())
                .withContextRelativeUrl(n.getSearchUrl()));
        }

        /**
         * Adds a computer
         *
         * @since 1.513
         * @deprecated use {@link #add(IComputer)} instead.
         */
        @Deprecated(since = "TODO")
        public ContextMenu add(Computer c) {
            return add((IComputer) c);
        }

        /**
         * Adds a {@link IComputer} instance.
         * @param c the computer to add to the menu
         * @return this
         * @since TODO
         */
        public ContextMenu add(IComputer c) {
            return add(new MenuItem()
                .withDisplayName(c.getDisplayName())
                .withIconClass(c.getIconClassName())
                .withContextRelativeUrl(c.getUrl()));
        }

        public ContextMenu add(Cloud c) {
            return add(new MenuItem()
                    .withDisplayName(c.getDisplayName())
                    .withIconClass(c.getIconClassName())
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
         * the use of {@code <l:task>} tags, and then converts those into {@link MenuItem}s. This is
         * supposed to make this work with most existing {@link ModelObject}s that follow the standard
         * convention.
         *
         * <p>
         * Unconventional {@link ModelObject} implementations that do not use {@code sidepanel.groovy}
         * can override {@link ModelObjectWithContextMenu#doContextMenu(StaplerRequest2, StaplerResponse2)}
         * directly to provide alternative semantics.
         */
        public ContextMenu from(ModelObjectWithContextMenu self, StaplerRequest2 request, StaplerResponse2 response) throws JellyException, IOException {
            return from(self, request, response, "sidepanel");
        }

        public ContextMenu from(ModelObjectWithContextMenu self, StaplerRequest request, StaplerResponse response) throws JellyException, IOException {
            return from(self, StaplerRequest.toStaplerRequest2(request), StaplerResponse.toStaplerResponse2(response), "sidepanel");
        }

        public ContextMenu from(ModelObjectWithContextMenu self, StaplerRequest2 request, StaplerResponse2 response, String view) throws JellyException, IOException {
            WebApp webApp = WebApp.getCurrent();
            final Script s = webApp.getMetaClass(self).getTearOff(JellyClassTearOff.class).findScript(view);
            if (s != null) {
                JellyFacet facet = webApp.getFacet(JellyFacet.class);
                request.setAttribute("taskTags", this); // <l:task> will look for this variable and populate us
                request.setAttribute("mode", "side-panel");
                // run sidepanel but ignore generated HTML
                facet.scriptInvoker.invokeScript(request, response, new Script() {
                    @Override
                    public Script compile() throws JellyException {
                        return this;
                    }

                    @Override
                    public void run(JellyContext context, XMLOutput output) throws JellyTagException {
                        Functions.initPageVariables(context);
                        s.run(context, output);
                    }
                }, self, new XMLOutput(new DefaultHandler()));
            } else
            if (self instanceof Actionable) {
                // fallback
                this.addAll(((Actionable) self).getAllActions());
            }

            return this;
        }
    }

    /**
     * Menu item in {@link ContextMenu}
     */
    @ExportedBean
    class MenuItem {
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
        @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "read by Stapler")
        public String displayName;

        /**
         * Optional URL to the icon image. Rendered as 24x24.
         */
        @Exported
        @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "read by Stapler")
        public String icon;

        /**
         * Optional icon XML, if set it's used instead of @icon for the menu item
         */
        private String iconXml;

        /**
         * True to make a POST request rather than GET.
         * @since 1.504
         */
        @Exported
        @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "read by Stapler")
        public boolean post;

        /**
         * True to require confirmation after a click.
         * @since 1.512
         */
        @Exported
        @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "read by Stapler")
        public boolean requiresConfirmation;


        private Badge badge;

        private String message;

        /**
         * The type of menu item
         * @since 2.340
         */
        @Exported
        @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "read by Stapler")
        public MenuItemType type = MenuItemType.ITEM;

        /**
         * If this is a submenu, definition of subitems.
         */
        @Exported(inline = true)
        @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "read by Stapler")
        public ContextMenu subMenu;

        @Exported
        public String getIconXml() {
            return iconXml;
        }

        /**
         * The badge to display for the context menu item
         * @since 2.401
         */
        @Exported
        public Badge getBadge() {
            return badge;
        }

        @Exported
        public String getMessage() {
            return message;
        }

        public MenuItem(String url, String icon, String displayName) {
            withUrl(url).withIcon(icon).withDisplayName(displayName);
        }

        public MenuItem() {
        }

        public MenuItem withUrl(String url) {
            try {
                this.url = new URI(Stapler.getCurrentRequest2().getRequestURI()).resolve(new URI(url)).toString();
            } catch (URISyntaxException x) {
                throw new IllegalArgumentException("Bad URI from " + Stapler.getCurrentRequest2().getRequestURI() + " vs. " + url, x);
            }
            return this;
        }

        /**
         * Sets the URL by passing in a URL relative to the context path of Jenkins
         */
        public MenuItem withContextRelativeUrl(String url) {
            if (!url.startsWith("/"))   url = '/' + url;
            this.url = Stapler.getCurrentRequest2().getContextPath() + url;
            return this;
        }

        public MenuItem withIcon(String icon) {
            this.icon = icon;
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
            this.icon = getResourceUrl() + "/images/24x24/" + icon;
            return this;
        }

        public MenuItem withIconClass(String iconClass) {
            if (iconClass != null && iconClass.startsWith("symbol-")) {
                this.icon = iconClass;
                this.iconXml = Symbol.get(new SymbolRequest.Builder()
                        .withName(iconClass.split(" ")[0].substring(7))
                        .withPluginName(Functions.extractPluginNameFromIconSrc(iconClass))
                        .withClasses("icon-md")
                        .build()
                );
            } else {
                Icon iconByClass = IconSet.icons.getIconByClassSpec(iconClass + " icon-md");
                this.icon = iconByClass == null ? null : iconByClass.getQualifiedUrl(getResourceUrl());
            }
            return this;
        }

        public MenuItem withDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public MenuItem withDisplayName(ModelObject o) {
            return withDisplayName(o.getDisplayName());
        }

        private String getResourceUrl() {
            return Stapler.getCurrentRequest2().getContextPath() + Jenkins.RESOURCE_PATH;
        }

    }

    enum MenuItemType {
        ITEM,
        HEADER,
        SEPARATOR
    }

    /**
     * Allows an action to decide whether it will be visible in a context menu.
     * @since 1.538
     */
    interface ContextMenuVisibility extends Action {

        /**
         * Determines whether to show this action right now.
         * Can always return false, for an action which should never be in the context menu;
         * or could examine {@link Stapler#getCurrentRequest2}.
         * @return true to display it, false to hide
         * @see ContextMenu#add(Action)
         */
        boolean isVisible();

    }

}
