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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.FilePath;
import hudson.Util;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import jenkins.model.Jenkins;
import jenkins.security.MasterToAgentCallable;
import jenkins.security.ResourceDomainConfiguration;
import jenkins.security.ResourceDomainRootAction;
import jenkins.util.SystemProperties;
import jenkins.util.VirtualFile;
import org.apache.commons.io.IOUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;

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
    // escape hatch for SECURITY-904 to keep legacy behavior
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    public static boolean ALLOW_SYMLINK_ESCAPE = SystemProperties.getBoolean(DirectoryBrowserSupport.class.getName() + ".allowSymlinkEscape");

    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    public static boolean ALLOW_TMP_DISPLAY = SystemProperties.getBoolean(DirectoryBrowserSupport.class.getName() + ".allowTmpEscape");

    private static final Pattern TMPDIR_PATTERN = Pattern.compile(".+@tmp/.*");

    public final ModelObject owner;

    public final String title;

    private final VirtualFile base;
    private final String icon;
    private final boolean serveDirIndex;
    private String indexFileName = "index.html";

    @Restricted(NoExternalUse.class)
    public static final String CSP_PROPERTY_NAME = DirectoryBrowserSupport.class.getName() + ".CSP";

    /**
     * Keeps track of whether this has been registered from use via {@link ResourceDomainRootAction}.
     */
    private ResourceDomainRootAction.Token resourceToken;

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

    @Override
    public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
        if (!ResourceDomainConfiguration.isResourceRequest(req) && ResourceDomainConfiguration.isResourceDomainConfigured()) {
            resourceToken = ResourceDomainRootAction.get().getToken(this, req);
        }

        try {
            serveFile(req, rsp, base, icon, serveDirIndex);
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
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
    public void serveFile(StaplerRequest req, StaplerResponse rsp, FilePath root, String icon, boolean serveDirIndex) throws IOException, javax.servlet.ServletException, InterruptedException {
        try {
            serveFile(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), root.toVirtualFile(), icon, serveDirIndex);
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void serveFile(StaplerRequest2 req, StaplerResponse2 rsp, VirtualFile root, String icon, boolean serveDirIndex) throws IOException, ServletException, InterruptedException {
        // handle form submission
        String pattern = req.getParameter("pattern");
        if (pattern == null)
            pattern = req.getParameter("path"); // compatibility with Hudson<1.129
        if (pattern != null && Util.isSafeToRedirectTo(pattern)) { // avoid open redirect
            rsp.sendRedirect2(pattern);
            return;
        }

        String path = getPath(req);
        if (path.replace('\\', '/').contains("/../")) {
            // don't serve anything other than files in the artifacts dir
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // split the path to the base directory portion "abc/def/ghi" which doesn't include any wildcard,
        // and the GLOB portion "**/*.xml" (the rest)
        StringBuilder _base = new StringBuilder();
        StringBuilder _rest = new StringBuilder();
        int restSize = -1; // number of ".." needed to go back to the 'base' level.
        boolean zip = false;  // if we are asked to serve a zip file bundle
        boolean plain = false; // if asked to serve a plain text directory listing
        {
            boolean inBase = true;
            StringTokenizer pathTokens = new StringTokenizer(path, "/");
            while (pathTokens.hasMoreTokens()) {
                String pathElement = pathTokens.nextToken();
                // Treat * and ? as wildcard unless they match a literal filename
                if ((pathElement.contains("?") || pathElement.contains("*"))
                        && inBase && !root.child((!_base.isEmpty() ? _base + "/" : "") + pathElement).exists())
                    inBase = false;
                if (pathElement.equals("*zip*")) {
                    // the expected syntax is foo/bar/*zip*/bar.zip
                    // the last 'bar.zip' portion is to causes browses to set a good default file name.
                    // so the 'rest' portion ends here.
                    zip = true;
                    break;
                }
                if (pathElement.equals("*plain*")) {
                    plain = true;
                    break;
                }

                StringBuilder sb = inBase ? _base : _rest;
                if (!sb.isEmpty())   sb.append('/');
                sb.append(pathElement);
                if (!inBase)
                    restSize++;
            }
        }
        restSize = Math.max(restSize, 0);
        String base = _base.toString();
        String rest = _rest.toString();

        // this is the base file/directory
        VirtualFile baseFile;
        if (base.isEmpty()) {
            baseFile = root;
        } else {
            boolean isAbsolute = root.run(new IsAbsolute(base));
            if (isAbsolute) {
                LOGGER.info(() -> "SECURITY-2481 The path provided in the URL (" + base + ") is absolute and thus is refused.");
                rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            baseFile = root.child(base);
        }

        if (baseFile.hasSymlink(getOpenOptions()) || hasTmpDir(baseFile, base, getOpenOptions())) {
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (baseFile.isDirectory()) {
            if (zip) {
                rsp.setContentType("application/zip");
                String includes, prefix;
                if (rest == null || rest.isBlank()) {
                    includes = "**";
                    // JENKINS-19947, JENKINS-61473: traditional behavior is to prepend the directory name
                    prefix = baseFile.getName();
                } else {
                    includes = rest;
                    prefix = "";
                }
                baseFile.zip(rsp.getOutputStream(), includes, null, true, prefix, getOpenOptions());
                return;
            }
            if (plain) {
                rsp.setContentType("text/plain;charset=UTF-8");
                try (OutputStream os = rsp.getOutputStream()) {
                    for (VirtualFile kid : baseFile.list(getOpenOptions())) {
                        os.write(kid.getName().getBytes(StandardCharsets.UTF_8));
                        if (kid.isDirectory()) {
                            os.write('/');
                        }
                        os.write('\n');
                    }
                    os.flush();
                }
                return;
            }

            if (rest.isEmpty()) {
                // if the target page to be displayed is a directory and the path doesn't end with '/', redirect
                StringBuffer reqUrl = req.getRequestURL();
                if (reqUrl.charAt(reqUrl.length() - 1) != '/') {
                    rsp.sendRedirect2(reqUrl.append('/').toString());
                    return;
                }
            }

            List<List<Path>> glob = null;
            boolean patternUsed = !rest.isEmpty();
            boolean containsSymlink = false;
            boolean containsTmpDir = false;
                if (patternUsed) {
                // the rest is Ant glob pattern
                glob = patternScan(baseFile, rest, createBackRef(restSize));
            } else
            if (serveDirIndex) {
                // serve directory index
                var result = baseFile.run(new BuildChildPaths(baseFile, req.getLocale(), getOpenOptions()));
                glob = result.glob;
                containsSymlink = result.containsSymLink;
                containsTmpDir = result.containsTmpDir;
            }

            if (glob != null) {
                // serve glob
                req.setAttribute("it", this);
                List<Path> parentPaths = buildParentPath(base, restSize);
                req.setAttribute("parentPath", parentPaths);
                req.setAttribute("backPath", createBackRef(restSize));
                req.setAttribute("topPath", createBackRef(parentPaths.size() + restSize));
                req.setAttribute("files", glob);
                req.setAttribute("icon", icon);
                req.setAttribute("path", path);
                req.setAttribute("pattern", rest);
                req.setAttribute("dir", baseFile);
                req.setAttribute("showSymlinkWarning", containsSymlink);
                req.setAttribute("showTmpDirWarning", containsTmpDir);
                if (ResourceDomainConfiguration.isResourceRequest(req)) {
                    req.getView(this, "plaindir.jelly").forward(req, rsp);
                } else {
                    req.getView(this, "dir.jelly").forward(req, rsp);
                }
                return;
            }

            // convert a directory service request to a single file service request by serving
            // 'index.html'
            baseFile = baseFile.child(indexFileName);
        }

        //serve a single file
        if (!baseFile.exists()) {
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        boolean view = rest.equals("*view*");

        if (rest.equals("*fingerprint*")) {
            try (InputStream fingerprintInput = baseFile.open()) {
                rsp.forward(Jenkins.get().getFingerprint(Util.getDigestOf(fingerprintInput)), "/", req);
            }
            return;
        }

        URL external = baseFile.toExternalURL();
        if (external != null) {
            // or this URL could be emitted directly from dir.jelly
            // though we would prefer to delay toExternalURL calls unless and until needed
            rsp.sendRedirect2(external.toExternalForm());
            return;
        }

        long lastModified = baseFile.lastModified();
        long length = baseFile.length();

        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("Serving " + baseFile + " with lastModified=" + lastModified + ", length=" + length);

        if (view) {
            InputStream in;
            try {
                in = baseFile.open(getOpenOptions());
            } catch (IOException ioe) {
                rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // for binary files, provide the file name for download
            rsp.setHeader("Content-Disposition", "inline; filename=" + baseFile.getName());

            // pseudo file name to let the Stapler set text/plain
            rsp.serveFile(req, in, lastModified, -1, length, "plain.txt");
        } else {
            if (resourceToken != null) {
                // redirect to second domain
                rsp.sendRedirect(302, ResourceDomainRootAction.get().getRedirectUrl(resourceToken, req.getRestOfPath()));
            } else {
                if (!ResourceDomainConfiguration.isResourceRequest(req)) {
                    // if we're serving this from the main domain, set CSP headers
                    String csp = SystemProperties.getString(CSP_PROPERTY_NAME, DEFAULT_CSP_VALUE);
                    if (!csp.trim().isEmpty()) {
                        // allow users to prevent sending this header by setting empty system property
                        for (String header : new String[]{"Content-Security-Policy", "X-WebKit-CSP", "X-Content-Security-Policy"}) {
                            rsp.setHeader(header, csp);
                        }
                    }
                }
                InputStream in;
                try {
                    in = baseFile.open(getOpenOptions());
                } catch (IOException ioe) {
                    rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                rsp.serveFile(req, in, lastModified, -1, length, baseFile.getName());
            }
        }
    }

    private static final class IsAbsolute extends MasterToAgentCallable<Boolean, IOException> {
        private final String fragment;

        IsAbsolute(String fragment) {
            this.fragment = fragment;
        }

        @Override
        public Boolean call() throws IOException {
            return new File(fragment).isAbsolute();
        }
    }

    private boolean hasTmpDir(VirtualFile baseFile, String base, OpenOption[] openOptions) {
        if (FilePath.isTmpDir(baseFile.getName(), openOptions)) {
            return true;
        }
        return FilePath.isIgnoreTmpDirs(openOptions) && TMPDIR_PATTERN.matcher(base).matches();
    }

    private List<List<Path>> keepReadabilityOnlyOnDescendants(VirtualFile root, boolean patternUsed, List<List<Path>> pathFragmentsList) {
        Stream<List<Path>> pathFragmentsStream = pathFragmentsList.stream().map((List<Path> pathFragments) -> {
            List<Path> mappedFragments = new ArrayList<>(pathFragments.size());
            String relativePath = "";
            for (int i = 0; i < pathFragments.size(); i++) {
                Path current = pathFragments.get(i);
                if (i == 0) {
                    relativePath = current.title;
                } else {
                    relativePath += "/" + current.title;
                }

                if (!current.isReadable) {
                    if (patternUsed) {
                        // we do not want to leak information about existence of folders / files satisfying the pattern inside that folder
                        return null;
                    }
                    mappedFragments.add(current);
                    return mappedFragments;
                } else {
                    if (isDescendant(root, relativePath)) {
                        mappedFragments.add(current);
                    } else {
                        if (patternUsed) {
                            // we do not want to leak information about existence of folders / files satisfying the pattern inside that folder
                            return null;
                        }
                        mappedFragments.add(Path.createNotReadableVersionOf(current));
                        return mappedFragments;
                    }
                }
            }
            return mappedFragments;
        });

        if (patternUsed) {
            pathFragmentsStream = pathFragmentsStream.filter(Objects::nonNull);
        }

        return pathFragmentsStream.collect(Collectors.toList());
    }

    private boolean isDescendant(VirtualFile root, String relativePath) {
        try {
            return ALLOW_SYMLINK_ESCAPE || !root.supportIsDescendant() || root.isDescendant(relativePath);
        }
        catch (IOException e) {
            return false;
        }
    }

    private String getPath(StaplerRequest2 req) {
        String path = req.getRestOfPath();
        if (path.isEmpty())
            path = "/";
        return path;
    }

    /**
     * Builds a list of {@link Path} that represents ancestors
     * from a string like "/foo/bar/zot".
     */
    private List<Path> buildParentPath(String pathList, int restSize) {
        List<Path> r = new ArrayList<>();
        StringTokenizer tokens = new StringTokenizer(pathList, "/");
        int total = tokens.countTokens();
        int current = 1;
        while (tokens.hasMoreTokens()) {
            String token = tokens.nextToken();
            r.add(new Path(createBackRef(total - current + restSize), token, true, 0, true, 0));
            current++;
        }
        return r;
    }

    private static String createBackRef(int times) {
        if (times == 0)    return "./";
        return "../".repeat(times);
    }

    private static void zip(StaplerResponse2 rsp, VirtualFile root, VirtualFile dir, String glob) throws IOException, InterruptedException {
        OutputStream outputStream = rsp.getOutputStream();
        // TODO JENKINS-20663 make encoding overridable via query parameter
        try (ZipOutputStream zos = new ZipOutputStream(outputStream, Charset.defaultCharset())) {
            // TODO consider using run(Callable) here

            if (glob.isEmpty()) {
                if (!root.supportsQuickRecursiveListing()) {
                    // avoid slow listing when the Glob can do a quicker job
                    glob = "**";
                }
            }

            if (glob.isEmpty()) {
                Map<String, VirtualFile> nameToVirtualFiles = collectRecursivelyAllLegalChildren(dir);
                sendZipUsingMap(zos, dir, nameToVirtualFiles);
            } else {
                Collection<String> listOfFile = dir.list(glob, null, /* TODO what is the user expectation? */true);
                sendZipUsingListOfNames(zos, dir, listOfFile);
            }
        }
    }

    private static void sendZipUsingMap(ZipOutputStream zos, VirtualFile dir, Map<String, VirtualFile> nameToVirtualFiles) throws IOException {
        for (Map.Entry<String, VirtualFile> entry : nameToVirtualFiles.entrySet()) {
            String n = entry.getKey();

            // JENKINS-19947: traditional behavior is to prepend the directory name
            String relativePath = dir.getName() + '/' + n;

            VirtualFile f = entry.getValue();
            sendOneZipEntry(zos, f, relativePath);
        }
    }

    private static void sendZipUsingListOfNames(ZipOutputStream zos, VirtualFile dir, Collection<String> listOfFileNames) throws IOException {
        for (String relativePath : listOfFileNames) {
            VirtualFile f = dir.child(relativePath);
            sendOneZipEntry(zos, f, relativePath);
        }
    }

    private static void sendOneZipEntry(ZipOutputStream zos, VirtualFile vf, String relativePath) throws IOException {
        // In ZIP archives "All slashes MUST be forward slashes" (http://pkware.com/documents/casestudies/APPNOTE.TXT)
        // TODO On Linux file names can contain backslashes which should not treated as file separators.
        //      Unfortunately, only the file separator char of the controller is known (File.separatorChar)
        //      but not the file separator char of the (maybe remote) "dir".
        ZipEntry e = new ZipEntry(relativePath.replace('\\', '/'));

        e.setTime(vf.lastModified());
        zos.putNextEntry(e);
        try (InputStream in = vf.open()) {
            IOUtils.copy(in, zos);
        }
        finally {
            zos.closeEntry();
        }
    }

    private static Map<String, VirtualFile> collectRecursivelyAllLegalChildren(VirtualFile dir) throws IOException {
        Map<String, VirtualFile> nameToFiles = new LinkedHashMap<>();
        collectRecursivelyAllLegalChildren(dir, "", nameToFiles);
        return nameToFiles;
    }

    private static void collectRecursivelyAllLegalChildren(VirtualFile currentDir, String currentPrefix, Map<String, VirtualFile> nameToFiles) throws IOException {
        if (currentDir.isFile()) {
            if (currentDir.isDescendant("")) {
                nameToFiles.put(currentPrefix, currentDir);
            }
        } else {
            if (!currentPrefix.isEmpty()) {
                currentPrefix += "/";
            }
            List<VirtualFile> children = currentDir.listOnlyDescendants();
            for (VirtualFile child : children) {
                collectRecursivelyAllLegalChildren(child, currentPrefix + child.getName(), nameToFiles);
            }
        }
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

       /**
        * For a file, the last modified timestamp.
        */
        private final long lastModified;

        /**
         * @deprecated Use {@link #Path(String, String, boolean, long, boolean, long)}
         */
        @Deprecated
        public Path(String href, String title, boolean isFolder, long size, boolean isReadable) {
            this(href, title, isFolder, size, isReadable, 0L);
        }

        public Path(String href, String title, boolean isFolder, long size, boolean isReadable, long lastModified) {
            this.href = href;
            this.title = title;
            this.isFolder = isFolder;
            this.size = size;
            this.isReadable = isReadable;
            this.lastModified = lastModified;
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
                return isFolder ? "folder.svg" : "document.svg";
            else
                return isFolder ? "folder-delete.svg" : "document-delete.svg";
        }

        public String getIconClassName() {
            if (isReadable)
                return isFolder ? "icon-folder" : "icon-document";
            else
                return isFolder ? "icon-folder-delete" : "icon-document-delete";
        }

        public long getSize() {
            return size;
        }

        /**
         *
         * @return A long value representing the time the file was last modified, measured in milliseconds since
         * the epoch (00:00:00 GMT, January 1, 1970), or 0L if is not possible to obtain the times.
         * @since 2.127
         */
        public long getLastModified() {
            return lastModified;
        }

        /**
         *
         * @return A Calendar representing the time the file was last modified, it lastModified is 0L
         * it will return 00:00:00 GMT, January 1, 1970.
         * @since 2.127
         */
        @Restricted(NoExternalUse.class)
        public Calendar getLastModifiedAsCalendar() {
            final Calendar cal = new GregorianCalendar();
            cal.setTimeInMillis(lastModified);
            return cal;
        }

        public static Path createNotReadableVersionOf(Path that) {
            return new Path(that.href, that.title, that.isFolder, that.size, false);
        }

        private static final long serialVersionUID = 1L;
    }



    private static final class FileComparator implements Comparator<VirtualFile> {
        private Collator collator;

        FileComparator(Locale locale) {
            this.collator = Collator.getInstance(locale);
        }

        @Override
        public int compare(VirtualFile lhs, VirtualFile rhs) {
            // directories first, files next
            int r = dirRank(lhs) - dirRank(rhs);
            if (r != 0) return r;
            // otherwise alphabetical
            return this.collator.compare(lhs.getName(), rhs.getName());
        }

        private int dirRank(VirtualFile f) {
            try {
            if (f.isDirectory())     return 0;
            else                    return 1;
            } catch (IOException ex) {
                return 0;
            }
        }
    }

    private static final class BuildChildPathsResult implements Serializable { // TODO Java 21+ record
        private static final long serialVersionUID = 1;
        private final List<List<Path>> glob;
        private final boolean containsSymLink;
        private final boolean containsTmpDir;

        BuildChildPathsResult(List<List<Path>> glob, boolean containsSymLink, boolean containsTmpDir) {
            this.glob = glob;
            this.containsSymLink = containsSymLink;
            this.containsTmpDir = containsTmpDir;
        }
    }

    private static final class BuildChildPaths extends MasterToAgentCallable<BuildChildPathsResult, IOException> {
        private final VirtualFile cur;
        private final Locale locale;
        private final OpenOption[] openOptions;

        BuildChildPaths(VirtualFile cur, Locale locale, OpenOption[] openOptions) {
            this.cur = cur;
            this.locale = locale;
            this.openOptions = openOptions;
        }

        @Override public BuildChildPathsResult call() throws IOException {
            return new BuildChildPathsResult(buildChildPaths(cur, locale), cur.containsSymLinkChild(openOptions), cur.containsTmpDirChild(openOptions));
        }
    }
    /**
     * Builds a list of list of {@link Path}. The inner
     * list of {@link Path} represents one child item to be shown
     * (this mechanism is used to skip empty intermediate directory.)
     */

    @SuppressFBWarnings(value = "SBSC_USE_STRINGBUFFER_CONCATENATION", justification = "no big deal")
    private static List<List<Path>> buildChildPaths(VirtualFile cur, Locale locale) throws IOException {
            List<List<Path>> r = new ArrayList<>();

            VirtualFile[] files = cur.list(getOpenOptions());
                Arrays.sort(files, new FileComparator(locale));

                for (VirtualFile f : files) {
                    Path p = new Path(Util.rawEncode(f.getName()), f.getName(), f.isDirectory(), f.length(), f.canRead(), f.lastModified());
                    if (!f.isDirectory()) {
                        r.add(List.of(p));
                    } else {
                        // find all empty intermediate directory
                        List<Path> l = new ArrayList<>();
                        l.add(p);
                        String relPath = Util.rawEncode(f.getName());
                        while (true) {
                            // files that don't start with '.' qualify for 'meaningful files', nor SCM related files
                            List<VirtualFile> sub = new ArrayList<>();
                            for (VirtualFile vf : f.list(getOpenOptions())) {
                                String name = vf.getName();
                                if (!name.startsWith(".") && !name.equals("CVS") && !name.equals(".svn")) {
                                    sub.add(vf);
                                }
                            }
                            if (sub.size() != 1 || !sub.get(0).isDirectory())
                                break;
                            f = sub.get(0);
                            relPath += '/' + Util.rawEncode(f.getName());
                            l.add(new Path(relPath, f.getName(), true, f.length(), f.canRead(), f.lastModified()));
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
            Collection<String> files = baseDir.list(pattern, null, /* TODO what is the user expectation? */true, getOpenOptions());

            if (!files.isEmpty()) {
                List<List<Path>> r = new ArrayList<>(files.size());
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
            List<Path> pathList = new ArrayList<>();
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

            Path path = new Path(href.toString(), filePath.getName(), filePath.isDirectory(), filePath.length(), filePath.canRead(), filePath.lastModified());
            pathList.add(path);
        }

    private static OpenOption[] getOpenOptions() {
            List<OpenOption> options = new ArrayList<>();
            if (!ALLOW_SYMLINK_ESCAPE) {
                options.add(LinkOption.NOFOLLOW_LINKS);
            }
            if (!ALLOW_TMP_DISPLAY) {
                options.add(FilePath.DisplayOption.IGNORE_TMP_DIRS);
            }
        return options.toArray(new OpenOption[0]);
    }

    private static final Logger LOGGER = Logger.getLogger(DirectoryBrowserSupport.class.getName());

    @Restricted(NoExternalUse.class)
    public static final String DEFAULT_CSP_VALUE = "sandbox allow-same-origin; default-src 'none'; img-src 'self'; style-src 'self';";
}
