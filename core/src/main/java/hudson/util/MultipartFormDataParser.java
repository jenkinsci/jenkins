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

package hudson.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import io.jenkins.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.fileupload2.core.FileUploadByteCountLimitException;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.core.FileUploadFileCountLimitException;
import org.apache.commons.fileupload2.core.FileUploadSizeException;
import org.apache.commons.fileupload2.jakarta.servlet5.JakartaServletDiskFileUpload;
import org.apache.commons.fileupload2.jakarta.servlet5.JakartaServletFileUpload;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Wraps commons file-upload and handles a "multipart/form-data" form submission
 * (that often includes file upload.)
 *
 * @author Kohsuke Kawaguchi
 */
public class MultipartFormDataParser implements AutoCloseable {
    private final Map<String, FileItem> byName = new HashMap<>();

    /**
     * Limits the number of form fields that can be processed in one multipart/form-data request.
     * Used to set {@link org.apache.commons.fileupload2.jakarta.JakartaServletFileUpload#setFileCountMax(long)}.
     * Despite the name, this applies to all form fields, not just actual file attachments.
     * Set to {@code -1} to disable limits.
     */
    private static /* nonfinal for Jenkins script console */ int FILEUPLOAD_MAX_FILES = Integer.getInteger(MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_FILES", 1000);

    /**
     * Limits the size (in bytes) of individual fields that can be processed in one multipart/form-data request.
     * Used to set {@link org.apache.commons.fileupload2.jakarta.JakartaServletFileUpload#setFileSizeMax(long)}.
     * Despite the name, this applies to all form fields, not just actual file attachments.
     * Set to {@code -1} to disable limits.
     */
    private static /* nonfinal for Jenkins script console */ long FILEUPLOAD_MAX_FILE_SIZE = Long.getLong(MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_FILE_SIZE", -1);

    /**
     * Limits the total request size (in bytes) that can be processed in one multipart/form-data request.
     * Used to set {@link org.apache.commons.fileupload2.jakarta.JakartaServletFileUpload#setSizeMax(long)}.
     * Set to {@code -1} to disable limits.
     */
    private static /* nonfinal for Jenkins script console */ long FILEUPLOAD_MAX_SIZE = Long.getLong(MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_SIZE", -1);

    @Restricted(NoExternalUse.class)
    public MultipartFormDataParser(HttpServletRequest request, int maxParts, long maxPartSize, long maxSize) throws ServletException {
        File tmpDir;
        try {
            tmpDir = Files.createTempDirectory("jenkins-multipart-uploads").toFile();
        } catch (IOException e) {
            throw new ServletException("Error creating temporary directory", e);
        }
        tmpDir.deleteOnExit();
        JakartaServletFileUpload<DiskFileItem, DiskFileItemFactory> upload = new JakartaServletDiskFileUpload(DiskFileItemFactory.builder().setFile(tmpDir).get());
        upload.setMaxFileCount(maxParts);
        upload.setMaxFileSize(maxPartSize);
        upload.setMaxSize(maxSize);
        try {
            for (FileItem fi : upload.parseRequest(request))
                byName.put(fi.getFieldName(), fi);
        } catch (FileUploadFileCountLimitException e) {
            throw new ServletException("File upload field count limit exceeded. Consider setting the Java system property "
                    + MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_FILES to a value greater than " + FILEUPLOAD_MAX_FILES + ", or to -1 to disable this limit.", e);
        } catch (FileUploadByteCountLimitException e) {
            throw new ServletException("File upload field size limit exceeded. Consider setting the Java system property "
                    + MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_FILE_SIZE to a value greater than " + FILEUPLOAD_MAX_FILE_SIZE + ", or to -1 to disable this limit.", e);
        } catch (FileUploadSizeException e) {
            throw new ServletException("File upload total size limit exceeded. Consider setting the Java system property "
                    + MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_SIZE to a value greater than " + FILEUPLOAD_MAX_SIZE + ", or to -1 to disable this limit.", e);
        } catch (FileUploadException e) {
            throw new ServletException(e);
        }
    }

    @Restricted(NoExternalUse.class)
    public MultipartFormDataParser(HttpServletRequest request, int maxParts) throws ServletException {
        this(request, maxParts, FILEUPLOAD_MAX_FILE_SIZE, FILEUPLOAD_MAX_SIZE);
    }

    public MultipartFormDataParser(HttpServletRequest request) throws ServletException {
        this(request, FILEUPLOAD_MAX_FILES, FILEUPLOAD_MAX_FILE_SIZE, FILEUPLOAD_MAX_SIZE);
    }

    /**
     * @deprecated use {@link #MultipartFormDataParser(HttpServletRequest)}
     */
    @Deprecated
    public MultipartFormDataParser(javax.servlet.http.HttpServletRequest request) throws javax.servlet.ServletException {
        File tmpDir;
        try {
            tmpDir = Files.createTempDirectory("jenkins-multipart-uploads").toFile();
        } catch (IOException e) {
            throw new javax.servlet.ServletException("Error creating temporary directory", e);
        }
        tmpDir.deleteOnExit();
        JakartaServletFileUpload<DiskFileItem, DiskFileItemFactory> upload = new JakartaServletDiskFileUpload(DiskFileItemFactory.builder().setFile(tmpDir).get());
        upload.setMaxFileCount(FILEUPLOAD_MAX_FILES);
        upload.setMaxFileSize(FILEUPLOAD_MAX_FILE_SIZE);
        upload.setMaxSize(FILEUPLOAD_MAX_SIZE);
        try {
            for (FileItem fi : upload.parseRequest(HttpServletRequestWrapper.toJakartaHttpServletRequest(request)))
                byName.put(fi.getFieldName(), fi);
        } catch (FileUploadFileCountLimitException e) {
            throw new javax.servlet.ServletException("File upload field count limit exceeded. Consider setting the Java system property "
                    + MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_FILES to a value greater than " + FILEUPLOAD_MAX_FILES + ", or to -1 to disable this limit.", e);
        } catch (FileUploadByteCountLimitException e) {
            throw new javax.servlet.ServletException("File upload field size limit exceeded. Consider setting the Java system property "
                    + MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_FILE_SIZE to a value greater than " + FILEUPLOAD_MAX_FILE_SIZE + ", or to -1 to disable this limit.", e);
        } catch (FileUploadSizeException e) {
            throw new javax.servlet.ServletException("File upload total size limit exceeded. Consider setting the Java system property "
                    + MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_SIZE to a value greater than " + FILEUPLOAD_MAX_SIZE + ", or to -1 to disable this limit.", e);
        } catch (FileUploadException e) {
            throw new javax.servlet.ServletException(e);
        }
    }

    public String get(String key) {
        FileItem fi = byName.get(key);
        if (fi == null)    return null;
        try {
            return fi.getString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public FileItem getFileItem2(String key) {
        return byName.get(key);
    }

    /**
     * @deprecated use {@link #getFileItem2(String)}
     */
    @Deprecated
    public org.apache.commons.fileupload.FileItem getFileItem(String key) {
        return org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(getFileItem2(key));
    }

    /**
     * If any file is created on the disk, delete them all.
     * Even if this method is not called, the resource will be still cleaned up later by GC.
     */
    public void cleanUp() {
        for (FileItem item : byName.values()) {
            try {
                item.delete();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Alias for {@link #cleanUp}. */
    @Override
    public void close() {
        cleanUp();
    }

    /**
     * Checks a Content-Type string to assert if it is "multipart/form-data".
     *
     * @param contentType Content-Type string.
     * @return {@code true} if the content type is "multipart/form-data", otherwise {@code false}.
     * @since 1.620
     */
    public static boolean isMultiPartForm(@CheckForNull String contentType) {
        if (contentType == null) {
            return false;
        }

        for (String part : contentType.split(";")) {
            if ("multipart/form-data".equals(part)) {
                return true;
            }
        }
        return false;
    }
}
