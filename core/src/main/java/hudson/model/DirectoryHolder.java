package hudson.model;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Has convenience methods to serve file system.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class DirectoryHolder extends Actionable {

    /**
     * Serves a file from the file system (Maps the URL to a directory in a file system.)
     *
     * @param icon
     *      The icon file name, like "folder-open.gif"
     * @param serveDirIndex
     *      True to generate the directory index.
     *      False to serve "index.html"
     */
    protected final void serveFile(StaplerRequest req, StaplerResponse rsp, File root, String icon, boolean serveDirIndex) throws IOException, ServletException {
        if(req.getQueryString()!=null) {
            req.setCharacterEncoding("UTF-8");
            String path = req.getParameter("path");
            if(path!=null) {
                rsp.sendRedirect(URLEncoder.encode(path,"UTF-8"));
                return;
            }
        }

        String path = req.getRestOfPath();

        if(path.length()==0)
            path = "/";

        if(path.indexOf("..")!=-1 || path.length()<1) {
            // don't serve anything other than files in the artifacts dir
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        File f = new File(root,path.substring(1));

        boolean isFingerprint=false;
        if(f.getName().equals("*fingerprint*")) {
            f = f.getParentFile();
            isFingerprint = true;
        }

        if(!f.exists()) {
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if(f.isDirectory()) {
            if(!req.getRequestURL().toString().endsWith("/")) {
                rsp.sendRedirect2(req.getRequestURL().append('/').toString());
                return;
            }

            if(serveDirIndex) {
                req.setAttribute("it",this);
                List<Path> parentPaths = buildParentPath(path);
                req.setAttribute("parentPath",parentPaths);
                req.setAttribute("topPath",
                    parentPaths.isEmpty() ? "." : repeat("../",parentPaths.size()));
                req.setAttribute("files",buildChildPathList(f));
                req.setAttribute("icon",icon);
                req.setAttribute("path",path);
                req.getView(this,"dir.jelly").forward(req,rsp);
                return;
            } else {
                f = new File(f,"index.html");
            }
        }


        if(isFingerprint) {
            FileInputStream in = new FileInputStream(f);
            try {
                Hudson hudson = Hudson.getInstance();
                rsp.forward(hudson.getFingerprint(hudson.getDigestOf(in)),"/",req);
            } finally {
                in.close();
            }
        } else {
            rsp.serveFile(req,f.toURL());
        }
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
            r.add(new Path(repeat("../",total-current),token,true,0));
            current++;
        }
        return r;
    }

    /**
     * Builds a list of list of {@link Path}. The inner
     * list of {@link Path} represents one child item to be shown
     * (this mechanism is used to skip empty intermediate directory.)
     */
    private List<List<Path>> buildChildPathList(File cur) {
        List<List<Path>> r = new ArrayList<List<Path>>();

        File[] files = cur.listFiles();
        Arrays.sort(files,FILE_SORTER);

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

    private static String repeat(String s,int times) {
        StringBuffer buf = new StringBuffer(s.length()*times);
        for(int i=0; i<times; i++ )
            buf.append(s);
        return buf.toString();
    }

    /**
     * Represents information about one file or folder.
     */
    public final class Path {
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
    }



    private static final Comparator<File> FILE_SORTER = new Comparator<File>() {
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
    };
}
