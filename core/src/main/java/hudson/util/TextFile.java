/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Represents a text file.
 *
 * Provides convenience methods for reading and writing to it.
 *
 * @author Kohsuke Kawaguchi
 */
public class TextFile {
    public final File file;

    public TextFile(File file) {
        this.file = file;
    }

    public boolean exists() {
        return file.exists();
    }

    public void delete() {
        file.delete();
    }

    /**
     * Reads the entire contents and returns it.
     */
    public String read() throws IOException {
        StringWriter out = new StringWriter();
        PrintWriter w = new PrintWriter(out);
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));
        try {
            String line;
            while((line=in.readLine())!=null)
                w.println(line);
        } finally{
            in.close();
        }
        return out.toString();
    }

    /**
     * Overwrites the file by the given string.
     */
    public void write(String text) throws IOException {
        file.getParentFile().mkdirs();
        AtomicFileWriter w = new AtomicFileWriter(file);
        try {
            w.write(text);
            w.commit();
        } catch (IOException e) {
            w.abort();
            throw e;
        }
    }

    public String readTrim() throws IOException {
        return read().trim();
    }

    public String toString() {
        return file.toString();
    }
}
