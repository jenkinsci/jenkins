package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.FilePath;
import hudson.model.Node;
import hudson.slaves.DumbSlave;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import jenkins.agents.ControllerToAgentCallable;
import jenkins.util.JenkinsJVM;
import org.apache.tools.tar.TarConstants;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class Security3657Test {
    @BeforeEach
    public void setup() throws Exception {
        // We're using SetEscapeHatchCallable to set this, potentially on the Jenkins JVM, so ensure a clean slate to start
        for (String fieldName : List.of("ALLOW_UNTAR_SYMLINK_RESOLUTION", "ALLOW_REENTRY_PATH_TRAVERSAL")) {
            final Field escapeHatch = FilePath.class.getDeclaredField(fieldName);
            escapeHatch.setAccessible(true);
            escapeHatch.set(null, null);
        }
    }

    @Test
    public void agentUnprotected(JenkinsRule j) throws Exception {
        final DumbSlave node = j.createOnlineSlave();
        node.getChannel().call(new EnsureNotJenkinsJVM());
        final UntarCallable callable = new UntarCallable(node.getRootDir());
        node.getChannel().call(callable);
    }

    @Test
    public void agentProtectedWithEscapeHatch(JenkinsRule j) throws Exception {
        // "escape hatch" is a misnomer here I guess
        final DumbSlave node = j.createOnlineSlave();
        node.getChannel().call(new EnsureNotJenkinsJVM());
        node.getChannel().call(new SetEscapeHatchCallable(false));
        final UntarCallable callable = new UntarCallable(node.getRootDir());
        final IOException ioException = assertThrows(IOException.class, () -> node.getChannel().call(callable));
        assertThat(ioException.getMessage(), is("Failed to extract file.tar"));
        assertThat(ioException.getCause().getMessage(), is("Tar file.tar attempts to write to file with symlink in path: foo/bar"));
    }

    @Test
    public void builtInProtected(JenkinsRule j) throws Exception {
        // Sort of a side effect of using JenkinsJVM, written this way to mirror agent tests only
        final Node node = j.jenkins.getComputer("(built-in)").getNode();
        node.getChannel().call(new EnsureJenkinsJVM());
        final UntarCallable callable = new UntarCallable(node.getRootDir());
        final IOException ioException = assertThrows(IOException.class, () -> node.getChannel().call(callable));
        assertThat(ioException.getMessage(), is("Failed to extract file.tar"));
        assertThat(ioException.getCause().getMessage(), is("Tar file.tar attempts to write to file with symlink in path: foo/bar"));
    }

    @Test
    public void builtInUnprotectedWithEscapeHatch(JenkinsRule j) throws Exception {
        final Node node = j.jenkins.getComputer("(built-in)").getNode();
        node.getChannel().call(new EnsureJenkinsJVM());
        node.getChannel().call(new SetEscapeHatchCallable(true));
        final UntarCallable callable = new UntarCallable(node.getRootDir());
        node.getChannel().call(callable);
    }

    private static class EnsureJenkinsJVM implements ControllerToAgentCallable<Void, IllegalStateException> {
        @Override
        public Void call() throws IllegalStateException {
            JenkinsJVM.checkJenkinsJVM();
            return null;
        }
    }

    private static class EnsureNotJenkinsJVM implements ControllerToAgentCallable<Void, IllegalStateException> {
        @Override
        public Void call() throws IllegalStateException {
            JenkinsJVM.checkNotJenkinsJVM();
            return null;
        }
    }

    private static class SetEscapeHatchCallable implements ControllerToAgentCallable<Void, Exception> {
        private final Boolean flag;

        SetEscapeHatchCallable(Boolean flag) {
            this.flag = flag;
        }

        @Override
        public Void call() throws Exception {
            for (String fieldName : List.of("ALLOW_UNTAR_SYMLINK_RESOLUTION", "ALLOW_REENTRY_PATH_TRAVERSAL")) {
                final Field escapeHatch = FilePath.class.getDeclaredField(fieldName);
                escapeHatch.setAccessible(true);
                escapeHatch.set(null, flag);
            }
            return null;
        }
    }

    private static class UntarCallable implements ControllerToAgentCallable<Void, Exception> {

        private final File rootDir;

        UntarCallable(File rootDir) {
            this.rootDir = rootDir;
        }

        @Override
        public Void call() throws Exception {
            rootDir.mkdirs();
            final File linkTar = createTarFile(rootDir, "link.tar", Entry.fileOrDir("anywhere/"), Entry.symlink("foo", "anywhere"));
            final File fileTar = createTarFile(rootDir, "file.tar", Entry.fileOrDir("foo/bar"));
            final File untarDestinationFile = new File(rootDir, "untar-destination");
            final FilePath untarDestinationFilePath = new FilePath(untarDestinationFile);
            untarDestinationFile.mkdirs();
            new FilePath(linkTar).untar(untarDestinationFilePath, FilePath.TarCompression.NONE);
            new FilePath(fileTar).untar(untarDestinationFilePath, FilePath.TarCompression.NONE);
            return null;
        }
    }

    // Lines below duplicated from core/…/Security3657Test.java

    private static File createTarFile(File base, String fileName, Entry... entries) throws IOException {
        File tarFile = new File(base, fileName);

        try (TarOutputStream tar = new TarOutputStream(Files.newOutputStream(tarFile.toPath()))) {
            for (Entry entry : entries) {
                entry.add(tar);
            }
        }
        return tarFile;
    }

    interface Entry {
        void add(TarOutputStream tar) throws IOException;

        /**
         * @param name the name of the file or folder. For folder, add trailing /
         */
        static Entry fileOrDir(String name) {
            return new FileOrDirEntry(name);
        }

        static Entry symlink(String name, String target) {
            return new SymlinkEntry(name, target);
        }

    }

    record FileOrDirEntry(String name) implements Entry {
        @Override
        public void add(TarOutputStream tar) throws IOException {
            TarEntry fileEntry = new TarEntry(name, true);
            byte[] content = "You've been pwned!".getBytes(StandardCharsets.UTF_8);
            if (!fileEntry.isDirectory()) {
                fileEntry.setSize(content.length);
            }
            tar.putNextEntry(fileEntry);
            if (!fileEntry.isDirectory()) {
                tar.write(content);
            }
            tar.closeEntry();
        }
    }

    record SymlinkEntry(String name, String target) implements Entry {
        @Override
        public void add(TarOutputStream tar) throws IOException {
            TarEntry symlinkEntry = new TarEntry(name, true);
            symlinkEntry.setLinkFlag(TarConstants.LF_SYMLINK);
            symlinkEntry.setLinkName(target);
            tar.putNextEntry(symlinkEntry);
            tar.closeEntry();
        }
    }
}
