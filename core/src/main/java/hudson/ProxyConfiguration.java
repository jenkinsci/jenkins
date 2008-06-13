package hudson;

import com.thoughtworks.xstream.XStream;
import hudson.model.Hudson;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * HTTP proxy configuration.
 *
 * @see Hudson#proxy
 */
public final class ProxyConfiguration {
    public final String name;
    public final int port;

    public ProxyConfiguration(String name, int port) {
        this.name = name;
        this.port = port;
    }

    public Proxy createProxy() {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(name,port));
    }

    public void save() throws IOException {
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

    private static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.alias("proxy", ProxyConfiguration.class);
    }
}
