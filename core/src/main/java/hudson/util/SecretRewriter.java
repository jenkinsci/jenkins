package hudson.util;

import com.trilead.ssh2.crypto.Base64;
import hudson.model.TaskListener;
import org.apache.commons.io.FileUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.LinkOption;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.util.HashSet;
import java.util.Set;

/**
 * Rewrites XML files by looking for Secrets that are stored with the old key and replaces them
 * by the new encrypted values.
 *
 * @author Kohsuke Kawaguchi
 */
public class SecretRewriter {
    private final Cipher cipher;
    private final SecretKey key;

    /**
     * How many files have been scanned?
     */
    private int count;

    /**
     * If non-null the original file before rewrite gets in here.
     */
    private final File backupDirectory;

    /**
     * Canonical paths of the directories we are recursing to protect
     * against symlink induced cycles.
     */
    private Set<String> callstack = new HashSet<String>();

    public SecretRewriter(File backupDirectory) throws GeneralSecurityException {
        cipher = Secret.getCipher("AES");
        key = Secret.getLegacyKey();
        this.backupDirectory = backupDirectory;
    }

    private String tryRewrite(String s) throws IOException, InvalidKeyException {
        if (s.length()<24)
            return s;   // Encrypting "" in Secret produces 24-letter characters, so this must be the minimum length
        if (!isBase64(s))
            return s;   // decode throws IOException if the input is not base64, and this is also a very quick way to filter

        byte[] in;
        try {
            in = Base64.decode(s.toCharArray());
        } catch (IOException e) {
            return s;   // not a valid base64
        }
        cipher.init(Cipher.DECRYPT_MODE, key);
        Secret sec = Secret.tryDecrypt(cipher, in);
        if(sec!=null) // matched
            return sec.getEncryptedValue(); // replace by the new encrypted value
        else // not encrypted with the legacy key. leave it unmodified
            return s;
    }

    /**
     * @param backup
     *      if non-null, the original file will be copied here before rewriting.
     *      if the rewrite doesn't happen, no copying.
     */
    public boolean rewrite(File f, File backup) throws InvalidKeyException, IOException {
        AtomicFileWriter w = new AtomicFileWriter(f, "UTF-8");
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(w));

            boolean modified = false; // did we actually change anything?
            try {
                FileInputStream fin = new FileInputStream(f);
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(fin, "UTF-8"));
                    String line;
                    StringBuilder buf = new StringBuilder();

                    while ((line=r.readLine())!=null) {
                        int copied=0;
                        buf.setLength(0);
                        while (true) {
                            int sidx = line.indexOf('>',copied);
                            if (sidx<0) break;
                            int eidx = line.indexOf('<',sidx);
                            if (eidx<0) break;

                            String elementText = line.substring(sidx+1,eidx);
                            String replacement = tryRewrite(elementText);
                            if (!replacement.equals(elementText))
                                modified = true;

                            buf.append(line.substring(copied,sidx+1));
                            buf.append(replacement);
                            copied = eidx;
                        }
                        buf.append(line.substring(copied));
                        out.println(buf.toString());
                    }
                } finally {
                    fin.close();
                }
            } finally {
                out.close();
            }

            if (modified) {
                if (backup!=null) {
                    backup.getParentFile().mkdirs();
                    FileUtils.copyFile(f,backup);
                }
                w.commit();
            }
            return modified;
        } finally {
            w.abort();
        }
    }


    /**
     * Recursively scans and rewrites a directory.
     *
     * This method shouldn't abort just because one file fails to rewrite.
     *
     * @return
     *      Number of files that were actually rewritten.
     */
    // synchronized to prevent accidental concurrent use. this instance is not thread safe
    public synchronized int rewriteRecursive(File dir, TaskListener listener) throws InvalidKeyException {
        return rewriteRecursive(dir,"",listener);
    }
    private int rewriteRecursive(File dir, String relative, TaskListener listener) throws InvalidKeyException {
        String canonical;
        try {
            canonical = dir.toPath().toRealPath(new LinkOption[0]).toString();
        } catch (IOException e) {
            canonical = dir.getAbsolutePath(); //
        }
        if (!callstack.add(canonical)) {
            listener.getLogger().println("Cycle detected: "+dir);
            return 0;
        }

        try {
            File[] children = dir.listFiles();
            if (children==null)     return 0;

            int rewritten=0;
            for (File child : children) {
                String cn = child.getName();
                if (cn.endsWith(".xml")) {
                    if ((count++)%100==0)
                        listener.getLogger().println("Scanning "+child);
                    try {
                        File backup = null;
                        if (backupDirectory!=null)  backup = new File(backupDirectory,relative+'/'+ cn);
                        if (rewrite(child,backup)) {
                            if (backup!=null)
                                listener.getLogger().println("Copied "+child+" to "+backup+" as a backup");
                            listener.getLogger().println("Rewritten "+child);
                            rewritten++;
                        }
                    } catch (IOException e) {
                        e.printStackTrace(listener.error("Failed to rewrite "+child));
                    }
                }
                if (child.isDirectory()) {
                    if (!isIgnoredDir(child))
                        rewritten += rewriteRecursive(child,
                                relative.length()==0 ? cn : relative+'/'+ cn,
                                listener);
                }
            }
            return rewritten;
        } finally {
            callstack.remove(canonical);
        }
    }

    /**
     * Decides if this directory is worth visiting or not.
     */
    protected boolean isIgnoredDir(File dir) {
        // ignoring the workspace and the artifacts directories. Both of them
        // are potentially large and they do not store any secrets.
        String n = dir.getName();
        return n.equals("workspace") || n.equals("artifacts")
            || n.equals("plugins") // no mutable data here
            || n.equals("jenkins.security.RekeySecretAdminMonitor") // we don't want to rewrite backups
            || n.equals(".") || n.equals("..");
    }

    private static boolean isBase64(char ch) {
        return 0<=ch && ch<128 && IS_BASE64[ch];
    }

    private static boolean isBase64(String s) {
        for (int i=0; i<s.length(); i++)
            if (!isBase64(s.charAt(i)))
                return false;
        return true;
    }

    private static final boolean[] IS_BASE64 = new boolean[128];
    static {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
        for (int i=0; i<chars.length();i++)
            IS_BASE64[chars.charAt(i)] = true;
    }
}
