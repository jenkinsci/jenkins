package jenkins.security;

import hudson.remoting.Callable;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotates {@link Callable} that this callable is meant to be run on the slave.
 *
 * @author Kohsuke Kawaguchi
 * @see SlaveToMaster
 * @see CallableDirectionChecker
 * @since 1.THU
 */
@Retention(RUNTIME)
@Target(TYPE)
@Inherited
@Documented
public @interface MasterToSlave {
}
