package hudson.triggers;

import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import hudson.util.CopyOnWriteMap;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.interceptor.RequirePOST;

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

    @Nonnull
    private final Map<String, Value> errors = new CopyOnWriteMap.Hash<>();

    @Nonnull
    private static final Logger LOGGER = Logger.getLogger(SlowTriggerAdminMonitor.class.getName());

    @Nonnull
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
    @Nonnull
    public String getDisplayName() {
        return "Cron triggers monitor";
    }

    public void clear() {
        errors.clear();
    }

    public void report(@Nonnull String trigger, @Nonnull String msg) {
        errors.put(trigger, new Value(msg));
    }

    @Nonnull
    public Map<String, Value> getErrors() {
        return new HashMap<>(errors);
    }

    @Restricted(DoNotUse.class)
    @RequirePOST
    @Nonnull
    public HttpResponse doClear() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        LOGGER.info("Called clearing...");
        clear();
        return HttpResponses.redirectViaContextPath("/manage");
    }

    public class Value {

        private final String time;
        private final String msg;

        Value(@Nonnull String msg) {
            this.msg = msg;
            this.time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(LocalDateTime.now());
        }

        @Nonnull
        public String getTime() {
            return time;
        }

        @Nonnull
        public String getMsg() {
            return msg;
        }
    }
}
