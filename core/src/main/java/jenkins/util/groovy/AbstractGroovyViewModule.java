package jenkins.util.groovy;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.GroovyObjectSupport;
import lib.FormTagLib;
import lib.JenkinsTagLib;
import lib.LayoutTagLib;
import org.kohsuke.stapler.jelly.groovy.JellyBuilder;
import org.kohsuke.stapler.jelly.groovy.Namespace;

/**
 * Base class for utility classes for Groovy view scripts
 * <p>
 * Usage from script of a subclass, say ViewHelper:
 * <p>
 * {@code new ViewHelper(delegate).method();}
 * <p>
 * see {@code ModularizeViewScript} in ui-samples for an example how to use
 * this class.
 */
public abstract class AbstractGroovyViewModule extends GroovyObjectSupport {

    public JellyBuilder builder;
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "read by Stapler")
    public FormTagLib f;
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "read by Stapler")
    public LayoutTagLib l;
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "read by Stapler")
    public JenkinsTagLib t;
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "read by Stapler")
    public Namespace st;

    protected AbstractGroovyViewModule(JellyBuilder b) {
        builder = b;
        f = builder.namespace(FormTagLib.class);
        l = builder.namespace(LayoutTagLib.class);
        t = builder.namespace(JenkinsTagLib.class);
        st = builder.namespace("jelly:stapler");
    }

    public Object methodMissing(String name, Object args) {
        return builder.invokeMethod(name, args);
    }

    public Object propertyMissing(String name) {
        return builder.getProperty(name);
    }

    public void propertyMissing(String name, Object value) {
        builder.setProperty(name, value);
    }

}
