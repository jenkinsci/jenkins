package jenkins.data;

import jenkins.data.model.CNode;
import org.apache.log4j.spi.ModelBinder;
import org.kohsuke.stapler.Stapler;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class DataContext {

    private Deprecation deprecation = Deprecation.reject;
    private Restriction restriction = Restriction.reject;
    private Unknown unknown = Unknown.reject;

    private transient List<Listener> listeners = new ArrayList<>();

    private transient final ModelBinderRegistry registry;

    public ConfigurationContext(ModelBinderRegistry registry) {
        this.registry = registry;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void warning(@Nonnull CNode node, @Nonnull String message) {
        for (Listener listener : listeners) {
            listener.warning(node, message);
        }
    }

    public Deprecation getDeprecated() { return deprecation; }

    public Restriction getRestricted() { return restriction; }

    public Unknown getUnknown() { return unknown; }

    public void setDeprecated(Deprecation deprecation) {
        this.deprecation = deprecation;
    }

    public void setRestricted(Restriction restriction) {
        this.restriction = restriction;
    }

    public void setUnknown(Unknown unknown) {
        this.unknown = unknown;
    }


    // --- delegate methods for ConfigurationContext


    @Override
    @CheckForNull
    public RootElementModelBinder lookupRootElement(String name) {
        return registry.lookupRootElement(name);
    }

    @Override
    @Nonnull
    public DataModel lookupOrFail(Type type) throws ModelBinderException {
        return registry.lookupOrFail(type);
    }

    @Override
    @CheckForNull
    public DataModel lookup(Type type) {
        return registry.lookup(type);
    }

    /**
     * the model-introspection model to be applied by configuration-as-code.
     * as we move forward, we might need to introduce some breaking change in the way we discover
     * configurable data model from live jenkins instance. At this time, 'new' mechanism will
     * only be enabled if yaml source do include adequate 'version: x'.
     */
    private Version version = Version.ONE;

    public void setVersion(Version version) {
        this.version = version;
    }

    public Version getVersion() {
        return version;
    }

    public <T> DataModel<T> getReflectionBinder(Class<T> type) {
        throw new UnsupportedOperationException();
    }

    // Once we introduce some breaking change on the model inference mechanism, we will introduce `TWO` and so on
    // And this new mechanism will only get enabled when configuration file uses this version or later
    enum Version { ONE("1");

        private final String value;

        Version(String value) {
            this.value = value;
        }

        public static Version of(String version) {
            switch (version) {
                case "1": return Version.ONE;
                default: throw new IllegalArgumentException("unsupported version "+version);
            }
        }

        public String value() {
            return value;
        }

        public boolean isAtLeast(Version version) {
            return this.ordinal() >= version.ordinal();
        }
    }

    static {
        Stapler.CONVERT_UTILS.register((type, value) -> Version.of(value.toString()), Version.class);
    }

    /**
     * Policy regarding unknown attributes.
     */
    enum Unknown { reject, warn }

    /**
     * Policy regarding {@link org.kohsuke.accmod.Restricted} attributes.
     */
    enum Restriction { reject, beta, warn }

    /**
     * Policy regarding {@link Deprecated} attributes.
     */
    enum Deprecation { reject, warn }

    @FunctionalInterface
    public interface Listener {
        void warning(@Nonnull CNode node, @Nonnull String error);
    }
}
