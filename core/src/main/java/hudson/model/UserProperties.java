package hudson.model;

import hudson.tasks.Mailer;

import java.util.List;

/**
 * List of all installed {@link UserProperty} types.
 * @author Kohsuke Kawaguchi
 */
public class UserProperties {
    public static final List<UserPropertyDescriptor> LIST = Descriptor.<UserPropertyDescriptor>toList(
        Mailer.UserProperty.DESCRIPTOR
    );
}
