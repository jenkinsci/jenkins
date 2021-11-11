package jenkins.security;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.jvnet.hudson.test.LoggerRule.recorded;

import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Functions;
import hudson.Util;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import jenkins.SlaveToMasterFileCallable;
import jenkins.SoloFilePathFilter;
import jenkins.agents.AgentComputerUtil;
import jenkins.security.s2m.AdminWhitelistRule;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.recipes.LocalData;

@SuppressWarnings("ThrowableNotThrown")
@Issue("SECURITY-2455")
public class Security2455Test {

    // TODO After merge, reference the class directly
    private static final String SECURITY_2428_KILLSWITCH = "jenkins.security.s2m.RunningBuildFilePathFilter.FAIL";

    @Rule
    public final FlagRule<String> flagRule = FlagRule.systemProperty(SECURITY_2428_KILLSWITCH, "false");

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule().record(SoloFilePathFilter.class, Level.WARNING);

    @Before
    public void setup() {
        ExtensionList.lookupSingleton(AdminWhitelistRule.class).setMasterKillSwitch(false);
    }

    // --------

    @Test
    @Issue("SECURITY-2427")
    public void mkdirsParentsTest() {
        final File buildStuff = new File(j.jenkins.getRootDir(), "job/nonexistent/builds/1/foo/bar");
        logging.capture(10);
        SecurityException ex = assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new MkdirsParentsCallable(buildStuff)));
        assertThat(logging, recorded(containsString("foo/bar")));
        assertThat(ex.getMessage(), not(containsString("foo/bar"))); // test error redaction

        SoloFilePathFilter.REDACT_ERRORS = false;
        try {
            SecurityException ex2 = assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new MkdirsParentsCallable(buildStuff)));
            assertThat(ex2.getMessage(), containsString("foo/bar")); // test error redaction
        } finally {
            SoloFilePathFilter.REDACT_ERRORS = true;
        }
    }
    private static class MkdirsParentsCallable extends MasterToSlaveCallable<String, Exception> {
        private final File file;

        private MkdirsParentsCallable(File file) {
            this.file = file;
        }

        @Override
        public String call() throws Exception {
            toFilePathOnController(this.file).mkdirs();
            return null;
        }
    }

    // --------

    @Test
    @Issue("SECURITY-2444")
    public void testNonCanonicalPath() throws Exception {
        assumeFalse(Functions.isWindows());
        final FreeStyleBuild build = j.createFreeStyleProject().scheduleBuild2(0, new Cause.UserIdCause()).waitForStart();
        j.waitForCompletion(build);
        final File link = new File(build.getRootDir(), "link");
        final File secrets = new File(j.jenkins.getRootDir(), "secrets/master.key");
        Files.createSymbolicLink(link.toPath(), secrets.toPath());
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new ReadToStringCallable(link)));
    }
    @Test
    @Issue("SECURITY-2444")
    public void testNonCanonicalPathOnController() throws Exception {
        assumeFalse(Functions.isWindows());
        final FreeStyleBuild build = j.createFreeStyleProject().scheduleBuild2(0, new Cause.UserIdCause()).waitForStart();
        j.waitForCompletion(build);
        final File link = new File(build.getRootDir(), "link");
        final File secrets = new File(j.jenkins.getRootDir(), "secrets/master.key");
        Files.createSymbolicLink(link.toPath(), secrets.toPath());
        String result = FilePath.localChannel.call(new ReadToStringCallable(link));
        assertEquals(IOUtils.readLines(new FileReader(secrets)).get(0), result);
    }

    private static class ReadToStringCallable extends MasterToSlaveCallable<String, Exception> {

        final String abs;

        ReadToStringCallable(File link) {
            abs = link.getPath();
        }

        @Override
        public String call() throws IOException {
            FilePath p = toFilePathOnController(new File(abs));
            try {
                return p.readToString();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
    }

    // --------

    @Test
    @Issue({"SECURITY-2446", "SECURITY-2531"})
    // $ tar tvf symlink.tar
    // lrwxr-xr-x  0 501    20          0 Oct  5 09:50 foo -> ../../../../secrets
    @LocalData
    public void testUntaringSymlinksFails() throws Exception {
        final FreeStyleBuild freeStyleBuild = j.buildAndAssertSuccess(j.createFreeStyleProject());
        final File symlinkTarFile = new File(j.jenkins.getRootDir(), "symlink.tar");
        final File untarTargetFile = new File(freeStyleBuild.getRootDir(), "foo");
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new UntarFileCallable(symlinkTarFile, untarTargetFile)));
    }
    private static final class UntarFileCallable extends MasterToSlaveCallable<Integer, Exception> {
        private final File source;
        private final File destination;

        private UntarFileCallable(File source, File destination) {
            this.source = source;
            this.destination = destination;
        }

        @Override
        public Integer call() throws Exception {
            final FilePath sourceFilePath = new FilePath(source);
            final FilePath destinationFilePath = toFilePathOnController(destination);
            sourceFilePath.untar(destinationFilePath, FilePath.TarCompression.NONE);
            return 1;
        }
    }

    // --------

    @Test
    @Issue("SECURITY-2453")
    public void testTarSymlinksThatAreSafe() throws Exception {
        assumeFalse(Functions.isWindows());
        final File buildDir = j.buildAndAssertSuccess(j.createFreeStyleProject()).getRootDir();
        // We cannot touch the build dir itself
        final File innerDir = new File(buildDir, "dir");
        final File innerDir2 = new File(buildDir, "dir2");
        assertTrue(innerDir.mkdirs());
        assertTrue(innerDir2.mkdirs());
        assertTrue(new File(innerDir2, "the-file").createNewFile());
        Util.createSymlink(innerDir, "../dir2", "link", TaskListener.NULL);
        assertTrue(new File(innerDir, "link/the-file").exists());
        final int files = invokeOnAgent(new TarCaller(innerDir));
        assertEquals(1, files);
    }
    @Test
    @Issue("SECURITY-2453")
    public void testTarSymlinksOutsideAllowedDirs() throws Exception {
        assumeFalse(Functions.isWindows());
        final File buildDir = j.buildAndAssertSuccess(j.createFreeStyleProject()).getRootDir();
        // We cannot touch the build dir itself
        final File innerDir = new File(buildDir, "dir");
        assertTrue(innerDir.mkdirs());
        Util.createSymlink(innerDir, "../../../../../secrets", "secrets-link", TaskListener.NULL);
        assertTrue(new File(innerDir, "secrets-link/master.key").exists());
        logging.capture(10);
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new TarCaller(innerDir)));
        assertThat(logging, recorded(containsString("filepath-filters.d")));
    }

    private static class TarCaller extends MasterToSlaveCallable<Integer, Exception> {
        private final File root;

        private TarCaller(File root) {
            this.root = root;
        }

        @Override
        public Integer call() throws Exception {
            return toFilePathOnController(root).tar(NullOutputStream.NULL_OUTPUT_STREAM, "**");
        }
    }

    // --------

    @Test
    @Issue("SECURITY-2484")
    public void zipTest() {
        final File secrets = new File(j.jenkins.getRootDir(), "secrets");
        assertTrue(secrets.exists());
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new ZipTestCallable(secrets)));
    }
    @Test
    @Issue("SECURITY-2484")
    public void zipTestController() throws Exception {
        final File secrets = new File(j.jenkins.getRootDir(), "secrets");
        assertTrue(secrets.exists());
        FilePath.localChannel.call(new ZipTestCallable(secrets));
    }

    private static class ZipTestCallable extends MasterToSlaveCallable<String, Exception> {
        private final File file;

        private ZipTestCallable(File file) {
            this.file = file;
        }

        @Override
        public String call() throws Exception {
            final File tmp = File.createTempFile("security2455_", ".zip");
            tmp.deleteOnExit();
            toFilePathOnController(file).zip(new FilePath(tmp));
            return tmp.getName();
        }
    }

    // --------

    @Test
    @Issue("SECURITY-2485")
    @LocalData
    public void unzipRemoteTest() {
        final File targetDir = j.jenkins.getRootDir();
        final File source = new File(targetDir, "file.zip"); // in this test, controller and agent are on same FS so this works -- file needs to exist but content should not be read
        assertTrue(targetDir.exists());
        final List<File> filesBefore = Arrays.asList(Objects.requireNonNull(targetDir.listFiles()));
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new UnzipRemoteTestCallable(targetDir, source)));
        final List<File> filesAfter = Arrays.asList(Objects.requireNonNull(targetDir.listFiles()));
        // We cannot do a direct comparison here because `logs/` appears during execution
        assertEquals(filesBefore.size(), filesAfter.stream().filter(it -> !it.getName().equals("logs")).count());
    }

    private static class UnzipRemoteTestCallable extends MasterToSlaveCallable<String, Exception> {
        private final File destination;
        private final File source;

        private UnzipRemoteTestCallable(File destination, File source) {
            this.destination = destination;
            this.source = source;
        }

        @Override
        public String call() throws Exception {
            FilePath onAgent = new FilePath(source);
            onAgent.unzip(toFilePathOnController(destination));
            return null;
        }
    }

    // --------

    @Test
    @Issue("SECURITY-2485")
    public void testCopyRecursiveFromControllerToAgent() {
        IOException ex = assertThrowsIOExceptionCausedBy(IOException.class, () -> invokeOnAgent(new CopyRecursiveToFromControllerToAgentCallable(new FilePath(new File(j.jenkins.getRootDir(), "secrets")))));
        assertThat(Objects.requireNonNull(ex).getMessage(), containsString("Unexpected end of ZLIB input stream")); // TODO this used to say "premature", why the change?
    }
    private static class CopyRecursiveToFromControllerToAgentCallable extends MasterToSlaveCallable<Integer, Exception> {
        private final FilePath controllerFilePath;

        private CopyRecursiveToFromControllerToAgentCallable(FilePath controllerFilePath) {
            this.controllerFilePath = controllerFilePath;
        }

        @Override
        public Integer call() throws Exception {
            return controllerFilePath.copyRecursiveTo(new FilePath(Files.createTempDirectory("jenkins-test").toFile()));
        }
    }

    // --------

    @Test
    @Issue("SECURITY-2485")
    public void testCopyRecursiveFromAgentToController() {
        assertThrowsIOExceptionCausedBy(SecurityException.class, () -> invokeOnAgent(new CopyRecursiveToFromAgentToControllerCallable(new FilePath(new File(j.jenkins.getRootDir(), "secrets")))));
    }
    private static class CopyRecursiveToFromAgentToControllerCallable extends MasterToSlaveCallable<Integer, Exception> {
        private final FilePath controllerFilePath;

        private CopyRecursiveToFromAgentToControllerCallable(FilePath controllerFilePath) {
            this.controllerFilePath = controllerFilePath;
        }

        @Override
        public Integer call() throws Exception {
            final File localPath = Files.createTempDirectory("jenkins-test").toFile();
            assertTrue(new File(localPath, "tmpfile").createNewFile());
            return new FilePath(localPath).copyRecursiveTo(controllerFilePath);
        }
    }

    // --------

    @Test
    @Issue("SECURITY-2486")
    public void testDecoyWrapper() {
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new ReadToStringBypassCallable(j.jenkins.getRootDir())));
    }

    private static class ReadToStringBypassCallable extends MasterToSlaveCallable<String, Exception> {
        private final File file;

        private ReadToStringBypassCallable(File file) {
            this.file = file;
        }

        @Override
        public String call() throws Exception {
            final Class<?> readToStringClass = Class.forName("hudson.FilePath$ReadToString");
            final Constructor<?> constructor = readToStringClass.getDeclaredConstructor(); // Used to have FilePath.class from non-static context
            constructor.setAccessible(true);

            //FilePath agentFilePath = new FilePath(new File("on agent lol")); // only used for the core code before fix

            final SlaveToMasterFileCallable<?> callable = (SlaveToMasterFileCallable<?>) constructor.newInstance(); // agentFilePath

            FilePath controllerFilePath = toFilePathOnController(new File(file, "secrets/master.key"));
            final Object returned = controllerFilePath.act(callable);
            return (String) returned;
        }
    }

    // --------

    @Test
    @Issue("SECURITY-2531")
    public void testSymlinkCheck() throws Exception {
        final File buildDir = j.buildAndAssertSuccess(j.createFreeStyleProject()).getRootDir();
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new SymlinkCreator(buildDir)));
    }
    private static class SymlinkCreator extends MasterToSlaveCallable<String, Exception> {
        private final File baseDir;
        private SymlinkCreator(File baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public String call() throws Exception {
            toFilePathOnController(new File(baseDir, "child")).symlinkTo(baseDir.getPath() + "child2", TaskListener.NULL);
            return null;
        }
    }

    // --------

    // --------

    @Test
    @Issue("SECURITY-2538") // SECURITY-2538 adjacent, confirms that reading, stating etc. is supposed to be possible for userContent
    public void testReadUserContent() throws Exception {
        invokeOnAgent(new UserContentReader(new File(j.jenkins.getRootDir(), "userContent")));
    }
    private static class UserContentReader extends MasterToSlaveCallable<String, Exception> {
        private final File userContent;

        private UserContentReader(File userContent) {
            this.userContent = userContent;
        }

        @Override
        public String call() throws Exception {
            final FilePath userContentFilePath = toFilePathOnController(userContent);
            userContentFilePath.lastModified();
            userContentFilePath.zip(NullOutputStream.NULL_OUTPUT_STREAM);
            assertThat(userContentFilePath.child("readme.txt").readToString(), containsString(hudson.model.Messages.Hudson_USER_CONTENT_README()));
            return null;
        }
    }

    @Test
    @Issue("SECURITY-2538")
    public void testRenameTo() throws Exception {
        final File buildDir = j.buildAndAssertSuccess(j.createFreeStyleProject()).getRootDir();
        final File userContentDir = new File(j.jenkins.getRootDir(), "userContent");
        final File readme = new File(userContentDir, "readme.txt");
        final File to = new File(buildDir, "readme.txt");
        assertTrue("readme.txt is a file", readme.isFile());
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new RenameToCaller(readme, to)));
        assertTrue("readme.txt is still a file", readme.isFile());
        assertFalse("to does not exist", to.exists());
    }
    private static class RenameToCaller extends MasterToSlaveCallable<String, Exception> {
        private final File from;
        private final File to;

        private RenameToCaller(File from, File to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public String call() throws Exception {
            toFilePathOnController(from).renameTo(toFilePathOnController(to));
            return null;
        }
    }

    @Test
    @Issue("SECURITY-2538")
    public void testMoveChildren() throws Exception {
        final File buildDir = j.buildAndAssertSuccess(j.createFreeStyleProject()).getRootDir();
        final File userContentDir = new File(j.jenkins.getRootDir(), "userContent");
        // The implementation of MoveAllChildrenTo seems odd and ends up removing the source directory, so work only in subdir of userContent
        final File userContentSubDir = new File(userContentDir, "stuff");
        assertTrue(userContentSubDir.mkdirs());
        final File userContentSubDirFileA = new File(userContentSubDir, "fileA");
        final File userContentSubDirFileB = new File(userContentSubDir, "fileB");
        assertTrue(userContentSubDirFileA.createNewFile());
        assertTrue(userContentSubDirFileB.createNewFile());
        assertTrue("userContentSubDir is a directory", userContentSubDir.isDirectory());
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new MoveAllChildrenToCaller(userContentSubDir, buildDir)));
        assertTrue("userContentSubDir is a directory", userContentSubDir.isDirectory());
        assertFalse("no fileA in buildDir", new File(buildDir, "fileA").exists());
        assertFalse("no fileB in buildDir", new File(buildDir, "fileB").exists());
        assertTrue("fileA is still a file", userContentSubDirFileA.isFile());
        assertTrue("fileB is still a file", userContentSubDirFileB.isFile());
    }
    private static class MoveAllChildrenToCaller extends MasterToSlaveCallable<String, Exception> {
        private final File from;
        private final File to;

        private MoveAllChildrenToCaller(File from, File to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public String call() throws Exception {
            toFilePathOnController(from).moveAllChildrenTo(toFilePathOnController(to));
            return null;
        }
    }

    // --------

    @Test
    @Issue("SECURITY-2539")
    public void testCreateTempFile() {
        final File rootDir = j.jenkins.getRootDir();
        assertFalse(Arrays.stream(Objects.requireNonNull(rootDir.listFiles())).anyMatch(it -> it.getName().contains("prefix")));

        // Extra level of catch/throw
        logging.capture(10);
        final IOException ioException = assertThrowsIOExceptionCausedBy(IOException.class, () -> invokeOnAgent(new CreateTempFileCaller(rootDir)));
        assertNotNull(ioException);
        final Throwable cause = ioException.getCause();
        assertNotNull(cause);
        assertTrue(cause instanceof SecurityException);
        assertThat(cause.getMessage(), not(containsString("prefix"))); // redacted
        assertThat(logging, recorded(containsString("'create'")));
        assertThat(logging, recorded(containsString("/prefix-security-check-dummy-suffix")));
        assertFalse(Arrays.stream(Objects.requireNonNull(rootDir.listFiles())).anyMatch(it -> it.getName().contains("prefix")));
    }
    private static class CreateTempFileCaller extends MasterToSlaveCallable<String, Exception> {
        private final File baseDir;

        private CreateTempFileCaller(File baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public String call() throws Exception {
            toFilePathOnController(baseDir).createTempFile("prefix", "suffix");
            return null;
        }
    }


    @Test
    @Issue("SECURITY-2539")
    public void testCreateTextTempFile() {
        final File rootDir = j.jenkins.getRootDir();
        assertFalse(Arrays.stream(Objects.requireNonNull(rootDir.listFiles())).anyMatch(it -> it.getName().contains("prefix")));

        // Extra level of catch/throw
        logging.capture(10);
        final IOException ioException = assertThrowsIOExceptionCausedBy(IOException.class, () -> invokeOnAgent(new CreateTextTempFileCaller(rootDir)));
        assertNotNull(ioException);
        final Throwable cause = ioException.getCause();
        assertNotNull(cause);
        assertTrue(cause instanceof SecurityException);
        assertThat(cause.getMessage(), not(containsString("prefix"))); // redacted
        assertThat(logging, recorded(containsString("'create'")));
        assertThat(logging, recorded(containsString("/prefix-security-check-dummy-suffix")));
        assertFalse(Arrays.stream(Objects.requireNonNull(rootDir.listFiles())).anyMatch(it -> it.getName().contains("prefix")));
    }
    private static class CreateTextTempFileCaller extends MasterToSlaveCallable<String, Exception> {
        private final File baseDir;

        private CreateTextTempFileCaller(File baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public String call() throws Exception {
            toFilePathOnController(baseDir).createTextTempFile("prefix", "suffix", "content");
            return null;
        }
    }

    @Test
    @Issue("SECURITY-2539")
    public void testCreateTempDir() {
        final File rootDir = j.jenkins.getRootDir();
        assertFalse(Arrays.stream(Objects.requireNonNull(rootDir.listFiles())).anyMatch(it -> it.getName().contains("prefix")));

        // Extra level of catch/throw
        logging.capture(10);
        final IOException ioException = assertThrowsIOExceptionCausedBy(IOException.class, () -> invokeOnAgent(new CreateTempDirCaller(rootDir)));
        assertNotNull(ioException);
        final Throwable cause = ioException.getCause();
        assertNotNull(cause);
        assertTrue(cause instanceof SecurityException);
        assertThat(cause.getMessage(), not(containsString("prefix"))); // redacted
        assertThat(logging, recorded(containsString("'mkdirs'")));
        assertThat(logging, recorded(containsString("/prefix.suffix-security-test"))); // weird but that's what it looks like
        assertFalse(Arrays.stream(Objects.requireNonNull(rootDir.listFiles())).anyMatch(it -> it.getName().contains("prefix")));
    }
    private static class CreateTempDirCaller extends MasterToSlaveCallable<String, Exception> {
        private final File baseDir;

        private CreateTempDirCaller(File baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public String call() throws Exception {
            toFilePathOnController(baseDir).createTempDir("prefix", "suffix");
            return null;
        }
    }
    // --------

    @Test
    @Issue("SECURITY-2541")
    public void testStatStuff() {
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new ToURICaller(j.jenkins.getRootDir())));
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new FreeDiskSpaceCaller(j.jenkins.getRootDir())));
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new TotalDiskSpaceCaller(j.jenkins.getRootDir())));
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new UsableDiskSpaceCaller(j.jenkins.getRootDir())));
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new AbsolutizeCaller(j.jenkins.getRootDir())));
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new HasSymlinkCaller(new File (j.jenkins.getRootDir(), "secrets"), j.jenkins.getRootDir())));
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new IsDescendantCaller(j.jenkins.getRootDir(), "secrets")));
    }

    private static class ToURICaller extends MasterToSlaveCallable<URI, Exception> {
        private final File file;

        private ToURICaller(File file) {
            this.file = file;
        }

        @Override
        public URI call() throws Exception {
            return toFilePathOnController(file).toURI();
        }
    }
    private static class AbsolutizeCaller extends MasterToSlaveCallable<String, Exception> {
        private final File file;

        private AbsolutizeCaller(File file) {
            this.file = file;
        }

        @Override
        public String call() throws Exception {
            return toFilePathOnController(file).absolutize().getRemote();
        }
    }
    private static class HasSymlinkCaller extends MasterToSlaveCallable<Boolean, Exception> {
        private final File file;
        private final File root;

        private HasSymlinkCaller(File file, File root) {
            this.file = file;
            this.root = root;
        }

        @Override
        public Boolean call() throws Exception {
            return toFilePathOnController(file).hasSymlink(toFilePathOnController(root), false);
        }
    }
    private static class IsDescendantCaller extends MasterToSlaveCallable<Boolean, Exception> {
        private final File file;
        private final String childPath;

        private IsDescendantCaller(File file, String childPath) {
            this.file = file;
            this.childPath = childPath;
        }

        @Override
        public Boolean call() throws Exception {
            return toFilePathOnController(file).isDescendant(childPath);
        }
    }
    private static class FreeDiskSpaceCaller extends MasterToSlaveCallable<Long, Exception> {
        private final File file;

        private FreeDiskSpaceCaller(File file) {
            this.file = file;
        }

        @Override
        public Long call() throws Exception {
            return toFilePathOnController(file).getFreeDiskSpace();
        }
    }
    private static class UsableDiskSpaceCaller extends MasterToSlaveCallable<Long, Exception> {
        private final File file;

        private UsableDiskSpaceCaller(File file) {
            this.file = file;
        }

        @Override
        public Long call() throws Exception {
            return toFilePathOnController(file).getUsableDiskSpace();
        }
    }
    private static class TotalDiskSpaceCaller extends MasterToSlaveCallable<Long, Exception> {
        private final File file;

        private TotalDiskSpaceCaller(File file) {
            this.file = file;
        }

        @Override
        public Long call() throws Exception {
            return toFilePathOnController(file).getTotalDiskSpace();
        }
    }

    // --------

    @Test
    @Issue("SECURITY-2542") // adjacent, this confirms we follow symlinks when it's within allowed directories
    public void testGlobFollowsSymlinks() throws Exception {
        assumeFalse(Functions.isWindows());
        final File buildDir = j.buildAndAssertSuccess(j.createFreeStyleProject()).getRootDir();
        // We cannot touch the build dir itself
        final File innerDir = new File(buildDir, "dir");
        final File innerDir2 = new File(buildDir, "dir2");
        assertTrue(innerDir.mkdirs());
        assertTrue(innerDir2.mkdirs());
        assertTrue(new File(innerDir2, "the-file").createNewFile());
        Util.createSymlink(innerDir, "../dir2", "link", TaskListener.NULL);
        assertTrue(new File(innerDir, "link/the-file").exists());
        final int files = invokeOnAgent(new GlobCaller(innerDir));
        assertEquals(1, files);
    }
    @Test
    @Issue("SECURITY-2542")
    public void testGlobSymlinksThrowsOutsideAllowedDirectories() throws Exception {
        assumeFalse(Functions.isWindows());
        final File buildDir = j.buildAndAssertSuccess(j.createFreeStyleProject()).getRootDir();
        // We cannot touch the build dir itself
        final File innerDir = new File(buildDir, "dir");
        assertTrue(innerDir.mkdirs());
        Util.createSymlink(innerDir, "../../../../../secrets", "secrets-link", TaskListener.NULL);
        assertTrue(new File(innerDir, "secrets-link/master.key").exists());
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new GlobCaller(innerDir)));
    }
    private static class GlobCaller extends MasterToSlaveCallable<Integer, Exception> {
        private final File root;

        private GlobCaller(File root) {
            this.root = root;
        }

        @Override
        public Integer call() throws Exception {
            return toFilePathOnController(root).list("**/*", "", false).length;
        }
    }

    // --------

    @Issue("SECURITY-2455") // general issue -- Maven Projects would no longer be allowed to perform some actions
    @Test
    public void testMavenReportersAllowListForTopLevelJob() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject();
        final File topLevelProjectDir = project.getRootDir();

        // similar but wrong names:
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new MkDirsWriter(new File(topLevelProjectDir, "not-site"))));
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new MkDirsWriter(new File(topLevelProjectDir, "not-javadoc"))));

        // project-level archived stuff:
        invokeOnAgent(new MkDirsWriter(new File(topLevelProjectDir, "javadoc")));
        invokeOnAgent(new MkDirsWriter(new File(topLevelProjectDir, "test-javadoc")));
        invokeOnAgent(new MkDirsWriter(new File(topLevelProjectDir, "site")));
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new MkDirsWriter(new File(topLevelProjectDir, "cobertura"))));

        // cannot mkdirs this from agent:
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new MkDirsWriter(new File(topLevelProjectDir, "modules"))));

        final File mavenModuleDir = new File(topLevelProjectDir, "modules/pretend-maven-module");
        assertTrue(mavenModuleDir.mkdirs());

        // module-level archived stuff:
        invokeOnAgent(new MkDirsWriter(new File(mavenModuleDir, "javadoc")));
        invokeOnAgent(new MkDirsWriter(new File(mavenModuleDir, "test-javadoc")));
        assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new MkDirsWriter(new File(mavenModuleDir, "site"))));
        invokeOnAgent(new MkDirsWriter(new File(mavenModuleDir, "cobertura")));
    }

    @Issue("SECURITY-2455") // general issue -- Maven Projects would no longer be allowed to perform some actions
    @Test
    public void testMavenReportersAllowListForJobInFolder() throws Exception {
        final MockFolder theFolder = j.createFolder("theFolder");
        {
            // basic child job
            final FreeStyleProject childProject = theFolder.createProject(FreeStyleProject.class, "child");
            final File childProjectRootDir = childProject.getRootDir();

            // project-level archived stuff for child project inside folder:
            invokeOnAgent(new MkDirsWriter(new File(childProjectRootDir, "javadoc")));
            invokeOnAgent(new MkDirsWriter(new File(childProjectRootDir, "test-javadoc")));
            invokeOnAgent(new MkDirsWriter(new File(childProjectRootDir, "site")));
            assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new MkDirsWriter(new File(childProjectRootDir, "cobertura"))));
        }

        { // misleadingly named child job (like one of the approved folders):
            final FreeStyleProject siteChildProject = theFolder.createProject(FreeStyleProject.class, "site");
            final File siteChildProjectRootDir = siteChildProject.getRootDir();

            // cannot mkdirs this from agent despite 'site' in the path (but on wrong level):
            assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new MkDirsWriter(siteChildProjectRootDir)));
            assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new MkDirsWriter(new File(siteChildProjectRootDir, "foo"))));
            assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new MkDirsWriter(new File(siteChildProjectRootDir, "modules"))));

            // project-level archived stuff for another child inside folder:
            invokeOnAgent(new MkDirsWriter(new File(siteChildProjectRootDir, "javadoc")));
            invokeOnAgent(new MkDirsWriter(new File(siteChildProjectRootDir, "test-javadoc")));
            invokeOnAgent(new MkDirsWriter(new File(siteChildProjectRootDir, "site")));
            assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new MkDirsWriter(new File(siteChildProjectRootDir, "cobertura"))));

            final File childProjectMavenModuleDir = new File(siteChildProjectRootDir, "modules/pretend-maven-module");
            assertTrue(childProjectMavenModuleDir.mkdirs());

            // module-level archived stuff:
            invokeOnAgent(new MkDirsWriter(new File(childProjectMavenModuleDir, "javadoc")));
            invokeOnAgent(new MkDirsWriter(new File(childProjectMavenModuleDir, "test-javadoc")));
            assertThrowsIOExceptionCausedBySecurityException(() -> invokeOnAgent(new MkDirsWriter(new File(childProjectMavenModuleDir, "site"))));
            invokeOnAgent(new MkDirsWriter(new File(childProjectMavenModuleDir, "cobertura")));
        }
    }

    private static class MkDirsWriter extends MasterToSlaveCallable<Object, Exception> {
        private final File root;

        private MkDirsWriter(File root) {
            this.root = root;
        }

        @Override
        public Object call() throws Exception {
            toFilePathOnController(root).mkdirs();
            toFilePathOnController(new File(root, "file.txt")).write("text", "UTF-8");
            return null;
        }
    }

    // --------

    // Misc tests

    @LocalData
    @Test
    public void testRemoteLocalUnzip() throws Exception {
        final DumbSlave onlineSlave = j.createOnlineSlave();
        final File zipFile = new File(j.jenkins.getRootDir(), "file.zip");
        assertTrue(zipFile.isFile());
        final FilePath agentRootPath = onlineSlave.getRootPath();
        final FilePath agentZipPath = agentRootPath.child("file.zip");
        new FilePath(zipFile).copyTo(agentZipPath);
        agentZipPath.unzip(agentRootPath);
    }

    // --------

    // Utility functions

    protected static FilePath toFilePathOnController(File file) {
        return toFilePathOnController(file.getPath());
    }

    protected static FilePath toFilePathOnController(String path) {
        final VirtualChannel channel = AgentComputerUtil.getChannelToMaster();
        return new FilePath(channel, path);
    }

    protected Node agent;

    protected  <T, X extends Throwable> T invokeOnAgent(MasterToSlaveCallable<T, X> callable) throws Exception, X {
        if (agent == null) {
            agent = j.createOnlineSlave();
        }
        return Objects.requireNonNull(agent.getChannel()).call(callable);
    }

    private static SecurityException assertThrowsIOExceptionCausedBySecurityException(ThrowingRunnable runnable) {
        return assertThrowsIOExceptionCausedBy(SecurityException.class, runnable);
    }

    private static <X extends Throwable> X assertThrowsIOExceptionCausedBy(Class<X> causeClass, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (IOException ex) {
            final Throwable cause = ex.getCause();
            assertTrue("IOException with message: '" + ex.getMessage() + "' wasn't caused by " + causeClass + ": " + (cause == null ? "(null)" : (cause.getClass().getName() + ": " + cause.getMessage())),
                    cause != null && causeClass.isAssignableFrom(cause.getClass()));
            return causeClass.cast(cause);
        } catch (Throwable t) {
            fail("Threw other Throwable: " + t.getClass() + " with message " + t.getMessage());
        }
        fail("Expected exception but passed");
        return null;
    }
}
