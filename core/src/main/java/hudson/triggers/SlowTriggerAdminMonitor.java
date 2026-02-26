package hudson.triggers;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Restricted(NoExternalUse.class)
@Extension
public class SlowTriggerAdminMonitor extends AdministrativeMonitor {

    @NonNull
    private final Map<String, Value> errors = new ConcurrentHashMap<>();

    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* Script Console modifiable */ int MAX_ENTRIES = SystemProperties.getInteger(SlowTriggerAdminMonitor.class.getName() + ".maxEntries", 10);

    @NonNull
    private static final Logger LOGGER = Logger.getLogger(SlowTriggerAdminMonitor.class.getName());

    @NonNull
    public static SlowTriggerAdminMonitor getInstance() {
        return ExtensionList.lookup(SlowTriggerAdminMonitor.class).getFirst();
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

    public void report(@NonNull final Class<? extends TriggerDescriptor> trigger, @NonNull final String fullJobName, long duration) {

        synchronized (errors) {
            if (errors.size() >= MAX_ENTRIES && !errors.containsKey(trigger.getName())) {
                String oldest_trigger = null;
                LocalDateTime oldest_time = null;
                for (Map.Entry<String, Value> entry : errors.entrySet()) {
                    String local_trigger = entry.getKey();
                    if (oldest_trigger == null
                            || entry.getValue().time.compareTo(oldest_time) < 0) {
                        oldest_trigger = local_trigger;
                        oldest_time = entry.getValue().time;
                    }
                }
                errors.remove(oldest_trigger);
            }
        }
        // TODO: We do not record multiple occurrences of the same trigger; on which instance would 10 different trigger types take forever? Figure out a better presentation.
        errors.put(trigger.getName(), new Value(trigger, fullJobName, duration));
    }

    @NonNull
    public Map<String, Value> getErrors() {
        return new HashMap<>(errors);
    }

    @Restricted(DoNotUse.class)
    @RequirePOST
    @NonNull
    public HttpResponse doClear() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        clear();
        return HttpResponses.redirectViaContextPath("/manage");
    }

    public static class Value {

        private final LocalDateTime time;
        private Class<? extends TriggerDescriptor> trigger;
        private final String fullJobName;
        private final long duration;

        Value(Class<? extends TriggerDescriptor> trigger, @NonNull String fullJobName, long duration) {
            this.trigger = trigger;
            this.fullJobName = fullJobName;
            this.duration = duration;
            this.time = LocalDateTime.now();
        }

        @NonNull
        public String getTimeString() {
            return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(time);
        }

        @NonNull
        public String getFullJobName() {
            return fullJobName;
        }

        public Class<? extends TriggerDescriptor> getTrigger() {
            return trigger;
        }

        public long getDuration() {
            return duration;
        }
    }
}
