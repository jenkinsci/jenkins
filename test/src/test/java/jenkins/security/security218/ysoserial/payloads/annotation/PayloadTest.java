package jenkins.security.security218.ysoserial.payloads.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author mbechler
 *
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface PayloadTest {

    String skip() default "";
    
    String precondition() default "";
    
    String harness() default "";

}
