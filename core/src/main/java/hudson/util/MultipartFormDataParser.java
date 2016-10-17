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

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;

import javax.annotation.CheckForNull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Wraps commons file-upload and handles a "multipart/form-data" form submisison
 * (that often includes file upload.)
 *
 * @author Kohsuke Kawaguchi
 */
public class MultipartFormDataParser {
    private final ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());
    private final Map<String,FileItem> byName = new HashMap<String,FileItem>();

    public MultipartFormDataParser(HttpServletRequest request) throws ServletException {
        try {
            for( FileItem fi : (List<FileItem>)upload.parseRequest(request) )
                byName.put(fi.getFieldName(),fi);
        } catch (FileUploadException e) {
            throw new ServletException(e);
        }
    }

    public String get(String key) {
        FileItem fi = byName.get(key);
        if(fi==null)    return null;
        return fi.getString();
    }

    public FileItem getFileItem(String key) {
        return byName.get(key);
    }

    /**
     * If any file is created on the disk, delete them all.
     * Even if this method is not called, the resource will be still cleaned up later by GC.
     */
    public void cleanUp() {
        for (FileItem item : byName.values())
            item.delete();
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

        String[] parts = contentType.split(";");
        if (parts.length == 0) {
            return false;
        }

        for (int i = 0; i < parts.length; i++) {
            if ("multipart/form-data".equals(parts[i])) {
                return true;
            }
        }

        return false;
    }
}
