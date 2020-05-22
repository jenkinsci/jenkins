/*
 * The MIT License
 *
 * Copyright (c) 2020, Sumit Sarin and Jenkins project contributors
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

import com.thoughtworks.xstream.converters.basic.DateConverter;
import hudson.BulkChange;
import hudson.Extension;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.listeners.SaveableListener;
import hudson.util.AtomicFileWriter;
import hudson.util.PersistedList;
import jenkins.model.FingerprintFacet;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class FileFingerprintStorage extends FingerprintStorage {

    private static final Logger logger = Logger.getLogger(Fingerprint.class.getName());
    private static final DateConverter DATE_CONVERTER = new DateConverter();

    public @CheckForNull Fingerprint load(@NonNull byte[] md5sum) throws IOException {
        return load(Fingerprint.getFingerprintFile(md5sum));
    }

    static @CheckForNull Fingerprint load(@NonNull File file) throws IOException {
        XmlFile configFile = Fingerprint.getConfigFile(file);
        if(!configFile.exists())
            return null;

        long start=0;
        if(logger.isLoggable(Level.FINE))
            start = System.currentTimeMillis();

        try {
            Object loaded = configFile.read();
            if (!(loaded instanceof Fingerprint)) {
                throw new IOException("Unexpected Fingerprint type. Expected " + Fingerprint.class + " or subclass but got "
                        + (loaded != null ? loaded.getClass() : "null"));
            }
            Fingerprint f = (Fingerprint) loaded;
            if(logger.isLoggable(Level.FINE))
                logger.fine("Loading fingerprint "+file+" took "+(System.currentTimeMillis()-start)+"ms");
            if (f.facets==null)
                f.facets = new PersistedList<>(f);
            for (FingerprintFacet facet : f.facets)
                facet._setOwner(f);
            return f;
        } catch (IOException e) {
            if(file.exists() && file.length()==0) {
                // Despite the use of AtomicFile, there are reports indicating that people often see
                // empty XML file, presumably either due to file system corruption (perhaps by sudden
                // power loss, etc.) or abnormal program termination.
                // generally we don't want to wipe out user data just because we can't load it,
                // but if the file size is 0, which is what's reported in HUDSON-2012, then it seems
                // like recovering it silently by deleting the file is not a bad idea.
                logger.log(Level.WARNING, "Size zero fingerprint. Disk corruption? {0}", configFile);
                file.delete();
                return null;
            }
            String parseError = Fingerprint.messageOfParseException(e);
            if (parseError != null) {
                logger.log(Level.WARNING, "Malformed XML in {0}: {1}", new Object[] {configFile, parseError});
                file.delete();
                return null;
            }
            logger.log(Level.WARNING, "Failed to load "+configFile,e);
            throw e;
        }
    }

    public synchronized void save(Fingerprint fp) throws IOException {
        if(BulkChange.contains(fp))
            return;

        long start=0;
        if(logger.isLoggable(Level.FINE))
            start = System.currentTimeMillis();

        File file = Fingerprint.getFingerprintFile(Fingerprint.toByteArray(fp.getHashString()));
        save(fp, file);
        SaveableListener.fireOnChange(fp, Fingerprint.getConfigFile(file));

        if(logger.isLoggable(Level.FINE))
            logger.fine("Saving fingerprint "+file+" took "+(System.currentTimeMillis()-start)+"ms");
    }

    static void save(Fingerprint fp, File file) throws IOException {
        if (fp.getPersistedFacets().isEmpty()) {
            file.getParentFile().mkdirs();
            // JENKINS-16301: fast path for the common case.
            AtomicFileWriter afw = new AtomicFileWriter(file);
            try {
                PrintWriter w = new PrintWriter(afw);
                w.println("<?xml version='1.1' encoding='UTF-8'?>");
                w.println("<fingerprint>");
                w.print("  <timestamp>");
                w.print(DATE_CONVERTER.toString(fp.getTimestamp()));
                w.println("</timestamp>");
                if (fp.getOriginal() != null) {
                    w.println("  <original>");
                    w.print("    <name>");
                    w.print(Util.xmlEscape(fp.getOriginal().name));
                    w.println("</name>");
                    w.print("    <number>");
                    w.print(fp.getOriginal().number);
                    w.println("</number>");
                    w.println("  </original>");
                }
                w.print("  <md5sum>");
                w.print(fp.getHashString());
                w.println("</md5sum>");
                w.print("  <fileName>");
                w.print(Util.xmlEscape(fp.getFileName()));
                w.println("</fileName>");
                w.println("  <usages>");
                for (Map.Entry<String, Fingerprint.RangeSet> e : fp.getUsages().entrySet()) {
                    w.println("    <entry>");
                    w.print("      <string>");
                    w.print(Util.xmlEscape(e.getKey()));
                    w.println("</string>");
                    w.print("      <ranges>");
                    w.print(Fingerprint.RangeSet.ConverterImpl.serialize(e.getValue()));
                    w.println("</ranges>");
                    w.println("    </entry>");
                }
                w.println("  </usages>");
                w.println("  <facets/>");
                w.print("</fingerprint>");
                w.flush();
                afw.commit();
            } finally {
                afw.abort();
            }
        } else {
            // Slower fallback that can persist facets.
            Fingerprint.getConfigFile(file).write(fp);
        }
    }

}
