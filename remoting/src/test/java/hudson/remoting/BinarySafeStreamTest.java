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
package hudson.remoting;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;

/**
 * @author Kohsuke Kawaguchi
 */
public class BinarySafeStreamTest extends TestCase {
    public void test1() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        OutputStream o = BinarySafeStream.wrap(buf);
        byte[] data = "Sending some data to make sure it's encoded".getBytes("UTF-8");

        o.write(data);
        o.close();

        InputStream in = BinarySafeStream.wrap(new ByteArrayInputStream(buf.toByteArray()));
        for (int i = 0; i < data.length; i++) {
            int ch = in.read();
            assertEquals(data[i], ch);
        }
        assertEquals(-1,in.read());
    }

    public void testSingleWrite() throws IOException {
        byte[] ds = getDataSet(65536);
        String master = Base64.encode(ds);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        OutputStream o = BinarySafeStream.wrap(buf);
        o.write(ds,0,ds.length);
        o.close();
        assertEquals(buf.toString(),master);
    }

    public void testChunkedWrites() throws IOException {
        byte[] ds = getDataSet(65536);
        String master = Base64.encode(ds);

        Random r = new Random(0);
        for( int i=0; i<16; i++) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            OutputStream o = BinarySafeStream.wrap(buf);
            randomCopy(r,new ByteArrayInputStream(ds),o,false);
            assertEquals(buf.toString(),master);
        }
    }

    public void testRoundtripNoFlush() throws IOException {
        _testRoundtrip(false);
    }

    public void testRoundtripFlush() throws IOException {
        _testRoundtrip(true);
    }

    private void _testRoundtrip(boolean flush) throws IOException {
        byte[] dataSet = getDataSet(65536);
        Random r = new Random(0);
        String master = Base64.encode(dataSet);

        for(int i=0; i<16; i++) {
            if(dump)
                System.out.println("test started");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            randomCopy(r,new ByteArrayInputStream(dataSet), BinarySafeStream.wrap(buf), flush);

            decodeByMaster(buf.toString(),dataSet);

            if(dump)
                System.out.println("------");

            ByteArrayOutputStream dst = new ByteArrayOutputStream();
            randomCopy(r,BinarySafeStream.wrap(new ByteArrayInputStream(buf.toByteArray())), dst,flush);

            byte[] result = dst.toByteArray();
            if(!Arrays.equals(dataSet, result)) {
                String msg = print(result, 0, result.length);
                for( int j=0; j<result.length; j++ )
                    assertEquals("offset "+j+" at "+msg,result[j],dataSet[j]);
                fail(msg);
            }

            if(dump)
                System.out.println("------");
        }
    }

    /**
     * Decodes by the JDK base64 code and make sure the encoded string looks correct.
     */
    private void decodeByMaster(String s, byte[] dataSet) throws IOException {
        int ptr=0;

        for( int i=0; i<s.length(); i+=4 ) {
            byte[] buf = Base64.decode(s.substring(i,i+4));
            for (int j = 0; j < buf.length; j++) {
                if(buf[j]!=dataSet[ptr])
                    fail("encoding error at offset "+ptr);
                ptr++;
            }
        }
    }

    /**
     * Creates a test data set.
     */
    private byte[] getDataSet(int len) {
        byte[] dataSet = new byte[len];
        for( int i=0; i<dataSet.length; i++ )
            dataSet[i] = (byte)i;
        return dataSet;
    }

    private void randomCopy(Random r, InputStream in, OutputStream out, boolean randomFlash) throws IOException {
        try {
            while(true) {
                switch(r.nextInt(3)) {
                case 0:
                    int ch = in.read();
                    if(dump)
                        System.out.println("read1("+ch+')');
                    assert ch>=0;
                    if(ch==-1)
                        return;
                    out.write(ch);
                    break;

                case 1:
                    int start = r.nextInt(16);
                    int chunk = r.nextInt(16);
                    int trail = r.nextInt(16);

                    byte[] tmp = new byte[start+chunk+trail];
                    int len = in.read(tmp, start, chunk);
                    if(dump)
                        System.out.println("read2("+print(tmp,start,len)+",len="+len+",chunk="+chunk+")");
                    if(len==-1)
                        return;

                    // check extra data corruption
                    for( int i=0; i<start; i++)
                        assertEquals(tmp[i],0);
                    for( int i=0; i<trail; i++)
                        assertEquals(tmp[start+chunk+i],0);

                    out.write(tmp,start,len);
                    break;

                case 2:
                    len = r.nextInt(16);
                    tmp = new byte[len];
                    len = in.read(tmp);
                    if(dump)
                        System.out.println("read3("+print(tmp,0,len)+",len="+len+')');
                    if(len==-1)
                        return;

                    // obtain the array of the exact size
                    byte[] n = new byte[len];
                    System.arraycopy(tmp,0,n,0,len);
                    out.write(n);
                }
                if(randomFlash && r.nextInt(8)==0)
                    out.flush();
            }
        } finally {
            out.close();
        }
    }

    private static String print(byte[] buf, int start, int len) {
        StringBuilder out = new StringBuilder();
        out.append('{');
        for (int i = 0; i < len; i++) {
            byte b = buf[i+start];
            if(i>0) out.append(',');
            out.append(((int)b)&0xFF);
        }
        return out.append('}').toString();
    }

    private static final boolean dump = false;
}
