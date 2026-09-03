/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code transient} field as not participating in XStream deserialization.
 *
 * <p>By default, {@link hudson.util.RobustReflectionConverter} intentionally unmarshals
 * into transient fields (unlike stock XStream) to support data migration: old on-disk XML
 * may contain elements that map to fields that have since been made {@code transient}, and
 * their values are consumed, e.g., by {@code readResolve()} to migrate to a newer representation.
 *
 * <p>Some transient fields, however, were fields were never serialized and must not be
 * writable by submitted XML, as doing so can alter the object's identity and bypass
 * access control boundaries.
 *
 * <p>Apply this annotation to transient fields that:
 * <ul>
 *   <li>Represent runtime-derived status or context rather than persisted configuration.</li>
 *   <li>Were never serialized to disk (so no legitimate on-disk XML contains a corresponding element).</li>
 *   <li>Must not be overwritable by user-submitted XML (e.g., via {@code config.xml} POST).</li>
 * </ul>
 *
 * <p>Do <strong>not</strong> use this annotation on transient fields that participate in
 * data migration (i.e., fields that once were non-transient and may still appear in old
 * on-disk XML for consumption, e.g., by {@code readResolve()}). For those fields, consider using
 * {@link XStreamDeserializable} to explicitly opt in once the default behavior changes.
 *
 * <p>For compatibility with plugins targeting older cores, the converter matches this
 * annotation by simple name. A plugin may declare its own {@code @XStreamNotDeserializable}
 * in any package; the contract above applies regardless. This allows plugins to use this
 * protection mechanism without requiring a recent Jenkins core dependency while this addition
 * is still recent. It is expected that this will change in 2027-2028, so once the plugin's core
 * dependency has this annotation, switch to using it directly.
 *
 * @see XStreamDeserializable
 * @see hudson.util.RobustReflectionConverter
 * @since 2.580
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface XStreamNotDeserializable {
}
