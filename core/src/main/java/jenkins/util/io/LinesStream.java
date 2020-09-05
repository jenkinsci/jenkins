/*
 * The MIT License
 *
 * Copyright 2018 Daniel Trebbien.
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

import com.google.common.collect.AbstractIterator;

import edu.umd.cs.findbugs.annotations.CleanupObligation;
import edu.umd.cs.findbugs.annotations.DischargesObligation;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Represents a stream over the lines of a text file.
 * <p>
 * Although {@code LinesStream} implements {@link java.lang.Iterable}, it
 * is intended to be first used to initialize a resource in a try-with-resources
 * statement and then iterated, as in:
 * <pre>
 *  try (LinesStream stream = new LinesStream(...)) {
 *      for (String line : stream) {
 *          ...
 *      }
 *  }
 * </pre>
 * This pattern ensures that the underlying file handle is closed properly.
 * <p>
 * Like {@link java.nio.file.DirectoryStream}, {@code LinesStream} supports
 * creating at most one {@link Iterator}. Invoking {@link #iterator()} to
 * obtain a second or subsequent {@link Iterator} throws
 * {@link IllegalStateException}.
 *
 * @since 2.111
 */
@CleanupObligation
public class LinesStream implements Closeable, Iterable<String> {

    private final @NonNull BufferedReader in;
    private transient @Nullable Iterator<String> iterator;

    /**
     * Opens the text file at {@code path} for reading using charset
     * {@link java.nio.charset.StandardCharsets#UTF_8}.
     * @param path Path to the file to open for reading.
     * @throws IOException if the file at {@code path} cannot be opened for
     * reading.
     */
    public LinesStream(@NonNull Path path) throws IOException {
        in = Files.newBufferedReader(path); // uses UTF-8 by default
    }

    @DischargesObligation
    @Override
    public void close() throws IOException {
        in.close();
    }

    @Override
    public Iterator<String> iterator() {
        if (iterator!=null)
            throw new IllegalStateException("Only one Iterator can be created.");

        iterator = new AbstractIterator<String>() {
            @Override
            protected String computeNext() {
                try {
                    String r = in.readLine();
                    if (r==null) {
                        // Calling close() here helps ensure that the file
                        // handle is closed even when LinesStream is being used
                        // incorrectly, where it is iterated over without being
                        // used to initialize a resource of a try-with-resources
                        // statement.
                        in.close();
                        return endOfData();
                    }
                    return r;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        return iterator;
    }
}
