package hudson.tools;

import hudson.Extension;

/**
 * Descriptor for {@link ToolProperty}.
 *
 * <p>
 * Put {@link Extension} on your descriptor implementation to have it auto-registered.
 *
 * @since 1.286
 * @see ToolProperty
 * @author Kohsuke Kawaguchi
 */
public abstract class ToolPropertyDescriptor extends PropertyDescriptor<ToolProperty<?>,ToolInstallation> {
    protected ToolPropertyDescriptor(Class<? extends ToolProperty<?>> clazz) {
        super(clazz);
    }

    protected ToolPropertyDescriptor() {
    }
}

