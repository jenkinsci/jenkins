package jenkins.model.logging;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serializable;

/**
 * Object which operates with {@link Loggable} items.
 * @author Oleg Nenashev
 * @since TODO
 */
@ExportedBean
public abstract class LogHandler {

    @CheckForNull
    protected transient Loggable loggable;

    public LogHandler(@Nonnull Loggable loggable) {
        this.loggable = loggable;
    }

    @Exported
    public String getId() {
        return getClass().getName();
    }

    /**
     * Called when the owner is loaded from disk.
     * The owner may be persisted on the disk, so the build reference should be {@code transient} (quasi-{@code final}) and restored here.
     * @param loggable an owner to which this component is associated.
     */
    public void onLoad(@Nonnull Loggable loggable) {
        this.loggable = loggable;
    }

    public static void onLoad(@Nonnull Loggable loggable, @CheckForNull LogHandler logHandler) {
        if (logHandler != null) {
            logHandler.onLoad(loggable);
        }
    }

    @Nonnull
    protected Loggable getOwner() {
        return loggable;
    }
}
