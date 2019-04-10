package jenkins.telemetry.impl.java11;

import hudson.Extension;
import jenkins.model.Jenkins;
import jenkins.telemetry.Telemetry;
import net.sf.json.JSONObject;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Telemetry class to gather information about configuration when running on java 11
 */
@Extension
@Restricted(NoExternalUse.class)
public class Java11Telemetry extends Telemetry {
    // When we begin to gather these data
    private final static LocalDate START = LocalDate.of(2019, 2, 9);
    // Gather for 12 months
    private final static LocalDate END = START.plusMonths(12);

    /**
     * Classes to be caught
     */
    public final static Set<Class> UNCAUGHT_EXCEPTIONS;

    public final static Supplier<Stream<String>> MOVED_PACKAGES;

    static {
        Set<Class> classes = new HashSet<>(4);
        Collections.addAll(classes, ClassNotFoundException.class, NoClassDefFoundError.class, NoSuchMethodError.class, NoSuchMethodException.class);
        UNCAUGHT_EXCEPTIONS = Collections.unmodifiableSet(classes);

        /*
        Moved packages:

        java.activation with javax.activation package
        java.corba with javax.activity, javax.rmi, javax.rmi.CORBA, and org.omg.* packages
        java.transaction with javax.transaction package
        java.xml.bind with all javax.xml.bind.* packages
        java.xml.ws with javax.jws, javax.jws.soap, javax.xml.soap, and all javax.xml.ws.* packages
        java.xml.ws.annotation with javax.annotation package
         */
        MOVED_PACKAGES = () -> Stream.of("java.sql.", "javax.activation.", "javax.annotation.",
                "javax.jws.", "javax.lang.model.", "javax.rmi.", "javax.script.", "javax.smartcardio.",
                "javax.sql.", "javax.tools.", "javax.transaction.", "javax.xml.bind.", "javax.xml.crypto.",
                "javax.xml.soap.", "javax.xml.ws.", "org.omg.", "javax.activity");
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return "Configuration and events related with Java 11";
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

    @CheckForNull
    @Override
    public JSONObject createContent() {
        Map<String, Object> info = new TreeMap<>();
        info.put("core", Jenkins.getVersion().toString());
        info.put("clientDate", clientDateString());
        info.put("extra", "my data");

        return JSONObject.fromObject(info);
    }

    private static String clientDateString() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(tz); // strip timezone
        return df.format(new Date());
    }

    /**
     * Report the exception loading the class to telemetry.
     * TODO: currently is just printing in the console, we have to check if active and store de event
     */
    @Restricted(NoExternalUse.class)
    public static void reportException(String name, ClassNotFoundException e) {
        System.out.println("A class not found, TODO: check if telemetry needed for class '" + name + "'. Stack trace: " + ExceptionUtils.getStackTrace(e));

    }
}
