package hudson.triggers;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.interceptor.RequirePOST;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Restricted(NoExternalUse.class)
@Extension
public class SlowTriggerAdminMonitor extends AdministrativeMonitor {

    @NonNull
    private final Map<String, Value> errors = new ConcurrentHashMap<>();

    public static int MAX_ENTRIES = 10;

    @NonNull
    private static final Logger LOGGER = Logger.getLogger(SlowTriggerAdminMonitor.class.getName());

    @NonNull
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
    @NonNull
    public String getDisplayName() {
        return Messages.SlowTriggerAdminMonitor_DisplayName();
    }

    public void clear() {
        synchronized (errors) {
            errors.clear();
        }
    }

    public void report(@NonNull final String trigger, @NonNull final String msg) {

        synchronized (errors) {
            if (errors.size() >= MAX_ENTRIES && !errors.containsKey(trigger)) {
                String oldest_trigger = null;
                LocalDateTime oldest_time = null;
                for (Map.Entry<String, Value> entry : errors.entrySet()) {
                    String local_trigger = entry.getKey();
                    if (oldest_trigger == null
                            || entry.getValue().getTimeLDT().compareTo(oldest_time) < 0) {
                        oldest_trigger = local_trigger;
                        oldest_time = entry.getValue().getTimeLDT();
                    }
                }
                errors.remove(oldest_trigger);
            }
        }
        errors.put(trigger, new Value(msg));
    }

    @NonNull
    public Map<String, Value> getErrors() {
        return new HashMap<>(errors);
    }

    @Restricted(DoNotUse.class)
    @RequirePOST
    @NonNull
    public HttpResponse doClear() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        clear();
        return HttpResponses.redirectViaContextPath("/manage");
    }

    public class Value {

        private final LocalDateTime time;
        private final String msg;

        Value(@NonNull String msg) {
            this.msg = msg;
            this.time = LocalDateTime.now();
        }

        @NonNull
        public String getTime() {
            return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(time);
        }

        @NonNull
        protected LocalDateTime getTimeLDT() {
            return time;
        }

        @NonNull
        public String getMsg() {
            return msg;
        }
    }
}
