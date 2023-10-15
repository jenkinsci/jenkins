package hudson.model;

import jakarta.annotation.PostConstruct;
import jenkins.model.Loadable;

/**
 * Marker interface for Descriptors which use xml persistent data, and as such need to load from disk when instantiated.
 * <p>
 * {@link Descriptor#load()} method is annotated as {@link PostConstruct} so it get automatically invoked after
 * constructor and field injection.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @since 2.140
 */
public interface PersistentDescriptor extends Loadable, Saveable {

    @PostConstruct
    @Override
    void load();
}
