package jenkins.security;

import hudson.remoting.Callable;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * Annotates {@link Callable} that are meant for the slave to execute on the master.
 *
 * @author Kohsuke Kawaguchi
 * @see MasterToSlave
 * @see CallableDirectionChecker
 * @since 1.THU
 */
@Retention(RUNTIME)
@Target(TYPE)
@Inherited
@Documented
public @interface SlaveToMaster {
}
