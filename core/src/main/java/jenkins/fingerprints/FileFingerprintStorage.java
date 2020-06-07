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
package jenkins.fingerprints;

import com.thoughtworks.xstream.converters.basic.DateConverter;
import hudson.Extension;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Fingerprint;
import hudson.model.listeners.SaveableListener;
import hudson.util.AtomicFileWriter;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.xmlpull.v1.XmlPullParserException;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default file system storage implementation for fingerprints.
 *
 * @author Sumit Sarin
 */
@Restricted(Beta.class)
@Extension
public class FileFingerprintStorage extends FingerprintStorage {

    private static final Logger logger = Logger.getLogger(FileFingerprintStorage.class.getName());
    private static final DateConverter DATE_CONVERTER = new DateConverter();

    /**
     * Load the Fingerprint with the given unique id.
     */
    public @CheckForNull Fingerprint load(@NonNull String id) throws IOException {
        return load(getFingerprintFile(id));
    }

    /**
     * Load the Fingerprint stored inside the given file.
     */
    public static @CheckForNull Fingerprint load(@NonNull File file) throws IOException {
        XmlFile configFile = getConfigFile(file);
        if(!configFile.exists()) {
            return null;
        }

        try {
            Object loaded = configFile.read();
            if (!(loaded instanceof Fingerprint)) {
                throw new IOException("Unexpected Fingerprint type. Expected " + Fingerprint.class + " or subclass but got "
                        + (loaded != null ? loaded.getClass() : "null"));
            }
            Fingerprint f = (Fingerprint) loaded;
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
            String parseError = messageOfParseException(e);
            if (parseError != null) {
                logger.log(Level.WARNING, "Malformed XML in {0}: {1}", new Object[] {configFile, parseError});
                file.delete();
                return null;
            }
            logger.log(Level.WARNING, "Failed to load " + configFile, e);
            throw e;
        }
    }

    /**
     * Saves the given Fingerprint in local XML-based database.
     */
    public synchronized void save(Fingerprint fp) throws IOException {
        File file = getFingerprintFile(fp.getHashString());
        save(fp, file);
        // TODO(oleg_nenashev): Consider generalizing SaveableListener and invoking it for all storage implementations.
        //  https://issues.jenkins-ci.org/browse/JENKINS-62543
        SaveableListener.fireOnChange(fp, getConfigFile(file));
    }

    /**
     * Saves the given Fingerprint as XML inside file.
     */
    public static void save(Fingerprint fp, File file) throws IOException {
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
                    w.print(Util.xmlEscape(fp.getOriginal().getName()));
                    w.println("</name>");
                    w.print("    <number>");
                    w.print(fp.getOriginal().getNumber());
                    w.println("</number>");
                    w.println("  </original>");
                }
                // TODO(oleg_nenashev): Consider renaming the field: https://issues.jenkins-ci.org/browse/JENKINS-25808
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
                    w.print(serialize(e.getValue()));
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
            getConfigFile(file).write(fp);
        }
    }

    /**
     * Deletes the Fingerprint with the given unique ID.
     */
    public void delete(String id) throws IOException {
        File file = getFingerprintFile(id);
        if (!file.exists()) {
            return;
        }

        if (!file.delete()) {
            throw new IOException("Error occurred in deleting Fingerprint " + id);
        }

        File inner = new File(Jenkins.get().getRootDir(), "fingerprints/" + id.substring(0,2) + "/" + id.substring(2,4));
        String[] inner_files = inner.list();
        if (inner_files != null && inner_files.length == 0) {
            if (!inner.delete()){
                throw new IOException("Error occurred in deleting inner directory of Fingerprint " + id);
            }
        }

        File outer = new File(Jenkins.get().getRootDir(), "fingerprints/" + id.substring(0,2));
        String[] outer_files = outer.list();
        if (outer_files != null && outer_files.length == 0) {
            if (!outer.delete()){
                throw new IOException("Error occurred in deleting outer directory of Fingerprint " + id);
            }
        }
    }

    /**
     * The file we save our configuration.
     */
    private static @NonNull XmlFile getConfigFile(@NonNull File file) {
        return new XmlFile(Fingerprint.getXStream(), file);
    }

    /**
     * Determines the file name from unique id (md5sum).
     */
    private static @NonNull File getFingerprintFile(@NonNull String id) {
        return new File( Jenkins.get().getRootDir(),
                "fingerprints/" + id.substring(0,2) + '/' + id.substring(2,4) + '/' + id.substring(4) + ".xml");
    }

    private static String messageOfParseException(Throwable throwable) {
        if (throwable instanceof XmlPullParserException || throwable instanceof EOFException) {
            return throwable.getMessage();
        }
        Throwable causeOfThrowable = throwable.getCause();
        if (causeOfThrowable != null) {
            return messageOfParseException(causeOfThrowable);
        }
        return null;
    }

    /**
     * Used to serialize the range sets (builds) of the fingerprint.
     */
    private static String serialize(Fingerprint.RangeSet src) {
        StringBuilder buf = new StringBuilder(src.getRanges().size() * 10);
        for (Fingerprint.Range r : src.getRanges()) {
            if(buf.length() > 0) {
                buf.append(',');
            }
            if(r.isSingle()) {
                buf.append(r.getStart());
            } else {
                buf.append(r.getStart()).append('-').append(r.getEnd() - 1);
            }
        }
        return buf.toString();
    }

}
