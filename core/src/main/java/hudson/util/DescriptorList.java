package hudson.util;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.Stapler;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * List of {@link Descriptor}s.
 *
 * <p>
 * This class is really just a list but also defines
 * some Hudson specific methods that operate on
 * {@link Descriptor} list.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.161
 */
public final class DescriptorList<T extends Describable<T>> extends CopyOnWriteArrayList<Descriptor<T>> {
    public DescriptorList(Descriptor<T>... descriptors) {
        super(descriptors);
    }

    /**
     * Creates a new instance of a {@link Describable}
     * from the structured form submission data posted
     * by a radio button group. 
     */
    public T newInstanceFromRadioList(JSONObject config) throws FormException {
        if(config.isNullObject())
            return null;    // none was selected
        int idx = config.getInt("value");
        return get(idx).newInstance(Stapler.getCurrentRequest(),config);
    }

    public T newInstanceFromRadioList(JSONObject parent, String name) throws FormException {
        return newInstanceFromRadioList(parent.getJSONObject(name));
    }

    /**
     * No-op method used to force the class initialization of the given class.
     * The class initialization in turn is expected to put the descriptor
     * into the {@link DescriptorList}.
     *
     * <p>
     * This is necessary to resolve the class initialization order problem.
     * Often a {@link DescriptorList} is defined in the base class, and
     * when it tries to initialize itself by listing up descriptors of known
     * sub-classes, they might not be available in time.
     *
     * @since 1.162
     */
    public void load(Class<? extends Describable> c) {
        try {
            Class.forName(c.getName(), true, c.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);  // Can't happen
        }
    }
}
