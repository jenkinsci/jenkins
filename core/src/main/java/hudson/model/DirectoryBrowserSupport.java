package hudson.model;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.DirectoryScanner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Has convenience methods to serve file system.
 *
 * <p>
 * This object can be used in a mix-in style to provide a directory browsing capability
 * to a {@link ModelObject}. 
 *
 * @author Kohsuke Kawaguchi
 */
public final class DirectoryBrowserSupport {

    public final ModelObject owner;
    
    public final String title;

    /**
     * @deprecated
     *      Use {@link #DirectoryBrowserSupport(ModelObject, String)}
     */
    public DirectoryBrowserSupport(ModelObject owner) {
        this(owner,owner.getDisplayName());
    }

    /**
     * @param owner
     *      The parent model object under which the directory browsing is added.
     * @param title
     *      Used in the HTML caption. 
     */
    public DirectoryBrowserSupport(ModelObject owner, String title) {
        this.owner = owner;
        this.title = title;
    }

    /**
     * Serves a file from the file system (Maps the URL to a directory in a file system.)
     *
     * @param icon
     *      The icon file name, like "folder-open.gif"
     * @param serveDirIndex
     *      True to generate the directory index.
     *      False to serve "index.html"
     */
    public final void serveFile(StaplerRequest req, StaplerResponse rsp, FilePath root, String icon, boolean serveDirIndex) throws IOException, ServletException, InterruptedException {

        String pattern = req.getParameter("pattern");
        if(pattern==null)
            pattern = req.getParameter("path"); // compatibility with Hudson<1.129
        if (pattern != null) {
            servePattern(req, rsp, root, icon, pattern);
            return;
        }

        String path = req.getRestOfPath();

        if(path.length()==0)
            path = "/";

        if(path.indexOf("..")!=-1 || path.length()<1) {
            // don't serve anything other than files in the artifacts dir
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        FilePath f = new FilePath(root,path.substring(1));

        boolean isFingerprint=false;
        if(f.getName().equals("*fingerprint*")) {
            f = f.getParent();
            isFingerprint = true;
        }

        boolean view = false;
        if (f.getName().equals("*view*")) {
            // f.name == ".../ws/.../<filename>/*view*/"
        	// set f to point to the real file
        	f = f.getParent();
        	view = true;
        }

        if(f.getParent().getName().equals("*zip*")) {
            // the expected syntax is foo/bar/*zip*/bar.zip
            // the last 'bar.zip' portion is to causes browses to set a good default file name 
            rsp.setContentType("application/zip");
            f.getParent().getParent().createZipArchive(rsp.getOutputStream());
            return;
        }

        if(f.isDirectory()) {
            if(!req.getRequestURL().toString().endsWith("/")) {
                rsp.sendRedirect2(req.getRequestURL().append('/').toString());
                return;
            }

            if(serveDirIndex) {
                serveFileListing(req, path, f, icon, rsp, f.act(new ChildPathBuilder()), null);
                return;
            } else {
                f = f.child("index.html");
            }
        }

        if(!f.exists()) {
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if(isFingerprint) {
            rsp.forward(Hudson.getInstance().getFingerprint(f.digest()),"/",req);
        } else {
            ContentInfo ci = f.act(new ContentInfo());

            InputStream in = f.read();            
        	if (view) {
        		// for binary files, provide the file name for download
        		rsp.setHeader("Content-Disposition", "inline; filename=" + f.getName());
        		
				// pseudo file name to let the Stapler set text/plain
                rsp.serveFile(req, in, ci.lastModified, -1, ci.contentLength, "plain.txt");
			} else {
        		rsp.serveFile(req, in, ci.lastModified, -1, ci.contentLength, f.getName() );	
        	}

            in.close();
        }
    }

    private void serveFileListing(StaplerRequest req, String path, FilePath dir, String icon, StaplerResponse rsp, List<List<Path>> files, String pattern) throws IOException, InterruptedException, ServletException {
        req.setAttribute("it",this);
        List<Path> parentPaths = buildParentPath(path);
        req.setAttribute("parentPath",parentPaths);
        req.setAttribute("topPath", createBackRef(parentPaths.size()));
        req.setAttribute("files",files);
        req.setAttribute("icon",icon);
        req.setAttribute("path",path);
        req.setAttribute("pattern", pattern);
        req.setAttribute("dir",dir);
        req.getView(this,"dir.jelly").forward(req,rsp);
    }

    /**
     * Serves files matched by the pattern relativ to the current workspace directory.
     */
    private void servePattern(StaplerRequest req, StaplerResponse rsp, FilePath root, String icon, final String pattern) throws IOException, ServletException, InterruptedException {
        String path = req.getRestOfPath();
        if (path.length() > 0) {
            // remove leading slash since the root path ends with a slash
            path = path.substring(1);
        }

        // current workspace directory
        FilePath curDir = new FilePath(root, path);

        if(new FilePath(curDir,pattern).exists()) {
            // this file/directory exists, so it's not a pattern
            rsp.sendRedirect2(pattern);
            return;
        }

        serveFileListing(req,path,root,icon,rsp,curDir.act(new PatternScanner(pattern)),pattern);
    }

    private static final class ContentInfo implements FileCallable<ContentInfo> {
        int contentLength;
        long lastModified;

        public ContentInfo invoke(File f, VirtualChannel channel) throws IOException {
            contentLength = (int) f.length();
            lastModified = f.lastModified();
            return this;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Builds a list of {@link Path} that represents ancestors
     * from a string like "/foo/bar/zot".
     */
    private List<Path> buildParentPath(String pathList) {
        List<Path> r = new ArrayList<Path>();
        StringTokenizer tokens = new StringTokenizer(pathList, "/");
        int total = tokens.countTokens();
        int current=1;
        while(tokens.hasMoreTokens()) {
            String token = tokens.nextToken();
            r.add(new Path(createBackRef(total - current),token,true,0));
            current++;
        }
        return r;
    }

    private static String createBackRef(int times) {
        if(times==0)    return ".";
        StringBuffer buf = new StringBuffer(3*times);
        for(int i=0; i<times; i++ )
            buf.append("../");
        return buf.toString();
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

        public Path(String href, String title, boolean isFolder, long size) {
            this.href = href;
            this.title = title;
            this.isFolder = isFolder;
            this.size = size;
        }

        public boolean isFolder() {
            return isFolder;
        }

        public String getHref() {
            return href;
        }

        public String getTitle() {
            return title;
        }

        public String getIconName() {
            return isFolder?"folder.gif":"text.gif";
        }

        public long getSize() {
            return size;
        }

        private static final long serialVersionUID = 1L;
    }



    private static final class FileComparator implements Comparator<File> {
        public int compare(File lhs, File rhs) {
            // directories first, files next
            int r = dirRank(lhs)-dirRank(rhs);
            if(r!=0) return r;
            // otherwise alphabetical
            return lhs.getName().compareTo(rhs.getName());
        }

        private int dirRank(File f) {
            if(f.isDirectory())     return 0;
            else                    return 1;
        }
    }

    /**
     * Builds a list of list of {@link Path}. The inner
     * list of {@link Path} represents one child item to be shown
     * (this mechanism is used to skip empty intermediate directory.)
     */
    private static final class ChildPathBuilder implements FileCallable<List<List<Path>>> {
        public List<List<Path>> invoke(File cur, VirtualChannel channel) throws IOException {
            List<List<Path>> r = new ArrayList<List<Path>>();

            File[] files = cur.listFiles();
            Arrays.sort(files,new FileComparator());

            for( File f : files ) {
                Path p = new Path(f.getName(),f.getName(),f.isDirectory(),f.length());
                if(!f.isDirectory()) {
                    r.add(Collections.singletonList(p));
                } else {
                    // find all empty intermediate directory
                    List<Path> l = new ArrayList<Path>();
                    l.add(p);
                    String relPath = f.getName();
                    while(true) {
                        // files that don't start with '.' qualify for 'meaningful files', nor SCM related files
                        File[] sub = f.listFiles(new FilenameFilter() {
                            public boolean accept(File dir, String name) {
                                return !name.startsWith(".") && !name.equals("CVS") && !name.equals(".svn");
                            }
                        });
                        if(sub.length!=1 || !sub[0].isDirectory())
                            break;
                        f = sub[0];
                        relPath += '/'+f.getName();
                        l.add(new Path(relPath,f.getName(),true,0));
                    }
                    r.add(l);
                }
            }

            return r;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Runs ant GLOB against the current {@link FilePath} and returns matching
     * paths.
     */
    private static class PatternScanner implements FileCallable<List<List<Path>>> {
        private final String pattern;

        public PatternScanner(String pattern) {
            this.pattern = pattern;
        }

        public List<List<Path>> invoke(File baseDir, VirtualChannel channel) throws IOException {
            FileSet fs = new FileSet();
            fs.setDir(baseDir);
            fs.setIncludes(pattern);

            DirectoryScanner ds = fs.getDirectoryScanner(new org.apache.tools.ant.Project());
            String[] files = ds.getIncludedFiles();

            if (files.length > 0) {
                List<List<Path>> r = new ArrayList<List<Path>>(files.length);
                for (String match : files) {
                    List<Path> file = buildPathList(baseDir, new File(baseDir,match));
                    r.add(file);
                }
                return r;
            }

            return null;
        }

        /**
         * Builds a path list from the current workspace directory down to the specified file path.
         */
        private List<Path> buildPathList(File baseDir, File filePath) throws IOException {
            List<Path> pathList = new ArrayList<Path>();
            StringBuilder href = new StringBuilder();

            buildPathList(baseDir, filePath, pathList, href);
            return pathList;
        }

        /**
         * Builds the path list and href recursively top-down.
         */
        private void buildPathList(File baseDir, File filePath, List<Path> pathList, StringBuilder href) throws IOException {
            File parent = filePath.getParentFile();
            if (!baseDir.equals(parent)) {
                buildPathList(baseDir, parent, pathList, href);
            }

            href.append(filePath.getName());
            if (filePath.isDirectory()) {
                href.append("/");
            }

            Path path = new Path(href.toString(), filePath.getName(), filePath.isDirectory(), filePath.length());
            pathList.add(path);
        }

        private static final long serialVersionUID = 1L;
    }
}
