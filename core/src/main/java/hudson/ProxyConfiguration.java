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

import com.thoughtworks.xstream.XStream;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.util.FormValidation;
import hudson.util.Scrambler;
import hudson.util.Secret;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import jenkins.model.Jenkins;
import jenkins.security.stapler.StaplerAccessibleType;
import jenkins.util.JenkinsJVM;
import jenkins.util.SystemProperties;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jenkinsci.Symbol;
import org.jvnet.robust_http_client.RetryableHttpStream;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

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
 * @see jenkins.model.Jenkins#proxy
 */
@StaplerAccessibleType
public final class ProxyConfiguration extends AbstractDescribableImpl<ProxyConfiguration> implements Saveable, Serializable {
    /**
     * Holds a default TCP connect timeout set on all connections returned from this class,
     * note this is value is in milliseconds, it's passed directly to {@link URLConnection#setConnectTimeout(int)}
     */
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = SystemProperties.getInteger("hudson.ProxyConfiguration.DEFAULT_CONNECT_TIMEOUT_MILLIS", (int)TimeUnit.SECONDS.toMillis(20));
    
    public final String name;
    public final int port;

    /**
     * Possibly null proxy user name.
     */
    private String userName;

    /**
     * List of host names that shouldn't use proxy, as typed by users.
     *
     * @see #getNoProxyHostPatterns()
     */
    @Restricted(NoExternalUse.class)
    public String noProxyHost;

    @Deprecated
    private String password;

    /**
     * encrypted password
     */
    private Secret secretPassword;
    
    private String testUrl;

    private transient Authenticator authenticator;

    private transient boolean authCacheSeeded;

    @DataBoundConstructor
    public ProxyConfiguration(String name, int port) {
        this(name,port,null,null);
    }

    public ProxyConfiguration(String name, int port, String userName, String password) {
        this(name,port,userName,password,null);
    }

    public ProxyConfiguration(String name, int port, String userName, String password, String noProxyHost) {
        this(name,port,userName,password,noProxyHost,null);
    }

    public ProxyConfiguration(String name, int port, String userName, String password, String noProxyHost, String testUrl) {
        this.name = Util.fixEmptyAndTrim(name);
        this.port = port;
        this.userName = Util.fixEmptyAndTrim(userName);
        String tempPassword = Util.fixEmptyAndTrim(password);
        this.secretPassword = tempPassword != null ? Secret.fromString(tempPassword) : null;
        this.noProxyHost = Util.fixEmptyAndTrim(noProxyHost);
        this.testUrl = Util.fixEmptyAndTrim(testUrl);
        this.authenticator = newAuthenticator();
    }

    private Authenticator newAuthenticator() {
        return new Authenticator() {
            @Override
            public PasswordAuthentication getPasswordAuthentication() {
                String userName = getUserName();
                if (getRequestorType() == RequestorType.PROXY && userName != null) {
                    return new PasswordAuthentication(userName, Secret.toString(secretPassword).toCharArray());
                }
                return null;
            }
        };
    }

    public String getUserName() {
        return userName;
    }

    public Secret getSecretPassword() {
        return secretPassword;
    }

    /**
     * @deprecated
     *      Use {@link #getSecretPassword()}
     */
    @Deprecated
    public String getPassword() {
        return Secret.toString(secretPassword);
    }

    /**
     * @deprecated
     *      Use {@link #getSecretPassword()}
     *
     * @return the encrypted proxy password
     */
    @Deprecated
    public String getEncryptedPassword() {
        return (secretPassword == null) ? null : secretPassword.getEncryptedValue();
    }

    public String getTestUrl() {
        return testUrl;
    }

    public int getPort() {
        return port;
    }

    public String getName() {
        return name;
    }

    /**
     * Returns the list of properly formatted no proxy host names.
     */
    public List<Pattern> getNoProxyHostPatterns() {
        return getNoProxyHostPatterns(noProxyHost);
    }

    public String getNoProxyHost() {
        return noProxyHost;
    }

    /**
     * Returns the list of properly formatted no proxy host names.
     */
    public static List<Pattern> getNoProxyHostPatterns(String noProxyHost) {
        if (noProxyHost==null)  return Collections.emptyList();

        List<Pattern> r = new ArrayList<>();
        for (String s : noProxyHost.split("[ \t\n,|]+")) {
            if (s.length()==0)  continue;
            r.add(Pattern.compile(s.replace(".", "\\.").replace("*", ".*")));
        }
        return r;
    }

    @DataBoundSetter
    public void setSecretPassword(Secret secretPassword) {
        this.secretPassword = secretPassword;
    }

    @DataBoundSetter
    public void setTestUrl(String testUrl) {
        this.testUrl = testUrl;
    }

    @DataBoundSetter
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @DataBoundSetter
    public void setNoProxyHost(String noProxyHost) {
        this.noProxyHost = noProxyHost;
    }

    /**
     * @deprecated
     *      Use {@link #createProxy(String)}
     */
    @Deprecated
    public Proxy createProxy() {
        return createProxy(null);
    }

    public Proxy createProxy(String host) {
        return createProxy(host, name, port, noProxyHost);
    }

    public static Proxy createProxy(String host, String name, int port, String noProxyHost) {
        if (host!=null && noProxyHost!=null) {
            for (Pattern p : getNoProxyHostPatterns(noProxyHost)) {
                if (p.matcher(host).matches())
                    return Proxy.NO_PROXY;
            }
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(name,port));
    }

    @Override
    public void save() throws IOException {
        if(BulkChange.contains(this))   return;
        XmlFile config = getXmlFile();
        config.write(this);
        SaveableListener.fireOnChange(this, config);
    }

    private Object readResolve() {
        if (secretPassword == null)
            // backward compatibility : get scrambled password and store it encrypted
            secretPassword = Secret.fromString(Scrambler.descramble(password));
        password = null;
        authenticator = newAuthenticator();
        return this;
    }

    public static XmlFile getXmlFile() {
        return new XmlFile(XSTREAM, new File(Jenkins.get().getRootDir(), "proxy.xml"));
    }

    public static ProxyConfiguration load() throws IOException {
        XmlFile f = getXmlFile();
        if(f.exists())
            return (ProxyConfiguration) f.read();
        else
            return null;
    }

    /**
     * This method should be used wherever {@link URL#openConnection()} to internet URLs is invoked directly.
     */
    public static URLConnection open(URL url) throws IOException {
        final ProxyConfiguration p = get();
        
        URLConnection con;
        if(p==null) {
            con = url.openConnection();
        } else {
            Proxy proxy = p.createProxy(url.getHost());
            con = url.openConnection(proxy);
            if(p.getUserName()!=null) {
                // Add an authenticator which provides the credentials for proxy authentication
                Authenticator.setDefault(p.authenticator);
                p.jenkins48775workaround(proxy, url);
            }
        }
        
        if(DEFAULT_CONNECT_TIMEOUT_MILLIS > 0) {
            con.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MILLIS);
        }
        
        if (JenkinsJVM.isJenkinsJVM()) { // this code may run on an agent
            decorate(con);
        }

        return con;
    }

    public static InputStream getInputStream(URL url) throws IOException {
        final ProxyConfiguration p = get();
        if (p == null)
            return new RetryableHttpStream(url);

        Proxy proxy = p.createProxy(url.getHost());
        InputStream is = new RetryableHttpStream(url, proxy);
        if (p.getUserName() != null) {
            // Add an authenticator which provides the credentials for proxy authentication
            Authenticator.setDefault(p.authenticator);
            p.jenkins48775workaround(proxy, url);
        }

        return is;
    }

    /**
     * If the first URL we try to access with a HTTP proxy is HTTPS then the authentication cache will not have been
     * pre-populated, so we try to access at least one HTTP URL before the very first HTTPS url.
     * @param proxy
     * @param url the actual URL being opened.
     */
    private void jenkins48775workaround(Proxy proxy, URL url) {
        if ("https".equals(url.getProtocol()) && !authCacheSeeded && proxy != Proxy.NO_PROXY) {
            HttpURLConnection preAuth = null;
            try {
                // We do not care if there is anything at this URL, all we care is that it is using the proxy
                preAuth = (HttpURLConnection) new URL("http", url.getHost(), -1, "/").openConnection(proxy);
                preAuth.setRequestMethod("HEAD");
                preAuth.connect();
            } catch (IOException e) {
                // ignore, this is just a probe we don't care at all
            } finally {
                if (preAuth != null) {
                    preAuth.disconnect();
                }
            }
            authCacheSeeded = true;
        } else if ("https".equals(url.getProtocol())){
            // if we access any http url using a proxy then the auth cache will have been seeded
            authCacheSeeded = authCacheSeeded || proxy != Proxy.NO_PROXY;
        }
    }

    @CheckForNull
    private static ProxyConfiguration get() {
        if (JenkinsJVM.isJenkinsJVM()) {
            return _get();
        }
        return null;
    }

    @CheckForNull
    private static ProxyConfiguration _get() {
        JenkinsJVM.checkJenkinsJVM();
        // this code could be called between the JVM flag being set and theInstance initialized
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        return jenkins == null ? null : jenkins.proxy;
    }

    private static void decorate(URLConnection con) throws IOException {
        for (URLConnectionDecorator d : URLConnectionDecorator.all())
            d.decorate(con);
    }

    private static final XStream XSTREAM = new XStream2();

    private static final long serialVersionUID = 1L;

    static {
        XSTREAM.alias("proxy", ProxyConfiguration.class);
    }

    @Extension @Symbol("proxy")
    public static class DescriptorImpl extends Descriptor<ProxyConfiguration> {
        @Override
        public String getDisplayName() {
            return "Proxy Configuration";
        }

        public FormValidation doCheckPort(@QueryParameter String value) {
            value = Util.fixEmptyAndTrim(value);
            if (value == null) {
                return FormValidation.ok();
            }
            int port;
            try {
                port = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return FormValidation.error(Messages.PluginManager_PortNotANumber());
            }
            if (port < 0 || port > 65535) {
                return FormValidation.error(Messages.PluginManager_PortNotInRange(0, 65535));
            }
            return FormValidation.ok();
        }

        @RequirePOST
        @Restricted(NoExternalUse.class)
        public FormValidation doValidateProxy(
                @QueryParameter("testUrl") String testUrl, @QueryParameter("name") String name, @QueryParameter("port") int port,
                @QueryParameter("userName") String userName, @QueryParameter("secretPassword") Secret password,
                @QueryParameter("noProxyHost") String noProxyHost) {

            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            if (Util.fixEmptyAndTrim(testUrl) == null) {
                return FormValidation.error(Messages.ProxyConfiguration_TestUrlRequired());
            }

            String host;
            try {
                URL url = new URL(testUrl);
                host = url.getHost();
            } catch (MalformedURLException e) {
                return FormValidation.error(Messages.ProxyConfiguration_MalformedTestUrl(testUrl));
            }

            GetMethod method = null;
            try {
                method = new GetMethod(testUrl);
                method.getParams().setParameter("http.socket.timeout", DEFAULT_CONNECT_TIMEOUT_MILLIS > 0 ? DEFAULT_CONNECT_TIMEOUT_MILLIS : (int)TimeUnit.SECONDS.toMillis(30));
                
                HttpClient client = new HttpClient();
                if (Util.fixEmptyAndTrim(name) != null && !isNoProxyHost(host, noProxyHost)) {
                    client.getHostConfiguration().setProxy(name, port);
                    Credentials credentials = createCredentials(userName, password != null ? password.getPlainText() : null);
                    AuthScope scope = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT);
                    client.getState().setProxyCredentials(scope, credentials);
                }
                
                int code = client.executeMethod(method);
                if (code != HttpURLConnection.HTTP_OK) {
                    return FormValidation.error(Messages.ProxyConfiguration_FailedToConnect(testUrl, code));
                }
            } catch (IOException e) {
                return FormValidation.error(e, Messages.ProxyConfiguration_FailedToConnectViaProxy(testUrl));
            } finally {
                if (method != null) {
                    method.releaseConnection();
                }
            }
            
            return FormValidation.ok(Messages.ProxyConfiguration_Success());
        }

        private boolean isNoProxyHost(String host, String noProxyHost) {
            if (host!=null && noProxyHost!=null) {
                for (Pattern p : getNoProxyHostPatterns(noProxyHost)) {
                    if (p.matcher(host).matches()) {
                        return true;
                    }
                }
            }
            return false;
        }

        private Credentials createCredentials(String userName, String password) {
            if (userName.indexOf('\\') >= 0){
                final String domain = userName.substring(0, userName.indexOf('\\'));
                final String user = userName.substring(userName.indexOf('\\') + 1);
                return new NTCredentials(user, Secret.fromString(password).getPlainText(), "", domain);
            } else {
                return new UsernamePasswordCredentials(userName, Secret.fromString(password).getPlainText());
            }
        }
    }
}
