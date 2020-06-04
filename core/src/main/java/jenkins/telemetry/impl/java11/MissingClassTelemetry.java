/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

package jenkins.telemetry.impl.java11;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.util.VersionNumber;
import io.jenkins.lib.versionnumber.JavaSpecificationVersion;
import jenkins.model.Jenkins;
import jenkins.telemetry.Telemetry;
import jenkins.util.java.JavaUtils;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Telemetry class to gather information about missing classes when running on java 11. This class sends classes not
 * found and in packages related with Java changes from Java 8 to Java 11. See {@link #MOVED_PACKAGES}.
 **/

@Extension
@Restricted(NoExternalUse.class)
public class MissingClassTelemetry extends Telemetry {
    private static final Logger LOGGER = Logger.getLogger(MissingClassTelemetry.class.getName());

    // Store 100 events today
    private static MissingClassEvents events = new MissingClassEvents();

    // When we begin to gather these data
    private final static LocalDate START = LocalDate.of(2019, 4, 1);
    // Gather for 2 years (who knows how long people will need to migrate to Java 11)
    private final static LocalDate END = START.plusMonths(24);

    // The types of exceptions which can be reported
    private static final Set reportableExceptions =
            new HashSet<Class>(Arrays.asList(ClassNotFoundException.class, NoClassDefFoundError.class));

    @VisibleForTesting
    /* package */ static final String CIRCULAR_REFERENCE = "Circular reference found on the exception we are analysing to report via telemetry";

    /**
     * Packages removed from java8 up to java11
     * https://blog.codefx.org/java/java-11-migration-guide/
     */
    private final static String[] MOVED_PACKAGES = new String[] {"javax.activation", "javax.annotation", "javax.jws",
            "javax.rmi", "javax.transaction", "javax.xml.bind", "javax.xml.soap", "javax.xml.ws", "org.omg",
            "javax.activity", "com.sun", "sun"};

    /**
     * Places where a ClassNotFoundException is going to be thrown but it's ignored later in the code, so we
     * don't have to send this exception, even though it might be related with java classes of moved packages
     */
    private static String[][] IGNORED_PLACES = {
            {"hudson.util.XStream2$AssociatedConverterImpl", "findConverter"},
            {"org.jenkinsci.plugins.workflow.cps.global.GrapeHack", "hack"},
            {"org.codehaus.groovy.runtime.callsite.CallSiteArray", "createCallStaticSite"},
            {"groovy.lang.MetaClassImpl", "addProperties"},
            // We set the reportException call directly in this method when it's appropriated
            {"hudson.PluginManager.UberClassLoader", "findClass"},
            {"hudson.ExtensionFinder$GuiceFinder$FaultTolerantScope$1", "get"},
            {"hudson.ExtensionFinder$GuiceFinder$SezpozModule", "resolve"},
            {"java.beans.Introspector", "findCustomizerClass"},
            {"com.sun.beans.finder.InstanceFinder", "instantiate"},
            {"com.sun.beans.finder.ClassFinder", "findClass"},
            {"java.util.ResourceBundle$Control", "newBundle"},
            //hundreds when a job is created
            {"org.codehaus.groovy.control.ClassNodeResolver", "tryAsLoaderClassOrScript"},
            {"org.kohsuke.stapler.RequestImpl$TypePair", "convertJSON"},
            {"net.bull.javamelody.FilterContext", "isMojarraAvailable"}, // JENKINS-60725
            {"hudson.remoting.RemoteClassLoader$ClassLoaderProxy", "fetch3"}, // JENKINS-61521
            //Don't add "java.base/" before sun.reflect.generics.factory.CoreReflectionFactory
            {"sun.reflect.generics.factory.CoreReflectionFactory", "makeNamedType"}, // JENKINS-61920
            
    };

    @NonNull
    @Override
    public String getDisplayName() {
        return "Missing classes related with Java updates";
    }

    @NonNull
    @Override
    public LocalDate getStart() {
        return START;
    }

    @NonNull
    @Override
    public LocalDate getEnd() {
        return END;
    }

    /**
     * To allow asserting this info in tests.
     * @return the events gathered.
     */
    @VisibleForTesting
    /* package */ static MissingClassEvents getEvents() {
        return events;
    }

    /**
     * This telemetry is only enabled when running on Java versions newer than Java 8.
     * @return true if running on a newer Java version than Java 8
     */
    public static boolean enabled() {
        return JavaUtils.getCurrentJavaRuntimeVersionNumber().isNewerThan(JavaSpecificationVersion.JAVA_8);
    }

    @CheckForNull
    @Override
    public JSONObject createContent() {
        // If we are on the time window of this telemetry (checked by the Telemetry class) but we are not running on
        // Java > 1.8 (checked here), we don't send anything
        if (!enabled()) {
            return null;
        }

        // To avoid sending empty events
        JSONArray events = formatEventsAndInitialize();
        if (events.size() == 0) {
            return null;
        }

        JSONObject info = new JSONObject();
        VersionNumber jenkinsVersion = Jenkins.getVersion();
        info.put("core", jenkinsVersion != null ? jenkinsVersion.toString() : "UNKNOWN");
        info.put("clientDate", clientDateString());
        info.put("classMissingEvents", events);

        return JSONObject.fromObject(info);
    }

    /**
     * Returns the events gathered as a Map ready to use in a Json object to send via telemetry and clean the map to
     * gather another window of events.
     * @return the map of missed classes events gathered along this window of telemetry
     */
    @NonNull
    private JSONArray formatEventsAndInitialize() {
        // Save the current events and clean for next (not this one) telemetry send
        ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> toReport = MissingClassTelemetry.events.getEventsAndClean();
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Cleaned events for missing classes");
        }

        return formatEvents(toReport);
    }

    /**
     * Format the events gathered in a map used to create the json object to send via telemetry. The events are named by
     * the class not found. But a class could be repeated if it was thrown from several places. The interesting
     * pieces of information we want to gather are the places where the {@link ClassNotFoundException} or the
     * {@link NoClassDefFoundError} errors happens, rather than the class itself.
     * @param events events collected in this telemetry window.
     * @return the events formatted in a map.
     */
    @NonNull
    private JSONArray formatEvents(@NonNull ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> events) {
        JSONArray jsonEvents = new JSONArray();

        events.forEach((stackTrace, event) -> {
            JSONObject eventObject = new JSONObject();
            eventObject.put("className", event.getClassName());
            eventObject.put("class", event.getClassName());
            eventObject.put("time", event.getTime());
            eventObject.put("occurrences", Long.toString(event.getOccurrences()));
            eventObject.put("stacktrace", event.getStackTrace());

            jsonEvents.add(eventObject);
        });

        return jsonEvents;
    }

    /**
     * The current time in the same way as other telemetry implementations.
     * @return the UTC time formatted with the pattern yyyy-MM-dd'T'HH:mm'Z'
     */
    @NonNull
    static String clientDateString() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(tz); // strip timezone
        return df.format(new Date());
    }

    /**
     * Store the exception if it's from a split package of Java. This method report this exception directly, it doesn't
     * look into the causes or suppressed exceptions of the exception specified. This method tends to be used in the
     * ClassLoader directly. Outside the class loaders is best to use {@link #reportExceptionInside(Throwable)}
     * @param name the name of the class
     * @param e the throwable to report if needed
     */
    public static void reportException(@NonNull String name, @NonNull Throwable e) {
        if (enabled()) {
            //ClassDefFoundError uses / instead of .
            name = name.replace('/', '.').trim();

            // We call the methods in this order because if the missing class is not java related, we don't loop over the
            // stack trace to look if it's not thrown from an ignored place avoiding an impact on performance.
            if (isFromMovedPackage(name) && !calledFromIgnoredPlace(e)) {
                if (LOGGER.isLoggable(Level.WARNING) && !wasAlreadyReported(name)) {
                    LOGGER.log(Level.WARNING, "Added a missed class for missing class telemetry. Class: " + name, e);
                }
                events.put(name, e);
            }
        }
    }

    private static boolean wasAlreadyReported(@NonNull String className) {
        return events.alreadyRegistered(className);
    }
    
    /**
     * Determine if the exception specified was thrown from an ignored place
     * @param throwable The exception thrown
     * @return true if in the stack trace there is an ignored method / class.
     */
    private static boolean calledFromIgnoredPlace(@NonNull Throwable throwable) {
        for(String[] ignoredPlace : IGNORED_PLACES) {
            if (calledFrom(throwable, ignoredPlace[0], ignoredPlace[1])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the throwable was thrown by the class and the method specified.
     * @param throwable stack trace to look at
     * @param clazz class to look for in the stack trace
     * @param method method where the throwable was thrown in the clazz
     * @return true if the method of the clazz has thrown the throwable
     */
    private static boolean calledFrom (@NonNull Throwable throwable, @NonNull String clazz, @NonNull String method){
        StackTraceElement[] trace = throwable.getStackTrace();
        for (StackTraceElement el : trace) {
            //If the exception has the class and method searched, it's called from there
            if (clazz.equals(el.getClassName()) && el.getMethodName().equals(method)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Store the exception extracting the class name from the message of the throwable specified. This method report
     * this exception directly, it doesn't look into the causes or suppressed exceptions of the exception specified.
     * This method tends to be used in the ClassLoader directly. Outside the class loaders is best to use
     * {@link #reportExceptionInside(Throwable)}
     * @param e the exception to report if needed
     */
    private static void reportException(@NonNull Throwable e) {
        if (enabled()) {
            String name = e.getMessage();

            if (name == null || name.trim().isEmpty()) {
                LOGGER.log(Level.INFO, "No class name could be extracted from the throwable to determine if it's reportable", e);
            } else {
                reportException(name, e);
            }
        }
    }

    private static boolean isFromMovedPackage(@NonNull String clazz) {
        for (String movedPackage : MOVED_PACKAGES) {
            if (clazz.startsWith(movedPackage)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Report the class not found if this exception or any of its causes or suppressed exceptions is related to missed
     * classes.
     * @param e the exception to look into
     */
    public static void reportExceptionInside(@NonNull Throwable e) {
        if (enabled()) {
            // Use a Set with equity based on == instead of equal to find cycles
            Set<Throwable> exceptionsReviewed = Collections.newSetFromMap(new IdentityHashMap<>());
            reportExceptionInside(e, exceptionsReviewed);
        }
    }

    /**
     * Find the exception to report among the exception passed and its causes and suppressed exceptions. It does a
     * recursion and uses a Set to avoid circular references.
     * @param e the exception
     * @param exceptionsReviewed the set of already reviewed exceptions
     * @return true if a exception was reported
     */
    private static boolean reportExceptionInside(@NonNull Throwable e, @NonNull Set<Throwable> exceptionsReviewed) {
        if (exceptionsReviewed.contains(e)) {
            LOGGER.log(Level.WARNING, CIRCULAR_REFERENCE, e);
            // Don't go deeper, we already did
            return false;
        }

        // Add this exception to the list of already reviewed exceptions before going deeper in its causes or suppressed
        // exceptions
        exceptionsReviewed.add(e);

        // It this exception is the one searched
        if (isMissedClassRelatedException(e)) {
            MissingClassTelemetry.reportException(e);
            return true;
        }

        // We search in its cause exception
        if (e.getCause() != null) {
            if (reportExceptionInside(e.getCause(), exceptionsReviewed)) {
                return true;
            }
        }

        // We search in its suppressed exceptions
        for (Throwable suppressed: e.getSuppressed()) {
            if (suppressed != null) {
                if (reportExceptionInside(suppressed, exceptionsReviewed)) {
                    return true;
                }
            }
        }

        // If this exception or its ancestors are not related with missed classes
        return false;
    }

    /**
     * Check if the exception specified is related with a missed class, that is, defined in the
     * {@link #reportableExceptions} method.
     * @param e the exception to look into
     * @return true if the class is related with missed classes.
     */
    private static boolean isMissedClassRelatedException(Throwable e) {
        return reportableExceptions.contains(e.getClass());
    }
}
