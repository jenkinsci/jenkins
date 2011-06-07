package hudson.taglibs;

import groovy.lang.Closure;
import org.kohsuke.stapler.jelly.groovy.TagLibraryUri;
import org.kohsuke.stapler.jelly.groovy.TypedTagLibrary;

import java.util.Map;

/**
 * Experimenting. This is to be auto-generated.
 *
 * @author Kohsuke Kawaguchi
 */
@TagLibraryUri("/lib/layout")
public interface LayoutTagLib extends TypedTagLibrary {
    void layout(Map args, Closure body);
    void layout(Map args);
    void layout(Closure body);
    void layout();

    void main_panel(Closure c);
}
