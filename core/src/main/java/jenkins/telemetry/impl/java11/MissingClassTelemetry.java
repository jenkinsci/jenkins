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
import hudson.ClassicPluginStrategy;
import hudson.Extension;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import jenkins.telemetry.Telemetry;
import jenkins.util.AntClassLoader;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Telemetry class to gather information about class loading issues when running on java 11.
 * The use of this class is not restricted for external use because we may want to gather information from plugins
 * directly if they have method generating exceptions in some methods and sometimes the exceptions are caught (good
 * behaviour) and sometimes they are left go up (wrong behavior), see: {@link #getCollectibleThrowables()}.
 **/
@Extension
//@Restricted(NoExternalUse.class)
public class MissingClassTelemetry extends Telemetry {
    private static final Logger LOGGER = Logger.getLogger(MissingClassTelemetry.class.getName());

    // Store 100 events today
    private static MissingClassEvents events = new MissingClassEvents();

    // When we begin to gather these data
    private final static LocalDate START = LocalDate.of(2019, 4, 1);
    // Gather for 2 years (who knows how long people will need to migrate to Java 11)
    private final static LocalDate END = START.plusMonths(24);

    /**
     * Classes to be caught in {@link hudson.init.impl.InstallUncaughtExceptionHandler}
     */
    private final static Class[] UNCAUGHT_EXCEPTIONS = new Class[] {ClassNotFoundException.class, NoClassDefFoundError.class};

    /**
     * Places where a ClassNotFoundException is going to be thrown but it's ignored later in the code, so we
     * don't have to send this exception.
     */
    private static String[][] IGNORED_PLACES = {
            // This method produces some CNFEs caught later in code. So we set a reportExceptionIfNeeded specifically in
            // the right lines inside the method.
            {AntClassLoader.class.getName(), "loadClass" },
            {XStream2.class.getName(), "findConverter"},
            {ClassicPluginStrategy.class.getName() + "$" + "DependencyClassLoader", "findClass"},
            {groovy.grape.Grape.class.getName(), "getInstance"},
            {groovy.lang.MetaClassImpl.class.getName(), "addProperties"},
            // This method produces some CNFEs caught later in code. So we set a reportExceptionIfNeeded specifically in
            // the right lines inside the method.
            {hudson.PluginManager.UberClassLoader.class.getName(), "findClass"}
    };

    /**
     * Packages removed from java8 up to java11
     * https://blog.codefx.org/java/java-11-migration-guide/
     */
    private final static String[] MOVED_PACKAGES = new String[] {"javax.activation", "javax.annotation", "javax.jws",
            "javax.rmi", "javax.transaction", "javax.xml.bind", "javax.xml.soap", "javax.xml.ws", "org.omg",
            "javax.activity", "com.sun", "sun"};

    @Nonnull
    @Override
    public String getDisplayName() {
        return "Java 11 related problems";
    }

    @Nonnull
    @Override
    public LocalDate getStart() {
        return START;
    }

    @Nonnull
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

    @CheckForNull
    @Override
    public JSONObject createContent() {
        JSONObject info = new JSONObject();
        info.put("core", Jenkins.getVersion() != null ? Jenkins.getVersion().toString() : "UNKNOWN");
        info.put("clientDate", clientDateString());
        info.put("classmissingevents", formatEventsAndInitialize());

        return JSONObject.fromObject(info);
    }

    /**
     * Returns the events gathered as a Map ready to use in a Json object to send via telemetry and clean the map to
     * gather another window of events.
     * @return the map of missed classes events gathered along this window of java 11 telemetry
     */
    @Nonnull
    private JSONArray formatEventsAndInitialize() {
        // Save the current events and clean for next (not this one) telemetry send
        ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> toReport = MissingClassTelemetry.events.getEventsAndClean();
        if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("Cleaned events for missed classes on telemetry for Java 11");

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
    @Nonnull
    private JSONArray formatEvents(@Nonnull ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> events) {
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
    @Nonnull
    static String clientDateString() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(tz); // strip timezone
        return df.format(new Date());
    }

    /**
     * Store the exception if it's from a split package of Java. We use this way in some specific ignored places where
     * the general {@link CatcherClassLoader} is not going to throw anything.
     * @param name the name of the class
     * @param e the exception thrown
     */
    public static void reportExceptionIfNeeded(@Nonnull String name, @Nonnull Throwable e) {
        if (isFromMovedPackage(name)) {
            events.put(e);

            if (LOGGER.isLoggable(Level.FINE)) LOGGER.log(Level.FINE, "Added a missed class for Java 11 telemetry. Class: " + name, e);
        }
    }

    /**
     * There are some places where a {@link ClassNotFoundException} could be thrown but it's ignored later in code. So
     * we only throw the exception from our {@link CatcherClassLoader} if we are not in such places.
     * @param name The name of the class
     * @param e the throwable where it was thrown
     */
    public static void reportExceptionIfAllowedAndConvenient(@Nonnull String name, @Nonnull Throwable e) {
        if (!calledFromIgnoredPlace(e)) {
            reportExceptionIfNeeded(name, e);
        }
    }

    private static boolean isFromMovedPackage(@Nonnull String clazz) {
        for (String movedPackage : MOVED_PACKAGES) {
            if (clazz.startsWith(movedPackage)) {
                return true;
            }
        }
        return false;
    }

    /**
     * List of exception classes we are going to send via telemetry.
     * @return the list of exception classes
     */
    public static Class[] getCollectibleThrowables() {
        return Arrays.copyOf(UNCAUGHT_EXCEPTIONS, UNCAUGHT_EXCEPTIONS.length);
    }

    /**
     * Determine if the exception specified was thrown from an ignored place
     * @param throwable The exception thrown
     * @return true if in the stack trace there is an ignored method / class.
     */
    private static boolean calledFromIgnoredPlace(@Nonnull Throwable throwable) {
        for(String[] ignoredPlace : IGNORED_PLACES) {
            if (calledFrom(throwable, ignoredPlace[0], ignoredPlace[1])) {
                //LOGGER.log(Level.FINE, "{0} called from ignored place ({1}.{2}", new Object[] {throwable, ignoredPlace.clazz, ignoredPlace.method});
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
    private static boolean calledFrom (@Nonnull Throwable throwable, @Nonnull String clazz, @Nonnull String method){
        StackTraceElement[] trace = throwable.getStackTrace();
        for (StackTraceElement el : trace) {
            //If the exception has the class and method searched, it's called from there
            if (clazz.equals(el.getClassName()) && el.getMethodName().equals(method)) {
                //LOGGER.log(Level.FINE, "{0}.{1} tried to load the class {2}", new Object[]{clazz, method, throwable.getMessage()});
                return true;
            }
        }
        return false;
    }
}
