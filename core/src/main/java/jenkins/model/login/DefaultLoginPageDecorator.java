package jenkins.model.login;

import hudson.Extension;
import jenkins.model.LoginPageDecorator;

/**
 * In case there are no other implementations we will fallback to this implementation.
 *
 * To make sure that we load this extension last (or at least very late) we use a negative ordinal.
 * This allows custom implementation to be "first"
 */
@Extension(ordinal=-9999)
public class DefaultLoginPageDecorator extends LoginPageDecorator {
}
