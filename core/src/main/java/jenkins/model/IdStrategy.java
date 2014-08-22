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

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.util.CaseInsensitiveComparator;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.Locale;

/**
 * The strategy to use for manipulating converting names (e.g. user names, group names, etc) into ids.
 *
 * @since 1.566
 */
public abstract class IdStrategy extends AbstractDescribableImpl<IdStrategy> implements ExtensionPoint,
        Comparator<String> {

    /**
     * The default case insensitive strategy.
     */
    public static IdStrategy CASE_INSENSITIVE = new CaseInsensitive();

    /**
     * Converts an ID into a name that for use as a filename.
     *
     * @param id the id. Note, this method assumes that the id does not contain any filesystem unsafe characters.
     * @return the name.
     */
    @Nonnull
    public abstract String filenameOf(@Nonnull String id);

    /**
     * Converts a filename into the corresponding id.
     * @param filename the filename.
     * @return the corresponding id.
     * @since 1.577
     */
    public String idFromFilename(@Nonnull String filename) {
        return filename;
    }

    /**
     * Converts an ID into a key for use in a Java Map.
     *
     * @param id the id.
     * @return the key.
     */
    @Nonnull
    public abstract String keyFor(@Nonnull String id);

    /**
     * Compare two IDs and return {@code true} IFF the two ids are the same. Normally we expect that this should be
     * the same as {@link #compare(String, String)} being equal to {@code 0}, however there may be a specific reason
     * for going beyond that, such as sorting id's case insensitively while treating them as case sensitive.
     *
     * @param id1 the first id.
     * @param id2 the second id.
     * @return {@code true} if and only if the two ids are the same.
     */
    public boolean equals(@Nonnull String id1, @Nonnull String id2) {
        return compare(id1, id2) == 0;
    }

    /**
     * Compare tow IDs and return their sorting order. If {@link #equals(String, String)} is {@code true} then this
     * must return {@code 0} but {@link #compare(String, String)} returning {@code 0} need not imply that
     * {@link #equals(String, String)} is {@code true}.
     *
     * @param id1 the first id.
     * @param id2 the second id.
     * @return the sorting order of the two IDs.
     */
    @Override
    public abstract int compare(@Nonnull String id1, @Nonnull String id2);

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public IdStrategyDescriptor getDescriptor() {
        return (IdStrategyDescriptor) super.getDescriptor();
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

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getClass().getName();
    }

    /**
     * Returns all the registered {@link IdStrategy} descriptors.
     */
    public static DescriptorExtensionList<IdStrategy, IdStrategyDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(IdStrategy.class);
    }

    /**
     * The default case insensitive {@link IdStrategy}
     */
    public static class CaseInsensitive extends IdStrategy {

        @DataBoundConstructor
        public CaseInsensitive() {}

        @Override
        @Nonnull
        public String filenameOf(@Nonnull String id) {
            return id.toLowerCase(Locale.ENGLISH);
        }

        /**
         * {@inheritDoc}
         */
        @Nonnull
        public String keyFor(@Nonnull String id) {
            return id.toLowerCase(Locale.ENGLISH);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compare(@Nonnull String id1, @Nonnull String id2) {
            return CaseInsensitiveComparator.INSTANCE.compare(id1, id2);
        }

        @Extension
        public static class DescriptorImpl extends IdStrategyDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.IdStrategy_CaseInsensitive_DisplayName();
            }
        }
    }

    /**
     * A case sensitive {@link IdStrategy}
     */
    public static class CaseSensitive extends IdStrategy {

        @DataBoundConstructor
        public CaseSensitive() {}

        /**
         * {@inheritDoc}
         */
        @Override
        @Nonnull
        public String filenameOf(@Nonnull String id) {
            if (id.matches("[a-z0-9_. -]+")) {
                return id;
            } else {
                StringBuilder buf = new StringBuilder(id.length() + 16);
                for (char c : id.toCharArray()) {
                    if ('a' <= c && c <= 'z') {
                        buf.append(c);
                    } else if ('0' <= c && c <= '9') {
                        buf.append(c);
                    } else if ('_' == c || '.' == c || '-' == c || ' ' == c || '@' == c) {
                        buf.append(c);
                    } else if ('A' <= c && c <= 'Z') {
                        buf.append('~');
                        buf.append(Character.toLowerCase(c));
                    } else {
                        buf.append('$');
                        buf.append(StringUtils.leftPad(Integer.toHexString(c & 0xffff), 4, '0'));
                    }
                }
                return buf.toString();
            }
        }

        @Override
        public String idFromFilename(@Nonnull String filename) {
            if (filename.matches("[a-z0-9_. -]+")) {
                return filename;
            } else {
                StringBuilder buf = new StringBuilder(filename.length());
                final char[] chars = filename.toCharArray();
                for (int i = 0; i < chars.length; i++) {
                    char c = chars[i];
                    if ('a' <= c && c <= 'z') {
                        buf.append(c);
                    } else if ('0' <= c && c <= '9') {
                        buf.append(c);
                    } else if ('_' == c || '.' == c || '-' == c || ' ' == c || '@' == c) {
                        buf.append(c);
                    } else if (c == '~') {
                        i++;
                        if (i < chars.length) {
                            buf.append(Character.toUpperCase(chars[i]));
                        }
                    } else if (c == '$') {
                        StringBuilder hex = new StringBuilder(4);
                        i++;
                        if (i < chars.length) {
                            hex.append(chars[i]);
                        } else {
                            break;
                        }
                        i++;
                        if (i < chars.length) {
                            hex.append(chars[i]);
                        } else {
                            break;
                        }
                        i++;
                        if (i < chars.length) {
                            hex.append(chars[i]);
                        } else {
                            break;
                        }
                        i++;
                        if (i < chars.length) {
                            hex.append(chars[i]);
                        } else {
                            break;
                        }
                        buf.append(Character.valueOf((char)Integer.parseInt(hex.toString(), 16)));
                    }
                }
                return buf.toString();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(@Nonnull String id1, @Nonnull String id2) {
            return StringUtils.equals(id1, id2);
        }

        /**
         * {@inheritDoc}
         */
        @Nonnull
        public String keyFor(@Nonnull String id) {
            return id;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compare(@Nonnull String id1, @Nonnull String id2) {
            return id1.compareTo(id2);
        }

        @Extension
        public static class DescriptorImpl extends IdStrategyDescriptor {

            /**
             * {@inheritDoc}
             */
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
     * <p/>
     * Note: Not all email addresses are case sensitive. It is knowledge that belongs to the server that holds the
     * mailbox. Most sane system administrators do not configure their accounts using case sensitive mailboxes
     * but the RFC does allow them the option to configure that way. Domain names are always case insensitive per RFC.
     */
    public static class CaseSensitiveEmailAddress extends CaseSensitive {

        @DataBoundConstructor
        public CaseSensitiveEmailAddress() {}

        /**
         * {@inheritDoc}
         */
        @Override
        @Nonnull
        public String filenameOf(@Nonnull String id) {
            return super.filenameOf(keyFor(id));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(@Nonnull String id1, @Nonnull String id2) {
            return StringUtils.equals(keyFor(id1), keyFor(id2));
        }

        /**
         * {@inheritDoc}
         */
        @Nonnull
        public String keyFor(@Nonnull String id) {
            int index = id.lastIndexOf('@'); // The @ can be used in local-part if quoted correctly
            // => the last @ is the one used to separate the domain and local-part
            return index == -1 ? id : id.substring(0, index) + (id.substring(index).toLowerCase(Locale.ENGLISH));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compare(@Nonnull String id1, @Nonnull String id2) {
            return keyFor(id1).compareTo(keyFor(id2));
        }

        @Extension
        public static class DescriptorImpl extends IdStrategyDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.IdStrategy_CaseSensitiveEmailAddress_DisplayName();
            }
        }
    }
}
