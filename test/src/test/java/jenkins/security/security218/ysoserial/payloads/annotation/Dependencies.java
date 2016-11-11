package jenkins.security.security218.ysoserial.payloads.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Dependencies {
	String[] value() default {};

	public static class Utils {
		public static String[] getDependencies(AnnotatedElement annotated) {
			Dependencies deps = annotated.getAnnotation(Dependencies.class);
			if (deps != null && deps.value() != null) {
				return deps.value();
			} else {
				return new String[0];
			}
		}
	}
}
