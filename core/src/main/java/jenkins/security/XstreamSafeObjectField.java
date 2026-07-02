package jenkins.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@link Object}-typed field as safe to populate during XStream deserialization.
 *
 * <p>By default, {@link hudson.util.RobustReflectionConverter} refuses to deserialize into
 * fields whose static type is {@code Object}. The risk is not that XStream's type filter
 * would admit something obviously dangerous, but that an {@code Object} field is an open
 * sink for any deserialized value that, once placed in the field, can reach Stapler
 * request routing (via {@code getDynamic}, {@code doXxx}, {@code getXxx}, etc.) and
 * act on behalf of the submitting user. Unlike classic Java deserialization gadgets, the
 * payload here is not a chain of {@code readObject} side effects but an ordinary,
 * JEP 200-approved object whose only role is to be reachable by Stapler after the fact.
 * SECURITY-3707 demonstrated this with {@code InternalResourceRequest} and {@code PluginWrapper};
 * the set of types exposing such request-handling behavior is open-ended.
 *
 * <p>Apply this annotation only if you are confident in <strong>at least one</strong> of
 * the following:
 *
 * <ul>
 *   <li>Users cannot control what is written to the field — for instance, the
 *       containing class is never populated from {@code config.xml} submissions or similar
 *       attacker-controlled input.</li>
 *   <li>Values written to the field never reach Stapler dispatch — directly or via a
 *       routable enclosing object.</li>
 * </ul>
 *
 * <p>A more specific static type is always preferable to this annotation: narrowing the
 * field's type moves the check from a human claim to the compiler. Use this annotation
 * only when {@code Object} is genuinely required. Adding it is a security assertion
 * equivalent to extending a deserialization allowlist; a mistake reopens the
 * SECURITY-3707 attack surface.
 *
 * <p>For compatibility with plugins targeting older cores, the converter matches this
 * annotation by simple name. A plugin may declare its own {@code @XstreamSafeObjectField}
 * in any package; the contract above applies regardless. This is expected to be temporary,
 * plugins should switch to depend on an LTS release of Jenkins declaring this annotation
 * as soon as is feasible.
 *
 * @see <a href="https://www.jenkins.io/security/issue/SECURITY-3707/">SECURITY-3707</a>
 * @see <a href="https://jenkins.io/jep/200">JEP-200: Switch Remoting/XStream denylist to an allowlist</a>
 *
 * @since TODO
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface XstreamSafeObjectField {
}
