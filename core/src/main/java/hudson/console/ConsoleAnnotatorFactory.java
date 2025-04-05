/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.console;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.jvnet.tiger_types.Types;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebMethod;

/**
 * Entry point to the {@link ConsoleAnnotator} extension point. This class creates a new instance
 * of {@link ConsoleAnnotator} that starts a new console annotation session.
 *
 * <p>
 * {@link ConsoleAnnotatorFactory}s are used whenever a browser requests console output (as opposed to when
 * the console output is being produced &mdash; for that see {@link ConsoleNote}.)
 *
 * <p>
 * {@link ConsoleAnnotator}s returned by {@link ConsoleAnnotatorFactory} are asked to start from
 * an arbitrary line of the output, because typically browsers do not request the entire console output.
 * Because of this, {@link ConsoleAnnotatorFactory} is generally suitable for peep-hole local annotation
 * that only requires a small contextual information, such as keyword coloring, URL hyperlinking, and so on.
 *
 * <p>
 * To register, put @{@link Extension} on your {@link ConsoleAnnotatorFactory} subtype.
 *
 * <h2>Behaviour, JavaScript, and CSS</h2>
 * <p>
 * {@link ConsoleNote} can have associated {@code script.js} and {@code style.css} (put them
 * in the same resource directory that you normally put Jelly scripts), which will be loaded into
 * the HTML page whenever the console notes are used. This allows you to use minimal markup in
 * code generation, and do the styling in CSS and perform the rest of the interesting work as a CSS behaviour/JavaScript.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.349
 */
public abstract class ConsoleAnnotatorFactory<T> implements ExtensionPoint {
    /**
     * Called when a console output page is requested to create a stateful {@link ConsoleAnnotator}.
     *
     * <p>
     * This method can be invoked concurrently by multiple threads.
     *
     * @param context
     *      The model object that owns the console output, such as {@link Run}.
     *      This method is only called when the context object if assignable to
     *      {@linkplain #type() the advertised type}.
     * @return
     *      null if this factory is not going to participate in the annotation of this console.
     */
    public abstract ConsoleAnnotator<T> newInstance(T context);

    /**
     * For which context type does this annotator work?
     */
    public Class<?> type() {
        Type type = Types.getBaseClass(getClass(), ConsoleAnnotatorFactory.class);
        if (type instanceof ParameterizedType)
            return Types.erasure(Types.getTypeArgument(type, 0));
        else
            return Object.class;
    }

    /**
     * Returns true if this descriptor has a JavaScript to be inserted on applicable console page.
     */
    public boolean hasScript() {
        return getResource("/script.js") != null;
    }

    public boolean hasStylesheet() {
        return getResource("/style.css") != null;
    }

    private URL getResource(String fileName) {
        Class<?> c = getClass();
        return c.getClassLoader().getResource(c.getName().replace('.', '/').replace('$', '/') + fileName);
    }

    /**
     * Serves the JavaScript file associated with this console annotator factory.
     */
    @WebMethod(name = "script.js")
    public void doScriptJs(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        rsp.serveFile(req, getResource("/script.js"), TimeUnit.DAYS.toMillis(1));
    }

    @WebMethod(name = "style.css")
    public void doStyleCss(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        rsp.serveFile(req, getResource("/style.css"), TimeUnit.DAYS.toMillis(1));
    }

    /**
     * All the registered instances.
     */
    @SuppressWarnings("rawtypes")
    public static ExtensionList<ConsoleAnnotatorFactory> all() {
        return ExtensionList.lookup(ConsoleAnnotatorFactory.class);
    }

    /**
     * This action makes {@link hudson.console.ConsoleAnnotatorFactory} instances accessible via HTTP.
     *
     * @see hudson.Functions#generateConsoleAnnotationScriptAndStylesheet
     * @see ConsoleAnnotatorFactory#hasStylesheet()
     * @see ConsoleAnnotatorFactory#hasScript()
     */
    @Restricted(NoExternalUse.class)
    @Extension
    public static class RootAction extends InvisibleAction implements hudson.model.RootAction {
        @Override
        public String getUrlName() {
            return ConsoleAnnotatorFactory.class.getName();
        }

        public ConsoleAnnotatorFactory<?> getDynamic(String className) {
            return all().getDynamic(className);
        }
    }
}
