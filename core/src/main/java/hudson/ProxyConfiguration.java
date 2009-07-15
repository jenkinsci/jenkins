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
package hudson;

import hudson.model.Hudson;
import hudson.model.Saveable;
import hudson.util.Scrambler;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;

import com.thoughtworks.xstream.XStream;

/**
 * HTTP proxy configuration.
 *
 * <p>
 * Use {@link #open(URL)} to open a connection with the proxy setting.
 * <p>
 * Proxy authentication (including NTLM) is implemented by setting a default
 * {@link Authenticator} which provides a {@link PasswordAuthentication}
 * (as described in the Java 6 tech note 
 * <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/http-auth.html">
 * Http Authentication</a>).
 * 
 * @see Hudson#proxy
 */
public final class ProxyConfiguration implements Saveable {
    public final String name;
    public final int port;

    /**
     * Possibly null proxy user name and password.
     * Password is base64 scrambled since this is persisted to disk.
     */
    private final String userName,password;

    public ProxyConfiguration(String name, int port) {
        this(name,port,null,null);
    }

    public ProxyConfiguration(String name, int port, String userName, String password) {
        this.name = name;
        this.port = port;
        this.userName = userName;
        this.password = Scrambler.scramble(password);
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return Scrambler.descramble(password);
    }

    public Proxy createProxy() {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(name,port));
    }

    public void save() throws IOException {
        if(BulkChange.contains(this))   return;
        getXmlFile().write(this);
    }

    public static XmlFile getXmlFile() {
        return new XmlFile(XSTREAM, new File(Hudson.getInstance().getRootDir(), "proxy.xml"));
    }

    public static ProxyConfiguration load() throws IOException {
        XmlFile f = getXmlFile();
        if(f.exists())
            return (ProxyConfiguration) f.read();
        else
            return null;
    }

    public static URLConnection open(URL url) throws IOException {
        ProxyConfiguration p = Hudson.getInstance().proxy;
        if(p==null)
            return url.openConnection();

        URLConnection con = url.openConnection(p.createProxy());
        if(p.getUserName()!=null) {
        	// Add an authenticator which provides the credentials for proxy authentication
            Authenticator.setDefault(new Authenticator() {
                public PasswordAuthentication getPasswordAuthentication() {
                    ProxyConfiguration p = Hudson.getInstance().proxy;
                    return new PasswordAuthentication(p.getUserName(),
                            p.getPassword().toCharArray());
                }
            });
        }
        return con;
    }

    private static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.alias("proxy", ProxyConfiguration.class);
    }
}
