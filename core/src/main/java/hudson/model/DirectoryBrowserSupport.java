/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt
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

import hudson.FilePath;
import hudson.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import jenkins.util.VirtualFile;
import org.apache.commons.io.IOUtils;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Has convenience methods to serve file system.
 *
 * <p>
 * This object can be used in a mix-in style to provide a directory browsing capability
 * to a {@link ModelObject}. 
 *
 * @author Kohsuke Kawaguchi
 */
public final class DirectoryBrowserSupport implements HttpResponse {

    public final ModelObject owner;
    
    public final String title;

    private final VirtualFile base;
    private final String icon;
    private final boolean serveDirIndex;
    private String indexFileName = "index.html";

    /**
     * @deprecated as of 1.297
     *      Use {@link #DirectoryBrowserSupport(ModelObject, FilePath, String, String, boolean)}
     */
    @Deprecated
    public DirectoryBrowserSupport(ModelObject owner, String title) {
        this(owner, (VirtualFile) null, title, null, false);
    }

    /**
     * @param owner
     *      The parent model object under which the directory browsing is added.
     * @param base
     *      The root of the directory that's bound to URL.
     * @param title
     *      Used in the HTML caption. 
     * @param icon
     *      The icon file name, like "folder.gif"
     * @param serveDirIndex
     *      True to generate the directory index.
     *      False to serve "index.html"
     */
    public DirectoryBrowserSupport(ModelObject owner, FilePath base, String title, String icon, boolean serveDirIndex) {
        this(owner, base.toVirtualFile(), title, icon, serveDirIndex);
    }

    /**
     * @param owner
     *      The parent model object under which the directory browsing is added.
     * @param base
     *      The root of the directory that's bound to URL.
     * @param title
     *      Used in the HTML caption.
     * @param icon
     *      The icon file name, like "folder.gif"
     * @param serveDirIndex
     *      True to generate the directory index.
     *      False to serve "index.html"
     * @since 1.532
     */
    public DirectoryBrowserSupport(ModelObject owner, VirtualFile base, String title, String icon, boolean serveDirIndex) {
        this.owner = owner;
        this.base = base;
        this.title = title;
        this.icon = icon;
        this.serveDirIndex = serveDirIndex;
    }

    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
        try {
            serveFile(req,rsp,base,icon,serveDirIndex);
        } catch (InterruptedException e) {
            throw new IOException("interrupted",e);
        }
    }

    /**
     * If the directory is requested but the directory listing is disabled, a file of this name
     * is served. By default it's "index.html".
     * @since 1.312
     */
    public void setIndexFileName(String fileName) {
        this.indexFileName = fileName;
    }

    /**
     * Serves a file from the file system (Maps the URL to a directory in a file system.)
     *
     * @param icon
     *      The icon file name, like "folder-open.gif"
     * @param serveDirIndex
     *      True to generate the directory index.
     *      False to serve "index.html"
     * @deprecated as of 1.297
     *      Instead of calling this method explicitly, just return the {@link DirectoryBrowserSupport} object
     *      from the {@code doXYZ} method and let Stapler generate a response for you.
     */
    @Deprecated
    public void serveFile(StaplerRequest req, StaplerResponse rsp, FilePath root, String icon, boolean serveDirIndex) throws IOException, ServletException, InterruptedException {
        serveFile(req, rsp, root.toVirtualFile(), icon, serveDirIndex);
    }

    private void serveFile(StaplerRequest req, StaplerResponse rsp, VirtualFile root, String icon, boolean serveDirIndex) throws IOException, ServletException, InterruptedException {
        // handle form submission
        String pattern = req.getParameter("pattern");
        if(pattern==null)
            pattern = req.getParameter("path"); // compatibility with Hudson<1.129
        if(pattern!=null && !Util.isAbsoluteUri(pattern)) {// avoid open redirect
            rsp.sendRedirect2(pattern);
            return;
        }

        String path = getPath(req);
        if(path.replace('\\','/').indexOf("/../")!=-1) {
            // don't serve anything other than files in the artifacts dir
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // split the path to the base directory portion "abc/def/ghi" which doesn't include any wildcard,
        // and the GLOB portion "**/*.xml" (the rest)
        StringBuilder _base = new StringBuilder();
        StringBuilder _rest = new StringBuilder();
        int restSize=-1; // number of ".." needed to go back to the 'base' level.
        boolean zip=false;  // if we are asked to serve a zip file bundle
        boolean plain = false; // if asked to serve a plain text directory listing
        {
            boolean inBase = true;
            StringTokenizer pathTokens = new StringTokenizer(path,"/");
            while(pathTokens.hasMoreTokens()) {
                String pathElement = pathTokens.nextToken();
                // Treat * and ? as wildcard unless they match a literal filename
                if((pathElement.contains("?") || pathElement.contains("*"))
                        && inBase && !root.child((_base.length() > 0 ? _base + "/" : "") + pathElement).exists())
                    inBase = false;
                if(pathElement.equals("*zip*")) {
                    // the expected syntax is foo/bar/*zip*/bar.zip
                    // the last 'bar.zip' portion is to causes browses to set a good default file name.
                    // so the 'rest' portion ends here.
                    zip=true;
                    break;
                }
                if(pathElement.equals("*plain*")) {
                    plain = true;
                    break;
                }

                StringBuilder sb = inBase?_base:_rest;
                if(sb.length()>0)   sb.append('/');
                sb.append(pathElement);
                if(!inBase)
                    restSize++;
            }
        }
        restSize = Math.max(restSize,0);
        String base = _base.toString();
        String rest = _rest.toString();

        // this is the base file/directory
        VirtualFile baseFile = root.child(base);

        if(baseFile.isDirectory()) {
            if(zip) {
                rsp.setContentType("application/zip");
                zip(rsp.getOutputStream(), baseFile, rest);
                return;
            }
            if (plain) {
                rsp.setContentType("text/plain;charset=UTF-8");
                OutputStream os = rsp.getOutputStream();
                try {
                    for (VirtualFile kid : baseFile.list()) {
                        os.write(kid.getName().getBytes("UTF-8"));
                        if (kid.isDirectory()) {
                            os.write('/');
                        }
                        os.write('\n');
                    }
                    os.flush();
                } finally {
                    os.close();
                }
                return;
            }

            if(rest.length()==0) {
                // if the target page to be displayed is a directory and the path doesn't end with '/', redirect
                StringBuffer reqUrl = req.getRequestURL();
                if(reqUrl.charAt(reqUrl.length()-1)!='/') {
                    rsp.sendRedirect2(reqUrl.append('/').toString());
                    return;
                }
            }

            List<List<Path>> glob = null;

            if(rest.length()>0) {
                // the rest is Ant glob pattern
                glob = patternScan(baseFile, rest, createBackRef(restSize));
            } else
            if(serveDirIndex) {
                // serve directory index
                glob = baseFile.run(new BuildChildPaths(baseFile, req.getLocale()));
            }

            if(glob!=null) {
                // serve glob
                req.setAttribute("it", this);
                List<Path> parentPaths = buildParentPath(base,restSize);
                req.setAttribute("parentPath",parentPaths);
                req.setAttribute("backPath", createBackRef(restSize));
                req.setAttribute("topPath", createBackRef(parentPaths.size()+restSize));
                req.setAttribute("files", glob);
                req.setAttribute("icon", icon);
                req.setAttribute("path", path);
                req.setAttribute("pattern",rest);
                req.setAttribute("dir", baseFile);
                req.getView(this,"dir.jelly").forward(req, rsp);
                return;
            }

            // convert a directory service request to a single file service request by serving
            // 'index.html'
            baseFile = baseFile.child(indexFileName);
        }

        //serve a single file
        if(!baseFile.exists()) {
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        boolean view = rest.equals("*view*");

        if(rest.equals("*fingerprint*")) {
            InputStream fingerprintInput = baseFile.open();
            try {
                rsp.forward(Jenkins.getInstance().getFingerprint(Util.getDigestOf(fingerprintInput)), "/", req);
            } finally {
                fingerprintInput.close();
            }
            return;
        }

        long lastModified = baseFile.lastModified();
        long length = baseFile.length();

        if(LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("Serving "+baseFile+" with lastModified=" + lastModified + ", length=" + length);

        InputStream in = baseFile.open();
        if (view) {
            // for binary files, provide the file name for download
            rsp.setHeader("Content-Disposition", "inline; filename=" + baseFile.getName());

            // pseudo file name to let the Stapler set text/plain
            rsp.serveFile(req, in, lastModified, -1, length, "plain.txt");
        } else {
            rsp.serveFile(req, in, lastModified, -1, length, baseFile.getName() );
        }
    }

    private String getPath(StaplerRequest req) {
        String path = req.getRestOfPath();
        if(path.length()==0)
            path = "/";
        return path;
    }

    /**
     * Builds a list of {@link Path} that represents ancestors
     * from a string like "/foo/bar/zot".
     */
    private List<Path> buildParentPath(String pathList, int restSize) {
        List<Path> r = new ArrayList<Path>();
        StringTokenizer tokens = new StringTokenizer(pathList, "/");
        int total = tokens.countTokens();
        int current=1;
        while(tokens.hasMoreTokens()) {
            String token = tokens.nextToken();
            r.add(new Path(createBackRef(total-current+restSize),token,true,0, true));
            current++;
        }
        return r;
    }

    private static String createBackRef(int times) {
        if(times==0)    return "./";
        StringBuilder buf = new StringBuilder(3*times);
        for(int i=0; i<times; i++ )
            buf.append("../");
        return buf.toString();
    }

    private static void zip(OutputStream outputStream, VirtualFile dir, String glob) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(outputStream);
        zos.setEncoding(System.getProperty("file.encoding")); // TODO JENKINS-20663 make this overridable via query parameter
        for (String n : dir.list(glob.length() == 0 ? "**" : glob)) {
            String relativePath;
            if (glob.length() == 0) {
                // JENKINS-19947: traditional behavior is to prepend the directory name
                relativePath = dir.getName() + '/' + n;
            } else {
                relativePath = n;
            }
            // In ZIP archives "All slashes MUST be forward slashes" (http://pkware.com/documents/casestudies/APPNOTE.TXT)
            // TODO On Linux file names can contain backslashes which should not treated as file separators.
            //      Unfortunately, only the file separator char of the master is known (File.separatorChar)
            //      but not the file separator char of the (maybe remote) "dir".
            ZipEntry e = new ZipEntry(relativePath.replace('\\', '/'));
            VirtualFile f = dir.child(n);
            e.setTime(f.lastModified());
            zos.putNextEntry(e);
            InputStream in = f.open();
            try {
                Util.copyStream(in, zos);
            } finally {
                IOUtils.closeQuietly(in);
            }
            zos.closeEntry();
        }
        zos.close();
    }

    /**
     * Represents information about one file or folder.
     */
    public static final class Path implements Serializable {
        /**
         * Relative URL to this path from the current page.
         */
        private final String href;
        /**
         * Name of this path. Just the file name portion.
         */
        private final String title;

        private final boolean isFolder;

        /**
         * File size, or null if this is not a file.
         */
        private final long size;
        
        /**
         * If the current user can read the file.
         */
        private final boolean isReadable;

        public Path(String href, String title, boolean isFolder, long size, boolean isReadable) {
            this.href = href;
            this.title = title;
            this.isFolder = isFolder;
            this.size = size;
            this.isReadable = isReadable;
        }

        public boolean isFolder() {
            return isFolder;
        }
        
        public boolean isReadable() {
            return isReadable;
        }

        public String getHref() {
            return href;
        }

        public String getTitle() {
            return title;
        }

        public String getIconName() {
            if (isReadable)
                return isFolder?"folder.png":"text.png";
            else
                return isFolder?"folder-error.png":"text-error.png";
        }

        public String getIconClassName() {
            if (isReadable)
                return isFolder?"icon-folder":"icon-text";
            else
                return isFolder?"icon-folder-error":"icon-text-error";
        }

        public long getSize() {
            return size;
        }

        private static final long serialVersionUID = 1L;
    }



    private static final class FileComparator implements Comparator<VirtualFile> {
        private Collator collator;

        FileComparator(Locale locale) {
            this.collator = Collator.getInstance(locale);
        }

        public int compare(VirtualFile lhs, VirtualFile rhs) {
            // directories first, files next
            int r = dirRank(lhs)-dirRank(rhs);
            if(r!=0) return r;
            // otherwise alphabetical
            return this.collator.compare(lhs.getName(), rhs.getName());
        }

        private int dirRank(VirtualFile f) {
            try {
            if(f.isDirectory())     return 0;
            else                    return 1;
            } catch (IOException ex) {
                return 0;
            }
        }
    }

    private static final class BuildChildPaths extends MasterToSlaveCallable<List<List<Path>>,IOException> {
        private final VirtualFile cur;
        private final Locale locale;
        BuildChildPaths(VirtualFile cur, Locale locale) {
            this.cur = cur;
            this.locale = locale;
        }
        @Override public List<List<Path>> call() throws IOException {
            return buildChildPaths(cur, locale);
        }
    }
    /**
     * Builds a list of list of {@link Path}. The inner
     * list of {@link Path} represents one child item to be shown
     * (this mechanism is used to skip empty intermediate directory.)
     */
    private static List<List<Path>> buildChildPaths(VirtualFile cur, Locale locale) throws IOException {
            List<List<Path>> r = new ArrayList<List<Path>>();

            VirtualFile[] files = cur.list();
                Arrays.sort(files,new FileComparator(locale));
    
                for( VirtualFile f : files ) {
                    Path p = new Path(Util.rawEncode(f.getName()), f.getName(), f.isDirectory(), f.length(), f.canRead());
                    if(!f.isDirectory()) {
                        r.add(Collections.singletonList(p));
                    } else {
                        // find all empty intermediate directory
                        List<Path> l = new ArrayList<Path>();
                        l.add(p);
                        String relPath = Util.rawEncode(f.getName());
                        while(true) {
                            // files that don't start with '.' qualify for 'meaningful files', nor SCM related files
                            List<VirtualFile> sub = new ArrayList<VirtualFile>();
                            for (VirtualFile vf : f.list()) {
                                String name = vf.getName();
                                if (!name.startsWith(".") && !name.equals("CVS") && !name.equals(".svn")) {
                                    sub.add(vf);
                                }
                            }
                            if (sub.size() !=1 || !sub.get(0).isDirectory())
                                break;
                            f = sub.get(0);
                            relPath += '/'+Util.rawEncode(f.getName());
                            l.add(new Path(relPath,f.getName(),true,0, f.canRead()));
                        }
                        r.add(l);
                    }
                }

            return r;
    }

    /**
     * Runs ant GLOB against the current {@link FilePath} and returns matching
     * paths.
     * @param baseRef String like "../../../" that cancels the 'rest' portion. Can be "./"
     */
    private static List<List<Path>> patternScan(VirtualFile baseDir, String pattern, String baseRef) throws IOException {
            String[] files = baseDir.list(pattern);

            if (files.length > 0) {
                List<List<Path>> r = new ArrayList<List<Path>>(files.length);
                for (String match : files) {
                    List<Path> file = buildPathList(baseDir, baseDir.child(match), baseRef);
                    r.add(file);
                }
                return r;
            }

            return null;
        }

        /**
         * Builds a path list from the current workspace directory down to the specified file path.
         */
        private static List<Path> buildPathList(VirtualFile baseDir, VirtualFile filePath, String baseRef) throws IOException {
            List<Path> pathList = new ArrayList<Path>();
            StringBuilder href = new StringBuilder(baseRef);

            buildPathList(baseDir, filePath, pathList, href);
            return pathList;
        }

        /**
         * Builds the path list and href recursively top-down.
         */
        private static void buildPathList(VirtualFile baseDir, VirtualFile filePath, List<Path> pathList, StringBuilder href) throws IOException {
            VirtualFile parent = filePath.getParent();
            if (!baseDir.equals(parent)) {
                buildPathList(baseDir, parent, pathList, href);
            }

            href.append(Util.rawEncode(filePath.getName()));
            if (filePath.isDirectory()) {
                href.append("/");
            }

            Path path = new Path(href.toString(), filePath.getName(), filePath.isDirectory(), filePath.length(), filePath.canRead());
            pathList.add(path);
        }


    private static final Logger LOGGER = Logger.getLogger(DirectoryBrowserSupport.class.getName());
}
