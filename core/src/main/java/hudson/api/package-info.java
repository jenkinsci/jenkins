/**
 * Hudson remote XML/JSON API support.
 *
 * <h2>Design</h2>
 * <p>
 * Hudson provides the JSON/XML dump of its model objects
 * (mostly {@link ModelObject}s but can be others.) See
 * <a href="https://hudson.deva.java.net/remote-api.html"> for the user-level
 * documentation of this feature.
 *
 * <p>
 * This mechanism is implemented by marking model objects
 * by using {@link ExposedBean} and {@link Exposed} annotations.
 * Those annotation together designates what properties are exposed
 * to those remote APIs.
 *
 * <p>
 * So generally speaking the model classes only need to annotate themselves
 * by those annotations to get their data exposed to the remote API.
 *
 * @since 1.101
 */
package hudson.api;

import hudson.model.ModelObject;