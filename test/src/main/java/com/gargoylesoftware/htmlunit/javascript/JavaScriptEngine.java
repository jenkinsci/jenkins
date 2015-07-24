/*
 * Copyright (c) 2002-2015 Gargoyle Software Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gargoylesoftware.htmlunit.javascript;

import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_ALLOW_CONST_ASSIGNMENT;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_CONSTRUCTOR;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_DATE_USE_UTC;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_DEFINE_GETTER;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_DONT_ENUM_FUNCTIONS;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_ECMA5_FUNCTIONS;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_FUNCTION_BIND;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_FUNCTION_TOSOURCE;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_IMAGE_PROTOTYPE_SAME_AS_HTML_IMAGE;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_INTL;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_Iterator;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_OBJECT_WITH_PROTOTYPE_PROPERTY_IN_WINDOW_SCOPE;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_OPTION_PROTOTYPE_SAME_AS_HTML_OPTION;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_WINDOW_ACTIVEXOBJECT_HIDDEN;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_XML;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.STRING_CONTAINS;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.STRING_TRIM;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.STRING_TRIM_LEFT_RIGHT;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.ContextAction;
import net.sourceforge.htmlunit.corejs.javascript.Function;
import net.sourceforge.htmlunit.corejs.javascript.FunctionObject;
import net.sourceforge.htmlunit.corejs.javascript.Script;
import net.sourceforge.htmlunit.corejs.javascript.ScriptRuntime;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import net.sourceforge.htmlunit.corejs.javascript.UniqueTag;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.ScriptException;
import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebWindow;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.javascript.background.BackgroundJavaScriptFactory;
import com.gargoylesoftware.htmlunit.javascript.background.JavaScriptExecutor;
import com.gargoylesoftware.htmlunit.javascript.configuration.ClassConfiguration;
import com.gargoylesoftware.htmlunit.javascript.configuration.JavaScriptConfiguration;
import com.gargoylesoftware.htmlunit.javascript.host.ActiveXObject;
import com.gargoylesoftware.htmlunit.javascript.host.DateCustom;
import com.gargoylesoftware.htmlunit.javascript.host.Element;
import com.gargoylesoftware.htmlunit.javascript.host.StringCustom;
import com.gargoylesoftware.htmlunit.javascript.host.Window;
import com.gargoylesoftware.htmlunit.javascript.host.html.HTMLDocument;
import com.gargoylesoftware.htmlunit.javascript.host.intl.Intl;

/**
 * A wrapper for the <a href="http://www.mozilla.org/rhino">Rhino JavaScript engine</a>
 * that provides browser specific features.<br/>
 * Like all classes in this package, this class is not intended for direct use
 * and may change without notice.
 *
 * @version $Revision: 10607 $
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author <a href="mailto:chen_jun@users.sourceforge.net">Chen Jun</a>
 * @author David K. Taylor
 * @author Chris Erskine
 * @author <a href="mailto:bcurren@esomnie.com">Ben Curren</a>
 * @author David D. Kilzer
 * @author Marc Guillemot
 * @author Daniel Gredler
 * @author Ahmed Ashour
 * @author Amit Manjhi
 * @author Ronald Brill
 * @author Frank Danek
 * @see <a href="http://groups-beta.google.com/group/netscape.public.mozilla.jseng/browse_thread/thread/b4edac57329cf49f/069e9307ec89111f">
 * Rhino and Java Browser</a>
 */
public class JavaScriptEngine {

    private static final Log LOG = LogFactory.getLog(JavaScriptEngine.class);

    private final WebClient webClient_;
    private final HtmlUnitContextFactory contextFactory_;
    private final JavaScriptConfiguration jsConfig_;

    private transient ThreadLocal<Boolean> javaScriptRunning_;
    private transient ThreadLocal<List<PostponedAction>> postponedActions_;
    private transient boolean holdPostponedActions_;

    /** The JavaScriptExecutor corresponding to all windows of this Web client */
    private transient JavaScriptExecutor javaScriptExecutor_;

    /**
     * Key used to place the scope in which the execution of some JavaScript code
     * started as thread local attribute in current context.<br/>
     * This is needed to resolve some relative locations relatively to the page
     * in which the script is executed and not to the page which location is changed.
     */
    public static final String KEY_STARTING_SCOPE = "startingScope";

    /**
     * Key used to place the {@link HtmlPage} for which the JavaScript code is executed
     * as thread local attribute in current context.
     */
    public static final String KEY_STARTING_PAGE = "startingPage";

    /**
     * Creates an instance for the specified {@link WebClient}.
     *
     * @param webClient the client that will own this engine
     */
    public JavaScriptEngine(final WebClient webClient) {
        webClient_ = webClient;
        contextFactory_ = new HtmlUnitContextFactory(webClient);
        initTransientFields();
        jsConfig_ = JavaScriptConfiguration.getInstance(webClient.getBrowserVersion());
    }

    /**
     * Returns the web client that this engine is associated with.
     * @return the web client
     */
    public final WebClient getWebClient() {
        return webClient_;
    }

    /**
     * Returns this JavaScript engine's Rhino {@link net.sourceforge.htmlunit.corejs.javascript.ContextFactory}.
     * @return this JavaScript engine's Rhino {@link net.sourceforge.htmlunit.corejs.javascript.ContextFactory}
     */
    public HtmlUnitContextFactory getContextFactory() {
        return contextFactory_;
    }

    /**
     * Performs initialization for the given webWindow.
     * @param webWindow the web window to initialize for
     */
    public void initialize(final WebWindow webWindow) {
        WebAssert.notNull("webWindow", webWindow);

        final ContextAction action = new ContextAction() {
            public Object run(final Context cx) {
                try {
                    init(webWindow, cx);
                }
                catch (final Exception e) {
                    LOG.error("Exception while initializing JavaScript for the page", e);
                    throw new ScriptException(null, e); // BUG: null is not useful.
                }

                return null;
            }
        };

        getContextFactory().call(action);
    }

    /**
     * Returns the JavaScriptExecutor.
     * @return the JavaScriptExecutor.
     */
    public JavaScriptExecutor getJavaScriptExecutor() {
        return javaScriptExecutor_;
    }

    /**
     * Initializes all the JS stuff for the window.
     * @param webWindow the web window
     * @param context the current context
     * @throws Exception if something goes wrong
     */
    private void init(final WebWindow webWindow, final Context context) throws Exception {
        final WebClient webClient = webWindow.getWebClient();
        final BrowserVersion browserVersion = webClient.getBrowserVersion();
        final Map<Class<? extends SimpleScriptable>, Scriptable> prototypes = new HashMap<>();
        final Map<String, Scriptable> prototypesPerJSName = new HashMap<>();

        final Window window = new Window();
        ((SimpleScriptable) window).setClassName("Window");
        context.initStandardObjects(window);

        if (browserVersion.hasFeature(JS_CONSTRUCTOR)) {
            final ClassConfiguration windowConfig = jsConfig_.getClassConfiguration("Window");
            if (windowConfig.getJsConstructor() != null) {
                final FunctionObject functionObject = new RecursiveFunctionObject("Window",
                        windowConfig.getJsConstructor(), window);
                ScriptableObject.defineProperty(window, "constructor", functionObject,
                        ScriptableObject.DONTENUM  | ScriptableObject.PERMANENT | ScriptableObject.READONLY);
            }
            else {
                defineConstructor(browserVersion, window, window, new Window());
            }
        }
        else {
            deleteProperties(window, "constructor");
        }

        // remove some objects, that Rhino defines in top scope but that we don't want
        deleteProperties(window, "java", "javax", "org", "com", "edu", "net",
                "JavaAdapter", "JavaImporter", "Continuation", "Packages", "getClass");
        if (!browserVersion.hasFeature(JS_XML)) {
            deleteProperties(window, "XML", "XMLList", "Namespace", "QName");
        }

        if (!browserVersion.hasFeature(JS_Iterator)) {
            deleteProperties(window, "Iterator", "StopIteration");
        }

        if (browserVersion.hasFeature(JS_INTL)) {
            final Intl intl = new Intl();
            intl.setParentScope(window);
            window.defineProperty(intl.getClassName(), intl, ScriptableObject.DONTENUM);
            intl.defineProperties(browserVersion);
        }

        // put custom object to be called as very last prototype to call the fallback getter (if any)
        final Scriptable fallbackCaller = new FallbackCaller();
        ScriptableObject.getObjectPrototype(window).setPrototype(fallbackCaller);

        final boolean putPrototypeInWindowScope =
                browserVersion.hasFeature(JS_OBJECT_WITH_PROTOTYPE_PROPERTY_IN_WINDOW_SCOPE);
        for (final ClassConfiguration config : jsConfig_.getAll()) {
            final boolean isWindow = Window.class.getName().equals(config.getHostClass().getName());
            if (isWindow) {
                configureConstantsPropertiesAndFunctions(config, window, browserVersion);

                final ScriptableObject prototype = configureClass(config, window, browserVersion);
                prototypesPerJSName.put(config.getClassName(), prototype);
            }
            else {
                final ScriptableObject prototype = configureClass(config, window, browserVersion);
                if (config.isJsObject()) {
                    // Place object with prototype property in Window scope
                    if (putPrototypeInWindowScope) {
                        final SimpleScriptable obj = config.getHostClass().newInstance();
                        prototype.defineProperty("__proto__", prototype, ScriptableObject.DONTENUM);
                        obj.defineProperty("prototype", prototype, ScriptableObject.DONTENUM); // but not setPrototype!
                        obj.setParentScope(window);
                        obj.setClassName(config.getClassName());
                        ScriptableObject.defineProperty(window, obj.getClassName(), obj, ScriptableObject.DONTENUM);
                        // this obj won't have prototype, constants need to be configured on it again
                        configureConstants(config, obj);

                        if (obj.getClass() == Element.class) {
                            final Page page = webWindow.getEnclosedPage();
                            if (page != null && page.isHtmlPage()) {
                                final DomNode domNode = new HtmlDivision("", (HtmlPage) page, null);
                                obj.setDomNode(domNode);
                            }
                        }
                    }
                }
                prototypes.put(config.getHostClass(), prototype);
                prototypesPerJSName.put(config.getClassName(), prototype);
            }
        }

        for (final ClassConfiguration config : jsConfig_.getAll()) {
            final Member jsConstructor = config.getJsConstructor();
            final String jsClassName = config.getClassName();
            Scriptable prototype = prototypesPerJSName.get(jsClassName);
            final String hostClassSimpleName = config.getHostClass().getSimpleName();
            if ("Image".equals(hostClassSimpleName)
                    && browserVersion.hasFeature(JS_IMAGE_PROTOTYPE_SAME_AS_HTML_IMAGE)) {
                prototype = prototypesPerJSName.get("HTMLImageElement");
            }
            if ("Option".equals(hostClassSimpleName)
                    && browserVersion.hasFeature(JS_OPTION_PROTOTYPE_SAME_AS_HTML_OPTION)) {
                prototype = prototypesPerJSName.get("HTMLOptionElement");
            }
            if (prototype != null && config.isJsObject()) {
                if (jsConstructor != null) {
                    final FunctionObject functionObject;
                    if ("Window".equals(jsClassName)) {
                        functionObject = (FunctionObject) ScriptableObject.getProperty(window, "constructor");
                    }
                    else {
                        functionObject = new RecursiveFunctionObject(jsClassName, jsConstructor, window);
                    }

                    if ("Image".equals(hostClassSimpleName) || "Option".equals(hostClassSimpleName)) {
                        final Object prototypeProperty = ScriptableObject.getProperty(window, prototype.getClassName());

                        functionObject.addAsConstructor(window, prototype);

                        ScriptableObject.defineProperty(window, hostClassSimpleName, functionObject,
                                ScriptableObject.DONTENUM);

                        // the prototype class name is set as a side effect of functionObject.addAsConstructor
                        // so we restore its value
                        if (!hostClassSimpleName.equals(prototype.getClassName())) {
                            if (prototypeProperty == UniqueTag.NOT_FOUND) {
                                ScriptableObject.deleteProperty(window, prototype.getClassName());
                            }
                            else {
                                ScriptableObject.defineProperty(window, prototype.getClassName(),
                                        prototypeProperty, ScriptableObject.DONTENUM);
                            }
                        }
                    }
                    else {
                        functionObject.addAsConstructor(window, prototype);
                    }

                    configureConstants(config, functionObject);

                    for (final Entry<String, Method> staticfunctionInfo : config.getStaticFunctionEntries()) {
                        final String functionName = staticfunctionInfo.getKey();
                        final Method method = staticfunctionInfo.getValue();
                        final FunctionObject staticFunctionObject = new FunctionObject(functionName, method,
                                functionObject);
                        functionObject.defineProperty(functionName, staticFunctionObject, ScriptableObject.EMPTY);
                    }
                }
                else {
                    if (browserVersion.hasFeature(JS_CONSTRUCTOR)) {
                        final ScriptableObject constructor;
                        if ("Window".equals(jsClassName)) {
                            constructor = (ScriptableObject) ScriptableObject.getProperty(window, "constructor");
                        }
                        else {
                            constructor = config.getHostClass().newInstance();
                            ((SimpleScriptable) constructor).setClassName(config.getClassName());
                        }
                        defineConstructor(browserVersion, window, prototype, constructor);
                        configureConstants(config, constructor);
                    }
                    else {
                        if (!"Window".equals(jsClassName)) {
                            final ScriptableObject constructor = config.getHostClass().newInstance();
                            constructor.setParentScope(window);
                            window.defineProperty(constructor.getClassName(), constructor, ScriptableObject.DONTENUM);
                        }

                        deleteProperties(prototype, "constructor");
                    }
                }
            }
        }
        window.setPrototype(prototypesPerJSName.get(Window.class.getSimpleName()));

        // once all prototypes have been build, it's possible to configure the chains
        final Scriptable objectPrototype = ScriptableObject.getObjectPrototype(window);
        for (final Map.Entry<String, Scriptable> entry : prototypesPerJSName.entrySet()) {
            final String name = entry.getKey();
            final ClassConfiguration config = jsConfig_.getClassConfiguration(name);
            Scriptable prototype = entry.getValue();
            if (prototype.getPrototype() != null) {
                prototype = prototype.getPrototype(); // "double prototype" hack for FF
            }
            if (!StringUtils.isEmpty(config.getExtendedClassName())) {
                final Scriptable parentPrototype = prototypesPerJSName.get(config.getExtendedClassName());
                prototype.setPrototype(parentPrototype);
            }
            else {
                prototype.setPrototype(objectPrototype);
            }
        }

        // IE11 ActiveXObject simulation
        // see http://msdn.microsoft.com/en-us/library/ie/dn423948%28v=vs.85%29.aspx
        // DEV Note: this is at the moment the only usage of HiddenFunctionObject
        //           if we need more in the future, we have to enhance our JSX annotations
        if (browserVersion.hasFeature(JS_WINDOW_ACTIVEXOBJECT_HIDDEN)) {
            final Scriptable prototype = prototypesPerJSName.get("ActiveXObject");
            if (null != prototype) {
                final Method jsConstructor = ActiveXObject.class.getDeclaredMethod("jsConstructor",
                        Context.class, Object[].class, Function.class, boolean.class);
                final FunctionObject functionObject = new HiddenFunctionObject("ActiveXObject", jsConstructor, window);
                functionObject.addAsConstructor(window, prototype);
            }
        }

        // Rhino defines too much methods for us, particularly since implementation of ECMAScript5
        removePrototypeProperties(window, "String", "equals", "equalsIgnoreCase");
        if (!browserVersion.hasFeature(STRING_TRIM)) {
            removePrototypeProperties(window, "String", "trim");
        }
        if (!browserVersion.hasFeature(STRING_TRIM_LEFT_RIGHT)) {
            removePrototypeProperties(window, "String", "trimLeft");
            removePrototypeProperties(window, "String", "trimRight");
        }
        if (browserVersion.hasFeature(STRING_CONTAINS)) {
            final ScriptableObject stringPrototype =
                (ScriptableObject) ScriptableObject.getClassPrototype(window, "String");
            stringPrototype.defineFunctionProperties(new String[] {"contains"},
                StringCustom.class, ScriptableObject.EMPTY);
        }

        if (!browserVersion.hasFeature(JS_FUNCTION_BIND)) {
            removePrototypeProperties(window, "Function", "bind");
        }
        if (!browserVersion.hasFeature(JS_ECMA5_FUNCTIONS)) {
            removePrototypeProperties(window, "Date", "toISOString", "toJSON");
        }

        if (!browserVersion.hasFeature(JS_DEFINE_GETTER)) {
            removePrototypeProperties(window, "Object", "__defineGetter__", "__defineSetter__", "__lookupGetter__",
                    "__lookupSetter__");
        }

        // only FF has toSource
        if (!browserVersion.hasFeature(JS_FUNCTION_TOSOURCE)) {
            deleteProperties(window, "uneval");
            removePrototypeProperties(window, "Object", "toSource");
            removePrototypeProperties(window, "Array", "toSource");
            removePrototypeProperties(window, "Date", "toSource");
            removePrototypeProperties(window, "Function", "toSource");
            removePrototypeProperties(window, "Number", "toSource");
            removePrototypeProperties(window, "String", "toSource");
        }
        deleteProperties(window, "isXMLName");

        NativeFunctionToStringFunction.installFix(window, webClient.getBrowserVersion());

        if (browserVersion.hasFeature(JS_ALLOW_CONST_ASSIGNMENT)) {
            makeConstWritable(window, "undefined", "NaN", "Infinity");
        }

        final ScriptableObject datePrototype = (ScriptableObject) ScriptableObject.getClassPrototype(window, "Date");
        datePrototype.defineFunctionProperties(new String[] {"toLocaleDateString", "toLocaleTimeString"},
                DateCustom.class, ScriptableObject.DONTENUM);

        if (browserVersion.hasFeature(JS_DATE_USE_UTC)) {
            datePrototype.defineFunctionProperties(new String[] {"toUTCString"},
                    DateCustom.class, ScriptableObject.DONTENUM);
        }

        window.setPrototypes(prototypes, prototypesPerJSName);
        window.initialize(webWindow);
    }

    private void defineConstructor(final BrowserVersion browserVersion, final Window window,
            final Scriptable prototype, final ScriptableObject constructor) {
        constructor.setParentScope(window);
        final Object constructorValue = browserVersion.hasFeature(JS_CONSTRUCTOR) ? constructor : null;
        ScriptableObject.defineProperty(prototype, "constructor", constructorValue,
                ScriptableObject.DONTENUM  | ScriptableObject.PERMANENT | ScriptableObject.READONLY);
        ScriptableObject.defineProperty(constructor, "prototype", prototype,
                ScriptableObject.DONTENUM  | ScriptableObject.PERMANENT | ScriptableObject.READONLY);
        window.defineProperty(constructor.getClassName(), constructor, ScriptableObject.DONTENUM);
    }

    private void makeConstWritable(final ScriptableObject scope, final String... constNames) {
        for (final String name : constNames) {
            final Object value = ScriptableObject.getProperty(scope, name);
            ScriptableObject.defineProperty(scope, name, value,
                    ScriptableObject.DONTENUM | ScriptableObject.PERMANENT);
        }
    }

    /**
     * Deletes the properties with the provided names.
     * @param scope the scope from which properties have to be removed
     * @param propertiesToDelete the list of property names
     */
    private void deleteProperties(final Scriptable scope, final String... propertiesToDelete) {
        for (final String property : propertiesToDelete) {
            scope.delete(property);
        }
    }

    /**
     * Define properties in Standards Mode.
     *
     * @param page the page
     */
    public void definePropertiesInStandardsMode(final HtmlPage page) {
        final Window window = ((HTMLDocument) page.getScriptObject()).getWindow();
        final BrowserVersion browserVersion = window.getBrowserVersion();
        for (final ClassConfiguration config : jsConfig_.getAll()) {
            final String jsClassName = config.getClassName();
            if (config.isDefinedInStandardsMode()) {
                final Scriptable prototype = window.getPrototype(jsClassName);
                if ("Window".equals(jsClassName)) {
                    defineConstructor(browserVersion, window, window, new Window());
                }
                else if (!config.isJsObject()) {
                    try {
                        final ScriptableObject constructor = config.getHostClass().newInstance();
                        defineConstructor(browserVersion, window, prototype, constructor);
                    }
                    catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    /**
     * Removes prototype properties.
     * @param scope the scope
     * @param className the class for which properties should be removed
     * @param properties the properties to remove
     */
    private void removePrototypeProperties(final Scriptable scope, final String className,
            final String... properties) {
        final ScriptableObject prototype = (ScriptableObject) ScriptableObject.getClassPrototype(scope, className);
        for (final String property : properties) {
            prototype.delete(property);
        }
    }

    /**
     * Configures the specified class for access via JavaScript.
     * @param config the configuration settings for the class to be configured
     * @param window the scope within which to configure the class
     * @param browserVersion the browser version
     * @throws InstantiationException if the new class cannot be instantiated
     * @throws IllegalAccessException if we don't have access to create the new instance
     * @return the created prototype
     */
    public static SimpleScriptable configureClass(final ClassConfiguration config, final Scriptable window,
            final BrowserVersion browserVersion)
        throws InstantiationException, IllegalAccessException {

        final SimpleScriptable prototype = config.getHostClass().newInstance();
        prototype.setParentScope(window);
        prototype.setClassName(config.getClassName());

        configureConstantsPropertiesAndFunctions(config, prototype, browserVersion);

        return prototype;
    }

    /**
     * Configures constants, properties and functions on the object.
     * @param config the configuration for the object
     * @param scriptable the object to configure
     */
    private static void configureConstantsPropertiesAndFunctions(final ClassConfiguration config,
            final ScriptableObject scriptable, final BrowserVersion browserVersion) {

        // the constants
        configureConstants(config, scriptable);

        // the properties
        for (final Entry<String, ClassConfiguration.PropertyInfo> propertyEntry : config.getPropertyEntries()) {
            final String propertyName = propertyEntry.getKey();
            final Method readMethod = propertyEntry.getValue().getReadMethod();
            final Method writeMethod = propertyEntry.getValue().getWriteMethod();
            int flag = ScriptableObject.EMPTY;

            // https://code.google.com/p/chromium/issues/detail?id=492999
            if (browserVersion.isChrome() && "cssFloat".equals(propertyName)) {
                flag = ScriptableObject.DONTENUM;
            }
            scriptable.defineProperty(propertyName, null, readMethod, writeMethod, flag);
        }

        int attributes;
        if (browserVersion.hasFeature(JS_DONT_ENUM_FUNCTIONS)) {
            attributes = ScriptableObject.DONTENUM;
        }
        else {
            attributes = ScriptableObject.EMPTY;
        }
        // the functions
        for (final Entry<String, Method> functionInfo : config.getFunctionEntries()) {
            final String functionName = functionInfo.getKey();
            final Method method = functionInfo.getValue();
            final FunctionObject functionObject = new FunctionObject(functionName, method, scriptable);
            scriptable.defineProperty(functionName, functionObject, attributes);
        }
    }

    private static void configureConstants(final ClassConfiguration config,
            final ScriptableObject scriptable) {
        final Class<?> linkedClass = config.getHostClass();
        for (final String constant : config.getConstants()) {
            try {
                final Object value = linkedClass.getField(constant).get(null);
                scriptable.defineProperty(constant, value, ScriptableObject.READONLY | ScriptableObject.PERMANENT);
            }
            catch (final Exception e) {
                throw Context.reportRuntimeError("Cannot get field '" + constant + "' for type: "
                    + config.getHostClass().getName());
            }
        }
    }

    /**
     * Register WebWindow with the JavaScriptExecutor.
     * @param webWindow the WebWindow to be registered.
     */
    public synchronized void registerWindowAndMaybeStartEventLoop(final WebWindow webWindow) {
        if (javaScriptExecutor_ == null) {
            javaScriptExecutor_ = BackgroundJavaScriptFactory.theFactory().createJavaScriptExecutor(webClient_);
        }
        javaScriptExecutor_.addWindow(webWindow);
    }

    /**
     * Executes the jobs in the eventLoop till timeoutMillis expires or the eventLoop becomes empty.
     * No use in non-GAE mode (see {@link com.gargoylesoftware.htmlunit.gae.GAEUtils#isGaeMode}.
     * @param timeoutMillis the timeout in milliseconds
     * @return the number of jobs executed
     */
    public int pumpEventLoop(final long timeoutMillis) {
        if (javaScriptExecutor_ == null) {
            return 0;
        }
        return javaScriptExecutor_.pumpEventLoop(timeoutMillis);
    }

    /**
     * Shutdown the JavaScriptEngine.
     */
    public void shutdown() {
        if (javaScriptExecutor_ != null) {
            javaScriptExecutor_.shutdown();
            javaScriptExecutor_ = null;
        }
        if (postponedActions_ != null) {
            postponedActions_.remove();
        }
        if (javaScriptRunning_ != null) {
            javaScriptRunning_.remove();
        }
        holdPostponedActions_ = false;
    }

    /**
     * Compiles the specified JavaScript code in the context of a given HTML page.
     *
     * @param htmlPage the page that the code will execute within
     * @param sourceCode the JavaScript code to execute
     * @param sourceName the name that will be displayed on error conditions
     * @param startLine the line at which the script source starts
     * @return the result of executing the specified code
     */
    public Script compile(final HtmlPage htmlPage, final String sourceCode,
                           final String sourceName, final int startLine) {

        WebAssert.notNull("sourceCode", sourceCode);

        if (LOG.isTraceEnabled()) {
            final String newline = System.getProperty("line.separator");
            LOG.trace("Javascript compile " + sourceName + newline + sourceCode + newline);
        }

        final Scriptable scope = getScope(htmlPage, null);
        final String source = sourceCode;
        final ContextAction action = new HtmlUnitContextAction(scope, htmlPage) {
            @Override
            public Object doRun(final Context cx) {
                return cx.compileString(source, sourceName, startLine, null);
            }

            @Override
            protected String getSourceCode(final Context cx) {
                return source;
            }
        };

        return (Script) getContextFactory().call(action);
    }

    /**
     * Executes the specified JavaScript code in the context of a given HTML page.
     *
     * @param htmlPage the page that the code will execute within
     * @param sourceCode the JavaScript code to execute
     * @param sourceName the name that will be displayed on error conditions
     * @param startLine the line at which the script source starts
     * @return the result of executing the specified code
     */
    public Object execute(final HtmlPage htmlPage,
                           final String sourceCode,
                           final String sourceName,
                           final int startLine) {

        final Script script = compile(htmlPage, sourceCode, sourceName, startLine);
        if (script == null) { // happens with syntax error + throwExceptionOnScriptError = false
            return null;
        }
        return execute(htmlPage, script);
    }

    /**
     * Executes the specified JavaScript code in the context of a given HTML page.
     *
     * @param htmlPage the page that the code will execute within
     * @param script the script to execute
     * @return the result of executing the specified code
     */
    public Object execute(final HtmlPage htmlPage, final Script script) {
        final Scriptable scope = getScope(htmlPage, null);

        final ContextAction action = new HtmlUnitContextAction(scope, htmlPage) {
            @Override
            public Object doRun(final Context cx) {
                return script.exec(cx, scope);
            }

            @Override
            protected String getSourceCode(final Context cx) {
                return null;
            }
        };

        return getContextFactory().call(action);
    }

    /**
     * Calls a JavaScript function and return the result.
     * @param htmlPage the page
     * @param javaScriptFunction the function to call
     * @param thisObject the this object for class method calls
     * @param args the list of arguments to pass to the function
     * @param htmlElement the HTML element that will act as the context
     * @return the result of the function call
     */
    public Object callFunction(
            final HtmlPage htmlPage,
            final Function javaScriptFunction,
            final Scriptable thisObject,
            final Object[] args,
            final DomNode htmlElement) {

        final Scriptable scope = getScope(htmlPage, htmlElement);

        return callFunction(htmlPage, javaScriptFunction, scope, thisObject, args);
    }

    /**
     * Calls the given function taking care of synchronization issues.
     * @param htmlPage the HTML page that caused this script to executed
     * @param function the JavaScript function to execute
     * @param scope the execution scope
     * @param thisObject the 'this' object
     * @param args the function's arguments
     * @return the function result
     */
    public Object callFunction(final HtmlPage htmlPage, final Function function,
            final Scriptable scope, final Scriptable thisObject, final Object[] args) {

        final ContextAction action = new HtmlUnitContextAction(scope, htmlPage) {
            @Override
            public Object doRun(final Context cx) {
                if (ScriptRuntime.hasTopCall(cx)) {
                    return function.call(cx, scope, thisObject, args);
                }
                return ScriptRuntime.doTopCall(function, cx, scope, thisObject, args);
            }
            @Override
            protected String getSourceCode(final Context cx) {
                return cx.decompileFunction(function, 2);
            }
        };
        return getContextFactory().call(action);
    }

    private Scriptable getScope(final HtmlPage htmlPage, final DomNode htmlElement) {
        if (htmlElement != null) {
            return htmlElement.getScriptObject();
        }
        return (Window) htmlPage.getEnclosingWindow().getScriptObject();
    }

    /**
     * Indicates if JavaScript is running in current thread.<br/>
     * This allows code to know if there own evaluation is has been triggered by some JS code.
     * @return <code>true</code> if JavaScript is running
     */
    public boolean isScriptRunning() {
        return Boolean.TRUE.equals(javaScriptRunning_.get());
    }

    /**
     * Facility for ContextAction usage.
     * ContextAction should be preferred because according to Rhino doc it
     * "guarantees proper association of Context instances with the current thread and is faster".
     */
    private abstract class HtmlUnitContextAction implements ContextAction {
        private final Scriptable scope_;
        private final HtmlPage htmlPage_;
        public HtmlUnitContextAction(final Scriptable scope, final HtmlPage htmlPage) {
            scope_ = scope;
            htmlPage_ = htmlPage;
        }

        public final Object run(final Context cx) {
            final Boolean javaScriptAlreadyRunning = javaScriptRunning_.get();
            javaScriptRunning_.set(Boolean.TRUE);

            try {
                // KEY_STARTING_SCOPE maintains a stack of scopes
                @SuppressWarnings("unchecked")
                Stack<Scriptable> stack = (Stack<Scriptable>) cx.getThreadLocal(JavaScriptEngine.KEY_STARTING_SCOPE);
                if (null == stack) {
                    stack = new Stack<Scriptable>();
                    cx.putThreadLocal(KEY_STARTING_SCOPE, stack);
                }

                final Object response;
                stack.push(scope_);
                try {
                    cx.putThreadLocal(KEY_STARTING_PAGE, htmlPage_);
                    synchronized (htmlPage_) { // 2 scripts can't be executed in parallel for one page
                        if (htmlPage_ != htmlPage_.getEnclosingWindow().getEnclosedPage()) {
                            return null; // page has been unloaded
                        }
                        response = doRun(cx);
                    }
                }
                finally {
                    stack.pop();
                }

                // doProcessPostponedActions is synchronized
                // moved out of the sync block to avoid deadlocks
                if (!holdPostponedActions_) {
                    doProcessPostponedActions();
                }
                return response;
            }
            catch (final Exception e) {
                handleJavaScriptException(new ScriptException(htmlPage_, e, getSourceCode(cx)), true);
                return null;
            }
            catch (final TimeoutError e) {
                final JavaScriptErrorListener javaScriptErrorListener = getWebClient().getJavaScriptErrorListener();
                if (javaScriptErrorListener != null) {
                    javaScriptErrorListener.timeoutError(htmlPage_, e.getAllowedTime(), e.getExecutionTime());
                }
                if (getWebClient().getOptions().isThrowExceptionOnScriptError()) {
                    throw new RuntimeException(e);
                }
                LOG.info("Caught script timeout error", e);
                return null;
            }
            finally {
                javaScriptRunning_.set(javaScriptAlreadyRunning);
            }
        }

        protected abstract Object doRun(final Context cx);

        protected abstract String getSourceCode(final Context cx);
    }

    private void doProcessPostponedActions() {
        holdPostponedActions_ = false;

        while (true) { // postponed action can result in more postponed actions
            try {
                getWebClient().loadDownloadedResponses();
            }
            catch (final RuntimeException e) {
                throw e;
            }
            catch (final Exception e) {
                throw new RuntimeException(e);
            }

            final List<PostponedAction> actions = postponedActions_.get();
            if (actions == null) {
                break;
            }

            if (actions != null) {
                postponedActions_.set(null);
                try {
                    for (final PostponedAction action : actions) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Processing PostponedAction " + action);
                        }

                        // verify that the page that registered this PostponedAction is still alive
                        if (action.isStillAlive()) {
                            action.execute();
                        }
                    }
                }
                catch (final Exception e) {
                    Context.throwAsScriptRuntimeEx(e);
                }
            }
        }
    }

    /**
     * Adds an action that should be executed first when the script currently being executed has finished.
     * @param action the action
     */
    public void addPostponedAction(final PostponedAction action) {
        List<PostponedAction> actions = postponedActions_.get();
        if (actions == null) {
            actions = new ArrayList<>();
            postponedActions_.set(actions);
        }
        actions.add(action);
    }

    /**
     * Handles an exception that occurred during execution of JavaScript code.
     * @param scriptException the exception
     * @param triggerOnError if true, this triggers the onerror handler
     */
    protected void handleJavaScriptException(final ScriptException scriptException, final boolean triggerOnError) {
        // Trigger window.onerror, if it has been set.
        final HtmlPage page = scriptException.getPage();
        if (triggerOnError && page != null) {
            final WebWindow window = page.getEnclosingWindow();
            if (window != null) {
                final Window w = (Window) window.getScriptObject();
                if (w != null) {
                    try {
                        w.triggerOnError(scriptException);
                    }
                    catch (final Exception e) {
                        handleJavaScriptException(new ScriptException(page, e, null), false);
                    }
                }
            }
        }
        final JavaScriptErrorListener javaScriptErrorListener = getWebClient().getJavaScriptErrorListener();
        if (javaScriptErrorListener != null) {
            javaScriptErrorListener.scriptException(page, scriptException);
        }
        // Throw a Java exception if the user wants us to.
        if (getWebClient().getOptions().isThrowExceptionOnScriptError()) {
            throw scriptException;
        }
        // Log the error; ScriptException instances provide good debug info.
        LOG.info("Caught script exception", scriptException);
    }

    /**
     * <span style="color:red">INTERNAL API - SUBJECT TO CHANGE AT ANY TIME - USE AT YOUR OWN RISK.</span><br/>
     * Indicates that no postponed action should be executed.
     */
    public void holdPosponedActions() {
        holdPostponedActions_ = true;
    }

    /**
     * <span style="color:red">INTERNAL API - SUBJECT TO CHANGE AT ANY TIME - USE AT YOUR OWN RISK.</span><br/>
     * Process postponed actions, if any.
     */
    public void processPostponedActions() {
        doProcessPostponedActions();
    }

    /**
     * Re-initializes transient fields when an object of this type is deserialized.
     */
    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initTransientFields();
    }

    private void initTransientFields() {
        javaScriptRunning_ = new ThreadLocal<Boolean>();
        postponedActions_ = new ThreadLocal<List<PostponedAction>>();
        holdPostponedActions_ = false;
    }

    private static class FallbackCaller extends ScriptableObject {
        private static final long serialVersionUID = 5142592186670858001L;

        @Override
        public Object get(final String name, final Scriptable start) {
            if (start instanceof ScriptableWithFallbackGetter) {
                return ((ScriptableWithFallbackGetter) start).getWithFallback(name);
            }
            return NOT_FOUND;
        }

        @Override
        public String getClassName() {
            return "htmlUnitHelper-fallbackCaller";
        }
    }

    /**
     * Gets the class of the JavaScript object for the node class.
     * @param c the node class {@link DomNode} or some subclass.
     * @return <code>null</code> if none found
     */
    public Class<? extends SimpleScriptable> getJavaScriptClass(final Class<?> c) {
        return jsConfig_.getDomJavaScriptMapping().get(c);
    }

    /**
     * Gets the associated configuration.
     * @return the configuration
     */
    public JavaScriptConfiguration getJavaScriptConfiguration() {
        return jsConfig_;
    }
}
