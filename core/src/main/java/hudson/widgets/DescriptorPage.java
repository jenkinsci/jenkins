package hudson.widgets;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.DescriptorByNameOwner;
import hudson.model.Item;
import hudson.model.User;
import hudson.model.View;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.jelly.JellyContext;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.bind.JavaScriptMethod;

/**
 * The configuration page of a descriptor for a new instance, as a hetero-list offers it to add or a dropdown list shows
 * it when selected, rendered on demand without keeping anything in the session.
 *
 * <p>
 * {@code <l:renderOnDemand>} otherwise keeps a {@link RenderOnDemandClosure} in the session for each of them, for as
 * long as the session lasts. Here everything needed is in the URL instead: the object configured (the
 * {@link DescriptorByNameOwner} the URL goes through, which Stapler resolves, checking access as for any other request),
 * the descriptor, and the view to render, as {@code descriptorByName/ID/page/VIEW} under the object's URL. Rendering
 * needs the permission to configure the object. Nothing in such a page is secret: it holds no instance, and it renders
 * as the user asking, who could add the same descriptor to an object they configure.
 *
 * <p>
 * Where any of this does not hold (other variables captured, an existing instance, read-only mode, an object of another
 * type, a user who could not configure it), the closure is kept as before.
 */
@Restricted(NoExternalUse.class)
public final class DescriptorPage {

    /** Views of this class, each rendering what the body of {@code <l:renderOnDemand>} renders where it is used. */
    private static final Set<String> VIEWS = Set.of("heteroList", "dropdownListBlock");

    /** The variables those bodies use, which are all that may be captured. */
    private static final Set<String> VARIABLES = Set.of("descriptor", "it", "instance", "readOnlyMode");

    /** Suffix of the view in the URL when {@code it} is the node of the computer configured. */
    private static final String NODE = ".node";

    private final Descriptor<?> descriptor;
    private final Object object;
    private final String view;

    private DescriptorPage(Descriptor<?> descriptor, Object object, String view) {
        this.descriptor = descriptor;
        this.object = object;
        this.view = view;
    }

    public Descriptor<?> getDescriptor() {
        return descriptor;
    }

    /** What the page had as {@code it}: the object configured, or the node of the computer configured. */
    public Object getObject() {
        return object;
    }

    /**
     * The proxy parameters for {@code <l:renderOnDemand>} to render this way, or null to keep a closure.
     *
     * @param view the view of this class that renders as the body would
     */
    public static @CheckForNull StaplerRequest2.RenderOnDemandParameters parameters(JellyContext context, String attributesToCapture, String view) {
        if (!VIEWS.contains(view)) {
            return keepClosure("no view " + view);
        }
        for (String v : attributesToCapture.split(",")) {
            if (v.isEmpty()) {
                continue;
            }
            if (!VARIABLES.contains(v)) {
                return keepClosure(v + " is captured");
            }
            if (v.equals("instance") && context.getVariable(v) != null) {
                return keepClosure("an instance is rendered");
            }
        }
        if (Boolean.TRUE.equals(context.getVariable("readOnlyMode"))) {
            return keepClosure("read-only");
        }
        if (!(context.getVariable("descriptor") instanceof Descriptor<?> d) || Jenkins.get().getDescriptor(d.getId()) != d) {
            return keepClosure("not a registered descriptor: " + context.getVariable("descriptor"));
        }
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req.getAttribute("currentDescriptorByNameUrl") != null) {
            return keepClosure("the page set the base URL of descriptors");
        }
        Ancestor a = req.findAncestor(DescriptorByNameOwner.class);
        Object it = context.getVariable("it");
        String name;
        if (a != null && a.getObject() == it) {
            name = view;
        } else if (a != null && a.getObject() instanceof Computer c && it != null && c.getNode() == it) {
            name = view + NODE;
        } else {
            return keepClosure("it is not " + (a == null ? "an object of the URL" : a.getObject()));
        }
        Permission configure = permissionToConfigure(a.getObject());
        if (configure == null || !((AccessControlled) a.getObject()).hasPermission(configure)) {
            return keepClosure("cannot configure " + a.getObject());
        }
        String url = a.getUrl() + '/' + d.getDescriptorUrl() + "/page/" + name;
        return new StaplerRequest2.RenderOnDemandParameters(
                "makeStaplerProxy", url, req.getWebApp().getCrumbIssuer().issueCrumb(), Set.of("render"));
    }

    private static <T> T keepClosure(String reason) {
        LOGGER.fine(() -> "keeping a closure: " + reason);
        return null;
    }

    /**
     * Resolves {@code descriptorByName/ID/page/NAME} under the object configured.
     */
    public static DescriptorPage of(Descriptor<?> descriptor, String name) {
        boolean node = name.endsWith(NODE);
        String view = node ? name.substring(0, name.length() - NODE.length()) : name;
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        Ancestor a = req.findAncestor(DescriptorByNameOwner.class);
        Permission configure = a == null ? null : permissionToConfigure(a.getObject());
        if (!VIEWS.contains(view) || configure == null) {
            throw HttpResponses.notFound();
        }
        AccessControlled owner = (AccessControlled) a.getObject();
        if (!owner.hasPermission(configure)) {
            throw HttpResponses.forbidden();
        }
        Object it = node ? (owner instanceof Computer c ? c.getNode() : null) : owner;
        if (it == null) {
            throw HttpResponses.notFound();
        }
        return new DescriptorPage(descriptor, it, view);
    }

    /** The permission to configure an object, if it is of a type known here. */
    private static @CheckForNull Permission permissionToConfigure(Object owner) {
        if (owner instanceof Item) {
            return Item.CONFIGURE;
        } else if (owner instanceof View) {
            return View.CONFIGURE;
        } else if (owner instanceof Computer) {
            return Computer.CONFIGURE;
        } else if (owner instanceof User) {
            return Jenkins.ADMINISTER; // a user has every permission on themselves
        } else if (owner instanceof Jenkins) {
            return Jenkins.ADMINISTER; // pages differ (some need only Manage); others keep the closure
        }
        return null;
    }

    /**
     * Renders the configuration page, as the closure would.
     */
    @JavaScriptMethod
    public HttpResponse render() {
        return new HttpResponse() {
            @Override
            public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
                req.getWebApp().getDispatchValidator().allowDispatch(req, rsp);
                req.getView(DescriptorPage.this, view + ".jelly").forward(req, rsp);
            }
        };
    }

    private static final Logger LOGGER = Logger.getLogger(DescriptorPage.class.getName());
}
