/*
 * The MIT License
 *
 * Copyright (c) 2014, Stephen Connolly
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

package jenkins.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.ProtectedExternally;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The strategy to use for manipulating converting names (e.g. user names, group names, etc) into ids.
 *
 * @since 1.566
 */
public abstract class IdStrategy implements Describable<IdStrategy>, ExtensionPoint,
        Comparator<String> {

    private static final Pattern PSEUDO_UNICODE_PATTERN = Pattern.compile("\\$[a-f0-9]{4}");
    private static final Pattern CAPITALIZATION_PATTERN = Pattern.compile("~[a-z]");

    /**
     * The default case insensitive strategy.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "used in several plugins")
    public static IdStrategy CASE_INSENSITIVE = new CaseInsensitive();

    /**
     * No longer used. This method is now a no-op but the signature is retained for backward compatibility.
     *
     * @param id the id.
     * @return the name.  Must be filesystem safe.
     * @deprecated No current use.
     */
    @Deprecated
    public String filenameOf(@NonNull String id) {
        return null;
    }

    /**
     * No longer used. This method is now a no-op but the signature is retained for backward compatibility.
     *
     * @param id the id
     * @return the name
     * @deprecated No current use.
     */
    @Deprecated
    @Restricted(ProtectedExternally.class)
    public String legacyFilenameOf(@NonNull String id) {
        return null;
    }

    /**
     * Converts a filename into the corresponding id.  This may contain filesystem unsafe characters.
     *
     * @param filename the filename.
     * @return the corresponding id.
     * @since 1.577
     * @deprecated Use only for migrating to new format. After the migration an id is no longer represented by a filename (directory).
     */
    @Deprecated
    public String idFromFilename(@NonNull String filename) {
        return filename;
    }

    /**
     * Converts an ID into a key for use in a Java Map or similar. This controls uniqueness of ids and how multiple different
     * ids may map to the same id. For example, all different capitalizations of "Foo" may map to the same value "foo".
     *
     * @param id the id.
     * @return the key.
     */
    @NonNull
    public String keyFor(@NonNull String id) {
        return id;
    }

    /**
     * Compare two IDs and return {@code true} IFF the two ids are the same. Normally we expect that this should be
     * the same as {@link #compare(String, String)} being equal to {@code 0}, however there may be a specific reason
     * for going beyond that, such as sorting id's case insensitively while treating them as case sensitive.
     *
     * Subclasses may want to override this naïve implementation that calls {@code compare(id1, id2) == 0} for a more performant implementation.
     *
     * @param id1 the first id.
     * @param id2 the second id.
     * @return {@code true} if and only if the two ids are the same.
     */
    public boolean equals(@NonNull String id1, @NonNull String id2) {
        return compare(id1, id2) == 0;
    }

    /**
     * Compare two IDs and return their sorting order. If {@link #equals(String, String)} is {@code true} then this
     * must return {@code 0} but {@link #compare(String, String)} returning {@code 0} need not imply that
     * {@link #equals(String, String)} is {@code true}.
     *
     * @param id1 the first id.
     * @param id2 the second id.
     * @return the sorting order of the two IDs.
     */
    @Override
    public abstract int compare(@NonNull String id1, @NonNull String id2);

    @Override
    public IdStrategyDescriptor getDescriptor() {
        return (IdStrategyDescriptor) Describable.super.getDescriptor();
    }

    /**
     * This method is used to decide whether a {@link hudson.model.User#rekey()} operation is required.
     *
     * @param obj the object to compare with.
     * @return {@code true} if and only if {@code this} is the same as {@code obj}.
     */
    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj != null && getClass().equals(obj.getClass()));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return getClass().getName();
    }

    /**
     * Returns all the registered {@link IdStrategy} descriptors.
     */
    public static DescriptorExtensionList<IdStrategy, IdStrategyDescriptor> all() {
        return Jenkins.get().getDescriptorList(IdStrategy.class);
    }

    String applyPatternRepeatedly(@NonNull Pattern pattern, @NonNull String filename,
                                  @NonNull Function<String, Character> converter) {
        StringBuilder id = new StringBuilder();
        int beginIndex = 0;
        Matcher matcher = pattern.matcher(filename);
        while (matcher.find()) {
            String group = matcher.group();
            id.append(filename, beginIndex, matcher.start());
            id.append(converter.apply(group));
            beginIndex = matcher.end();
        }
        id.append(filename.substring(beginIndex));
        return id.toString();
    }

    Character convertPseudoUnicode(String matchedGroup) {
        return (char) Integer.parseInt(matchedGroup.substring(1), 16);
    }

    /**
     * The default case insensitive {@link IdStrategy}
     */
    public static class CaseInsensitive extends IdStrategy implements Serializable {

        private static final long serialVersionUID = -7244768200684861085L;

        @DataBoundConstructor
        public CaseInsensitive() {}

        @Override
        public String idFromFilename(@NonNull String filename) {
            String id = applyPatternRepeatedly(PSEUDO_UNICODE_PATTERN, filename, this::convertPseudoUnicode);
            return id.toLowerCase(Locale.ENGLISH);
        }

        @Override
        @NonNull
        public String keyFor(@NonNull String id) {
            return id.toLowerCase(Locale.ENGLISH);
        }

        @Override
        public boolean equals(@NonNull String id1, @NonNull String id2) {
            return id1.equalsIgnoreCase(id2);
        }

        @Override
        public int compare(@NonNull String id1, @NonNull String id2) {
            return id1.compareToIgnoreCase(id2);
        }

        @Extension @Symbol("caseInsensitive")
        public static class DescriptorImpl extends IdStrategyDescriptor {

            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.IdStrategy_CaseInsensitive_DisplayName();
            }
        }
    }

    /**
     * A case sensitive {@link IdStrategy}
     */
    public static class CaseSensitive extends IdStrategy implements Serializable {

        private static final long serialVersionUID = 8339425353883308324L;

        @DataBoundConstructor
        public CaseSensitive() {}

        @Override
        public String idFromFilename(@NonNull String filename) {
            String id = applyPatternRepeatedly(CAPITALIZATION_PATTERN, filename, this::convertCapitalizedAscii);
            return applyPatternRepeatedly(PSEUDO_UNICODE_PATTERN, id, this::convertPseudoUnicode);
        }

        private Character convertCapitalizedAscii(String encoded) {
            return encoded.toUpperCase().charAt(1);
        }

        @Override
        public boolean equals(@NonNull String id1, @NonNull String id2) {
            return Objects.equals(id1, id2);
        }

        @Override
        public int compare(@NonNull String id1, @NonNull String id2) {
            return id1.compareTo(id2);
        }

        @Extension @Symbol("caseSensitive")
        public static class DescriptorImpl extends IdStrategyDescriptor {

            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.IdStrategy_CaseSensitive_DisplayName();
            }
        }
    }

    /**
     * A case sensitive email address {@link IdStrategy}. Providing this implementation among the set of default
     * implementations as given the history of misunderstanding in the Jenkins code base around ID case sensitivity,
     * if not provided people will get this wrong.
     * <p>
     * Note: Not all email addresses are case sensitive. It is knowledge that belongs to the server that holds the
     * mailbox. Most sane system administrators do not configure their accounts using case sensitive mailboxes
     * but the RFC does allow them the option to configure that way. Domain names are always case insensitive per RFC.
     */
    public static class CaseSensitiveEmailAddress extends CaseSensitive implements Serializable {

        private static final long serialVersionUID = -5713655323057260180L;

        @DataBoundConstructor
        public CaseSensitiveEmailAddress() {}

        @Override
        public boolean equals(@NonNull String id1, @NonNull String id2) {
            return Objects.equals(keyFor(id1), keyFor(id2));
        }

        @Override
        @NonNull
        public String keyFor(@NonNull String id) {
            int index = id.lastIndexOf('@'); // The @ can be used in local-part if quoted correctly
            // => the last @ is the one used to separate the domain and local-part
            return index == -1 ? id : id.substring(0, index) + id.substring(index).toLowerCase(Locale.ENGLISH);
        }

        @Override
        public int compare(@NonNull String id1, @NonNull String id2) {
            return keyFor(id1).compareTo(keyFor(id2));
        }

        @Extension
        public static class DescriptorImpl extends IdStrategyDescriptor {

            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.IdStrategy_CaseSensitiveEmailAddress_DisplayName();
            }
        }
    }
}
