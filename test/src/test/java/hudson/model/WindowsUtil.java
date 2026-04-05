/*
 * The MIT License
 *
 * Copyright (c) 2026
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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility methods for Windows specific details on tests.
 *
 * @author Mark Waite
 */
public class WindowsUtil {

    private static final AtomicReference<Boolean> symlinkSupported = new AtomicReference<>();

    /**
     * Fail the test if running in CI and symlinks are not supported.
     * We do not want to fail the test on a developer computer if
     * symlinks are not enabled, but we do want to fail the test if a
     * CI agent on Windows does not have symbolic links enabled.
     */
    private static void assertConfiguration(boolean supported) {
        if (System.getenv("CI") != null) {
            assertTrue(supported, "Jenkins CI configurations must enable symlinks on Windows");
        }
    }

    /**
     * Returns true if Windows allows a symbolic link to be created.
     *
     * @return true if Windows allows a symbolic link to be created.
     * @throws IOException if creation or removal of temporary files fails
     */
    public static boolean isWindowsSymlinkSupported() throws IOException {
        // Fast path, don't acquire unnecessary lock
        Boolean supported = symlinkSupported.get();
        if (supported != null) {
            assertConfiguration(supported);
            return supported;
        }
        synchronized (WindowsUtil.class) {
            supported = symlinkSupported.get();
            if (supported != null) {
                assertConfiguration(supported);
                return supported;
            }
            Path tempDir = Files.createTempDirectory("symlink-check");
            Path target = Files.createFile(tempDir.resolve("target.txt"));
            Path link = tempDir.resolve("link.txt");

            try {
                Files.createSymbolicLink(link, target);
                Files.delete(link);
                symlinkSupported.set(true);
            } catch (IOException | UnsupportedOperationException uoe) {
                symlinkSupported.set(false);
            } finally {
                Files.delete(target);
                Files.delete(tempDir);
            }
        }
        assertConfiguration(symlinkSupported.get());
        return symlinkSupported.get();
    }
}
