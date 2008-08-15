package hudson;

import com.thoughtworks.xstream.XStream;
import hudson.model.Hudson;
import hudson.model.Saveable;
import hudson.util.XStream2;
import hudson.util.Scrambler;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLConnection;
import java.net.URL;

/**
 * HTTP proxy configuration.
 *
 * <p>
 * Use {@link #open(URL)} to open a connection with the proxy setting.
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
            con.setRequestProperty("Proxy-Authorization","Basic "+
                    Scrambler.scramble(p.getUserName()+':'+p.getPassword()));
        }
        return con;
    }

    private static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.alias("proxy", ProxyConfiguration.class);
    }
}
