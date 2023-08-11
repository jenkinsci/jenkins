/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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

package jenkins.util.io;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Functions;
import hudson.Util;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class PathRemover {

    public static PathRemover newSimpleRemover() {
        return new PathRemover(ignored -> false, PathChecker.ALLOW_ALL);
    }

    public static PathRemover newRemoverWithStrategy(@NonNull RetryStrategy retryStrategy) {
        return new PathRemover(retryStrategy, PathChecker.ALLOW_ALL);
    }

    public static PathRemover newFilteredRobustRemover(@NonNull PathChecker pathChecker, int maxRetries, boolean gcAfterFailedRemove, long waitBetweenRetries) {
        return new PathRemover(new PausingGCRetryStrategy(Math.max(maxRetries, 0), gcAfterFailedRemove, waitBetweenRetries), pathChecker);
    }

    private final RetryStrategy retryStrategy;
    private final PathChecker pathChecker;

    private PathRemover(@NonNull RetryStrategy retryStrategy, @NonNull PathChecker pathChecker) {
        this.retryStrategy = retryStrategy;
        this.pathChecker = pathChecker;
    }

    public void forceRemoveFile(@NonNull Path path) throws IOException {
        for (int retryAttempts = 0; ; retryAttempts++) {
            Optional<IOException> maybeError = tryRemoveFile(path);
            if (maybeError.isEmpty()) return;
            if (retryStrategy.shouldRetry(retryAttempts)) continue;
            IOException error = maybeError.get();
            throw new IOException(retryStrategy.failureMessage(path, retryAttempts), error);
        }
    }

    public void forceRemoveDirectoryContents(@NonNull Path path) throws IOException {
        for (int retryAttempt = 0; ; retryAttempt++) {
            List<IOException> errors = tryRemoveDirectoryContents(path);
            if (errors.isEmpty()) return;
            if (retryStrategy.shouldRetry(retryAttempt)) continue;
            throw new CompositeIOException(retryStrategy.failureMessage(path, retryAttempt), errors);
        }
    }

    public void forceRemoveRecursive(@NonNull Path path) throws IOException {
        for (int retryAttempt = 0; ; retryAttempt++) {
            List<IOException> errors = tryRemoveRecursive(path);
            if (errors.isEmpty()) return;
            if (retryStrategy.shouldRetry(retryAttempt)) continue;
            throw new CompositeIOException(retryStrategy.failureMessage(path, retryAttempt), errors);
        }
    }

    @Restricted(NoExternalUse.class)
    @FunctionalInterface
    public interface PathChecker {
        void check(@NonNull Path path) throws SecurityException;

        PathChecker ALLOW_ALL = path -> {};
    }

    @Restricted(NoExternalUse.class)
    @FunctionalInterface
    public interface RetryStrategy {
        boolean shouldRetry(int retriesAttempted);

        default String failureMessage(@NonNull Path fileToRemove, int retryCount) {
            StringBuilder sb = new StringBuilder()
                    .append("Unable to delete '")
                    .append(fileToRemove)
                    .append("'. Tried ")
                    .append(retryCount)
                    .append(" time");
            if (retryCount != 1) sb.append('s');
            sb.append('.');
            return sb.toString();
        }
    }

    private static class PausingGCRetryStrategy implements RetryStrategy {
        private final int maxRetries;
        private final boolean gcAfterFailedRemove;
        private final long waitBetweenRetries;
        private final ThreadLocal<Boolean> interrupted = ThreadLocal.withInitial(() -> false);

        private PausingGCRetryStrategy(int maxRetries, boolean gcAfterFailedRemove, long waitBetweenRetries) {
            this.maxRetries = maxRetries;
            this.gcAfterFailedRemove = gcAfterFailedRemove;
            this.waitBetweenRetries = waitBetweenRetries;
        }

        @SuppressFBWarnings(value = "DM_GC", justification = "Garbage collection happens only when "
                + "GC_AFTER_FAILED_DELETE is true. It's an experimental feature in Jenkins.")
        private void gcIfEnabled() {
            /* If the Jenkins process had the file open earlier, and it has not
             * closed it then Windows won't let us delete it until the Java object
             * with the open stream is Garbage Collected, which can result in builds
             * failing due to "file in use" on Windows despite working perfectly
             * well on other OSs. */
            if (gcAfterFailedRemove) System.gc();
        }

        @Override
        public boolean shouldRetry(int retriesAttempted) {
            if (retriesAttempted >= maxRetries) return false;
            gcIfEnabled();
            long delayMillis = waitBetweenRetries >= 0 ? waitBetweenRetries : -(retriesAttempted + 1) * waitBetweenRetries;
            if (delayMillis <= 0) return !Thread.interrupted();
            try {
                Thread.sleep(delayMillis);
                return true;
            } catch (InterruptedException e) {
                interrupted.set(true);
                return false;
            }
        }

        @Override
        public String failureMessage(@NonNull Path fileToRemove, int retryCount) {
            StringBuilder sb = new StringBuilder();
            sb.append("Unable to delete '");
            sb.append(fileToRemove);
            sb.append("'. Tried ");
            sb.append(retryCount + 1);
            sb.append(" time");
            if (retryCount != 1) sb.append('s');
            if (maxRetries > 0) {
                sb.append(" (of a maximum of ");
                sb.append(maxRetries + 1);
                sb.append(')');
                if (gcAfterFailedRemove)
                    sb.append(" garbage-collecting");
                if (waitBetweenRetries != 0 && gcAfterFailedRemove)
                    sb.append(" and");
                if (waitBetweenRetries != 0) {
                    sb.append(" waiting ");
                    sb.append(Util.getTimeSpanString(Math.abs(waitBetweenRetries)));
                    if (waitBetweenRetries < 0) {
                        sb.append("-");
                        sb.append(Util.getTimeSpanString(Math.abs(waitBetweenRetries) * (maxRetries + 1)));
                    }
                }
                if (waitBetweenRetries != 0 || gcAfterFailedRemove)
                    sb.append(" between attempts");
            }
            if (interrupted.get())
                sb.append(". The delete operation was interrupted before it completed successfully");
            sb.append('.');
            interrupted.set(false);
            return sb.toString();
        }
    }

    private Optional<IOException> tryRemoveFile(@NonNull Path path) {
        try {
            removeOrMakeRemovableThenRemove(path.normalize());
            return Optional.empty();
        } catch (IOException e) {
            return Optional.of(e);
        }
    }

    private List<IOException> tryRemoveRecursive(@NonNull Path path) {
        Path normalized = path.normalize();
        List<IOException> accumulatedErrors = Util.isSymlink(normalized) ? new ArrayList<>() :
                tryRemoveDirectoryContents(normalized);
        tryRemoveFile(normalized).ifPresent(accumulatedErrors::add);
        return accumulatedErrors;
    }

    private List<IOException> tryRemoveDirectoryContents(@NonNull Path path) {
        Path normalized = path.normalize();
        List<IOException> accumulatedErrors = new ArrayList<>();
        if (!Files.isDirectory(normalized)) return accumulatedErrors;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(normalized)) {
            for (Path child : children) {
                accumulatedErrors.addAll(tryRemoveRecursive(child));
            }
        } catch (IOException e) {
            accumulatedErrors.add(e);
        }
        return accumulatedErrors;
    }

    private void removeOrMakeRemovableThenRemove(@NonNull Path path) throws IOException {
        pathChecker.check(path);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            makeRemovable(path);
            try {
                Files.deleteIfExists(path);
            } catch (IOException e2) {
                // see https://java.net/projects/hudson/lists/users/archive/2008-05/message/357
                // I suspect other processes putting files in this directory
                if (Files.isDirectory(path)) {
                    List<String> entries;
                    try (Stream<Path> children = Files.list(path)) {
                        entries = children.map(Path::toString).collect(Collectors.toList());
                    }
                    throw new CompositeIOException("Unable to remove directory " + path + " with directory contents: " + entries, e, e2);
                }
                throw new CompositeIOException("Unable to remove file " + path, e, e2);
            }
        }
    }

    private static void makeRemovable(@NonNull Path path) throws IOException {
        if (!Files.isWritable(path)) {
            makeWritable(path);
        }
        /*
         on Unix both the file and the directory that contains it has to be writable
         for a file deletion to be successful. (Confirmed on Solaris 9)

         $ ls -la
         total 6
         dr-xr-sr-x   2 hudson   hudson       512 Apr 18 14:41 .
         dr-xr-sr-x   3 hudson   hudson       512 Apr 17 19:36 ..
         -r--r--r--   1 hudson   hudson       469 Apr 17 19:36 manager.xml
         -rw-r--r--   1 hudson   hudson         0 Apr 18 14:41 x
         $ rm x
         rm: x not removed: Permission denied
         */
        Optional<Path> maybeParent = Optional.ofNullable(path.getParent()).map(Path::normalize).filter(p -> !Files.isWritable(p));
        if (maybeParent.isPresent()) {
            makeWritable(maybeParent.get());
        }
    }

    private static void makeWritable(@NonNull Path path) throws IOException {
        if (!Functions.isWindows()) {
            try {
                PosixFileAttributes attrs = Files.readAttributes(path, PosixFileAttributes.class);
                Set<PosixFilePermission> newPermissions = attrs.permissions();
                newPermissions.add(PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(path, newPermissions);
            } catch (NoSuchFileException ignored) {
                return;
            } catch (UnsupportedOperationException ignored) {
                // PosixFileAttributes not supported, fall back to old IO.
            }
        } else {
          /*
           * If on Windows a folder has a read only attribute set, the file.setWritable(true) doesn't work (JENKINS-57855)
           */
          DosFileAttributeView dos = Files.getFileAttributeView(path, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
          if (dos != null) {
            dos.setReadOnly(false);
          }
        }

        /*
         * We intentionally do not check the return code of setWritable, because if it
         * is false we prefer to rethrow the exception thrown by Files.deleteIfExists,
         * which will have a more useful message than something we make up here.
         */
        path.toFile().setWritable(true);
    }

}
