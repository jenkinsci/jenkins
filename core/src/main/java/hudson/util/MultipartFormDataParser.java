package hudson.util;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;

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
}
