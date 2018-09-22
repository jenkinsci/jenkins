package jenkins.util.groovy;

import groovy.lang.GroovyObjectSupport;
import lib.FormTagLib;
import lib.LayoutTagLib;
import org.kohsuke.stapler.jelly.groovy.JellyBuilder;
import org.kohsuke.stapler.jelly.groovy.Namespace;
import lib.JenkinsTagLib;

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
    public FormTagLib f;
    public LayoutTagLib l;
    public JenkinsTagLib t;
    public Namespace st;

    public AbstractGroovyViewModule(JellyBuilder b) {
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
