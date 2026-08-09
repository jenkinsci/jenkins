/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

package hudson.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import jenkins.util.VirtualFile;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Extension point that allows plugins to filter or transform input streams and metadata served by {@link DirectoryBrowserSupport}.
 *
 * <p>Plugins can implement this extension point to inspect, decode, or transform files served through
 * {@link DirectoryBrowserSupport} (such as build artifacts or workspace files). Potential use cases include, for example:
 * <ul>
 *   <li>Decompressing gzipped files on the fly when requested by a user.</li>
 *   <li>Decrypting encrypted artifact contents.</li>
 * </ul>
 *
 * @since TODO
 */
public abstract class DirectoryBrowserSupportFilter implements ExtensionPoint {

    /**
     * Context object passed to {@link DirectoryBrowserSupportFilter} implementations.
     *
     * <p>This object tracks the file stream and metadata during filtering. Superseded input streams replaced
     * via {@link #setInputStream(InputStream)} are automatically tracked and closed when this context is closed.
     */
    public static final class Context implements AutoCloseable {
        private final VirtualFile file;
        private final StaplerRequest2 request;
        private final boolean view;
        private final List<InputStream> supersededStreams = new ArrayList<>();
        private InputStream inputStream;
        private long length;
        private String fileName;

        public Context(@NonNull VirtualFile file, @Nullable StaplerRequest2 request, @NonNull InputStream inputStream, long length, boolean view) {
            this.file = file;
            this.request = request;
            this.inputStream = inputStream;
            this.length = length;
            this.view = view;
            this.fileName = file.getName();
        }

        public Context(@NonNull VirtualFile file, @Nullable StaplerRequest2 request, @NonNull InputStream inputStream, long length) {
            this(file, request, inputStream, length, false);
        }

        /**
         * Gets the {@link VirtualFile} being served.
         */
        @NonNull
        public VirtualFile getFile() {
            return file;
        }

        /**
         * Gets the current {@link StaplerRequest2}, if available.
         */
        @Nullable
        public StaplerRequest2 getRequest() {
            return request;
        }

        /**
         * Returns whether the request was made in view mode (e.g., {@code /*view*\/}).
         */
        public boolean isView() {
            return view;
        }

        /**
         * Gets the current {@link InputStream} for the file content.
         */
        @NonNull
        public InputStream getInputStream() {
            return inputStream;
        }

        /**
         * Sets a new {@link InputStream} for the file content.
         *
         * <p>If a previous stream is replaced by an independent stream (for example, reading all bytes into a new
         * {@link java.io.ByteArrayInputStream}), the previous stream is marked as superseded and will be closed
         * when this context is closed.
         */
        public void setInputStream(@NonNull InputStream inputStream) {
            if (this.inputStream != inputStream) {
                this.supersededStreams.add(this.inputStream);
                this.inputStream = inputStream;
            }
        }

        /**
         * Gets the length of the stream content in bytes.
         */
        public long getLength() {
            return length;
        }

        /**
         * Sets the length of the stream content in bytes, or {@code -1} if the length is unknown.
         */
        public void setLength(long length) {
            this.length = length;
        }

        /**
         * Gets the filename used for response headers and MIME type matching.
         */
        @NonNull
        public String getFileName() {
            return fileName;
        }

        /**
         * Sets a new filename for the served content.
         */
        public void setFileName(@NonNull String fileName) {
            this.fileName = fileName;
        }

        @Override
        public void close() {
            for (InputStream s : supersededStreams) {
                IOUtils.closeQuietly(s);
            }
        }
    }

    /**
     * Filters or transforms the given file serving context.
     *
     * @param context the current file serving context
     * @return the transformed context (or the same context), or {@code null} if no changes are made
     * @throws IOException if an I/O error occurs during filtering
     */
    @Nullable
    public abstract Context filter(@NonNull Context context) throws IOException;

    /**
     * All registered {@link DirectoryBrowserSupportFilter} instances.
     */
    public static ExtensionList<DirectoryBrowserSupportFilter> all() {
        return ExtensionList.lookup(DirectoryBrowserSupportFilter.class);
    }
}
