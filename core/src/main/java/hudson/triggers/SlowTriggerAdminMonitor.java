package hudson.triggers;

import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import hudson.util.CopyOnWriteMap;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Restricted(NoExternalUse.class)
public class SlowTriggerAdminMonitor extends AdministrativeMonitor {

    private final @Nonnull
    Map<String, Value> errors = new CopyOnWriteMap.Hash<>();

    private static final Logger LOGGER = Logger.getLogger(SlowTriggerAdminMonitor.class.getName());

    public static SlowTriggerAdminMonitor getInstance() {
        return ExtensionList.lookup(SlowTriggerAdminMonitor.class).get(0);
    }

    public SlowTriggerAdminMonitor() {
    }

    @Override
    public boolean isActivated() {
        return !errors.isEmpty();
    }

    @Override
    public @Nonnull String getDisplayName() {
        return "Cron triggers monitor";
    }

    public void clear() {
        errors.clear();
    }

    public void report(@Nonnull String trigger, @Nonnull String msg) {
        errors.put(trigger, new Value(msg));
    }

    public @Nonnull Map<String, Value> getErrors() {
        return new HashMap<>(errors);
    }

    public HttpResponse doClear() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        LOGGER.info("Called clearing...");
        clear();
        return HttpResponses.redirectToDot();
    }

    private class Value {
        private String time;
        private String msg;

        Value(String msg) {
            this.msg = msg;
            this.time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(LocalDateTime.now());
        }

        String getTime() {
            return time;
        }

        String getMsg() {
            return msg;
        }
    }
}
