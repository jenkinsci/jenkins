/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.tasks.BuildWrapper;
import hudson.util.VariableResolver;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import jenkins.util.SystemProperties;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.fileupload2.core.FileItemFactory;
import org.apache.commons.fileupload2.core.FileItemHeaders;
import org.apache.commons.fileupload2.core.FileItemHeadersProvider;
import org.apache.commons.io.FilenameUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * {@link ParameterValue} for {@link FileParameterDefinition}.
 *
 * @author Kohsuke Kawaguchi
 */
public class FileParameterValue extends ParameterValue {
    private static final String FOLDER_NAME = "fileParameters";
    private static final Pattern PROHIBITED_DOUBLE_DOT = Pattern.compile(".*[\\\\/]\\.\\.[\\\\/].*");
    private static final long serialVersionUID = -143427023159076073L;

    /**
     * Escape hatch for SECURITY-1074, fileParameter used to escape their expected folder.
     * It's not recommended to enable for security reasons. That option is only present for backward compatibility.
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* Script Console modifiable */ boolean ALLOW_FOLDER_TRAVERSAL_OUTSIDE_WORKSPACE =
            SystemProperties.getBoolean(FileParameterValue.class.getName() + ".allowFolderTraversalOutsideWorkspace");

    private final transient FileItem file;

    /**
     * The name of the originally uploaded file.
     */
    private final String originalFileName;

    /**
     * Overrides the location in the build to place this file. Initially set to {@link #getName()}
     * The location could be directly the filename or also a hierarchical path.
     * The intermediate folders will be created automatically.
     * Take care that no escape from the current directory is allowed and will result in the failure of the build.
     */
    private String location;

    @DataBoundConstructor
    public FileParameterValue(String name, FileItem file) {
        this(name, file, FilenameUtils.getName(file.getName()));
    }

    /**
     * @deprecated use {@link #FileParameterValue(String, FileItem)}
     */
    @Deprecated
    public FileParameterValue(String name, org.apache.commons.fileupload.FileItem file) {
        this(name, file.toFileUpload2FileItem(), FilenameUtils.getName(file.getName()));
    }

    public FileParameterValue(String name, File file, String originalFileName) {
        this(name, new FileItemImpl2(file), originalFileName);
    }

    protected FileParameterValue(String name, FileItem file, String originalFileName) {
        super(name);
        this.file = file;
        this.originalFileName = originalFileName;
        setLocation(name);
    }

    // post initialization hook
    protected void setLocation(String location) {
        this.location = location;
    }

    public String getLocation() {
        return location;
    }

    @Override
    public Object getValue() {
        return file;
    }

    /**
     * Exposes the originalFileName as an environment variable.
     */
    @Override
    public void buildEnvironment(Run<?, ?> build, EnvVars env) {
        env.put(name, originalFileName);
    }

    @Override
    public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
        return name -> FileParameterValue.this.name.equals(name) ? originalFileName : null;
    }

    /**
     * Get the name of the originally uploaded file. If this
     * {@link FileParameterValue} was created prior to 1.362, this method will
     * return {@code null}.
     *
     * @return the name of the originally uploaded file
     */
    public String getOriginalFileName() {
        return originalFileName;
    }

    public FileItem getFile2() {
        return file;
    }

    /**
     * @deprecated use {@link #getFile2}
     */
    @Deprecated
    public org.apache.commons.fileupload.FileItem getFile() {
        return org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(getFile2());
    }

    @Override
    public BuildWrapper createBuildWrapper(AbstractBuild<?, ?> build) {
        return new BuildWrapper() {
            @SuppressFBWarnings(value = {"FILE_UPLOAD_FILENAME", "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"}, justification = "TODO needs triage")
            @Override
            public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
                if (location != null && !location.isEmpty() && file.getName() != null && !file.getName().isEmpty()) {
                    listener.getLogger().println("Copying file to " + location);
                    FilePath ws = build.getWorkspace();
                    if (ws == null) {
                        throw new IllegalStateException("The workspace should be created when setUp method is called");
                    }
                    if (!ALLOW_FOLDER_TRAVERSAL_OUTSIDE_WORKSPACE && (PROHIBITED_DOUBLE_DOT.matcher(location).matches() || !ws.isDescendant(location))) {
                        listener.error("Rejecting file path escaping base directory with relative path: " + location);
                        // force the build to fail
                        return null;
                    }
                    FilePath locationFilePath = ws.child(location);
                    locationFilePath.getParent().mkdirs();

                    // TODO Remove this workaround after FILEUPLOAD-293 is resolved.
                    if (locationFilePath.exists() && !locationFilePath.isDirectory()) {
                        locationFilePath.delete();
                    }

                    locationFilePath.copyFrom(file);
                    locationFilePath.copyTo(new FilePath(getLocationUnderBuild(build)));
                }
                return new Environment() {};
            }
        };
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result
                + (location == null ? 0 : location.hashCode());
        return result;
    }

    /**
     * Compares file parameters (existing files will be considered as different).
     * @since 1.586 Function has been modified in order to avoid <a href="https://issues.jenkins.io/browse/JENKINS-19017">JENKINS-19017</a> issue (wrong merge of builds in the queue).
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        FileParameterValue other = (FileParameterValue) obj;

        if (location == null && other.location == null)
            return true; // Consider null parameters as equal

        //TODO: check fingerprints or checksums to improve the behavior (JENKINS-25211)
        // Return false even if files are equal
        return false;
    }

    @Override
    public String toString() {
        return "(FileParameterValue) " + getName() + "='" + originalFileName + "'";
    }

    @Override public String getShortDescription() {
        return name + "=" + originalFileName;
    }

    /**
     * Serve this file parameter in response to a {@link StaplerRequest2}.
     */
    public DirectoryBrowserSupport doDynamic(StaplerRequest2 request, StaplerResponse2 response) {
        AbstractBuild build = (AbstractBuild) request.findAncestor(AbstractBuild.class).getObject();
        File fileParameter = getFileParameterFolderUnderBuild(build);
        return new DirectoryBrowserSupport(build, new FilePath(fileParameter), Messages.FileParameterValue_IndexTitle(), "folder.png", false);
    }

    /**
     * Get the location under the build directory to store the file parameter.
     *
     * @param build the build
     * @return the location to store the file parameter
     */
    private File getLocationUnderBuild(AbstractBuild build) {
        return new File(getFileParameterFolderUnderBuild(build), location);
    }

    private File getFileParameterFolderUnderBuild(AbstractBuild<?, ?> build) {
        return new File(build.getRootDir(), FOLDER_NAME);
    }

    /**
     * Default implementation from {@link File}.
     *
     * @deprecated use {@link FileItemImpl2}
     */
    @Deprecated
    public static final class FileItemImpl implements org.apache.commons.fileupload.FileItem {
        private final FileItem delegate;

        public FileItemImpl(File file) {
            if (file == null) {
                throw new NullPointerException("file");
            }
            this.delegate = new FileItemImpl2(file);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).getInputStream();
        }

        @Override
        public String getContentType() {
            return org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).getContentType();
        }

        @Override
        @SuppressFBWarnings(value = "FILE_UPLOAD_FILENAME", justification = "for compatibility")
        public String getName() {
            return org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).getName();
        }

        @Override
        public boolean isInMemory() {
            return org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).isInMemory();
        }

        @Override
        public long getSize() {
            return org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).getSize();
        }

        @Override
        public byte[] get() {
            return org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).get();
        }

        @Override
        public String getString(String encoding) throws UnsupportedEncodingException {
            return org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).getString(encoding);
        }

        @Override
        public String getString() {
            return org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).getString();
        }

        @Override
        public void write(File to) throws Exception {
            org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).write(to);
        }

        @Override
        public void delete() {
            org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).delete();
        }

        @Override
        public String getFieldName() {
            return org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).getFieldName();
        }

        @Override
        public void setFieldName(String name) {
            org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).setFieldName(name);
        }

        @Override
        public boolean isFormField() {
            return org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).isFormField();
        }

        @Override
        public void setFormField(boolean state) {
            org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).setFormField(state);
        }

        @Override
        @Deprecated
        public OutputStream getOutputStream() throws IOException {
            return org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).getOutputStream();
        }

        @Override
        public org.apache.commons.fileupload.FileItemHeaders getHeaders() {
            return org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).getHeaders();
        }

        @Override
        public void setHeaders(org.apache.commons.fileupload.FileItemHeaders headers) {
            org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(delegate).setHeaders(headers);
        }

        @Override
        public FileItem toFileUpload2FileItem() {
            return delegate;
        }
    }

    /**
     * Default implementation from {@link File}.
     */
    public static final class FileItemImpl2 implements FileItem {
        private final File file;

        public FileItemImpl2(File file) {
            if (file == null) {
                throw new NullPointerException("file");
            }
            this.file = file;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(Util.fileToPath(file));
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public String getName() {
            return file.getName();
        }

        @Override
        public boolean isInMemory() {
            return false;
        }

        @Override
        public long getSize() {
            return file.length();
        }

        @Override
        public byte[] get() {
            try {
                return Files.readAllBytes(file.toPath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public String getString(Charset toCharset) throws IOException {
            try {
                return new String(get(), toCharset);
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }

        @Override
        public String getString() {
            return new String(get(), Charset.defaultCharset());
        }

        @Override
        public FileItem write(Path to) throws IOException {
            try {
                new FilePath(file).copyTo(new FilePath(to.toFile()));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return this;
        }

        @Override
        public FileItem delete() throws IOException {
            Files.deleteIfExists(Util.fileToPath(file));
            return this;
        }

        @Override
        public String getFieldName() {
            return null;
        }

        @Override
        public FileItem setFieldName(String name) {
            return this;
        }

        @Override
        public boolean isFormField() {
            return false;
        }

        @Override
        public FileItem setFormField(boolean state) {
            return this;
        }

        @Override
        @Deprecated
        public OutputStream getOutputStream() throws IOException {
            return Files.newOutputStream(Util.fileToPath(file));
        }

        @Override
        public FileItemHeaders getHeaders() {
            return FileItemFactory.AbstractFileItemBuilder.newFileItemHeaders();
        }

        @Override
        public FileItemHeadersProvider setHeaders(FileItemHeaders headers) {
            return this;
        }
    }
}
