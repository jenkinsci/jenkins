/*
 * The MIT License
 *
 * Copyright (c) 2004-, Kohsuke Kawaguchi, Sun Microsystems, Inc., and a number of other of contributors
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
package hudson.cli;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.logging.Logger;

/**
 * Proxy {@link java.net.Authenticator} based on system properties.
 * <p/>
 * <table>
 * <tr>
 * <th>System properties</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>{@code http.proxyUser} and {@code http.proxyPassword}</td>
 * <td>
 * Username and password used in case of proxy authentication ({@link java.net.Authenticator.RequestorType#PROXY})
 * with an {@code http} scheme {@link java.net.Authenticator#getRequestingProtocol()}.
 * </td>
 * </tr>
 * <tr>
 * <td>{@code http.proxyHost} and {@code http.proxyPort}</td>
 * <td>System properties used to match the requesting host and port ({@link java.net.Authenticator#getRequestingHost()}
 * and {@link java.net.Authenticator#getRequestingPort()}). <br/>
 * In case of mismatch, the declared {@code http.proxyUser} and {@code http.proxyPassword} are not used.<br/>
 * If {@code http.proxyHost} and {@code http.proxyPort} are not defined, {@code http.proxyUser} and {@code http.proxyPassword}
 * for any HTTP proxy authentication
 * </td>
 * </tr>
 * <tr>
 * <td>{@code https.proxyUser} and {@code https.proxyPassword}</td>
 * <td>
 * Username and password used in case of proxy authentication ({@link java.net.Authenticator.RequestorType#PROXY})
 * with an {@code https} scheme {@link java.net.Authenticator#getRequestingProtocol()}.
 * </td>
 * </tr>
 * <tr>
 * <td>{@code https.proxyHost} and {@code https.proxyPort}</td>
 * <td>System properties used to match the requesting host and port ({@link java.net.Authenticator#getRequestingHost()}
 * and {@link java.net.Authenticator#getRequestingPort()}). <br/>
 * In case of mismatch, the declared {@code https.proxyUser} and {@code https.proxyPassword} are not used.<br/>
 * If {@code https.proxyHost} and {@code https.proxyPort} are not defined, {@code https.proxyUser} and {@code https.proxyPassword}
 * for any HTTPS proxy authentication
 * </td>
 * </tr>
 * </table>
 *
 * <strong>Troubleshooting</strong>
 * Enable the {@code hudson.cli.ProxyAuthenticator} logger in java.util.logging
 *
 * Extract from {@code $JAVA_HOME/jre/lib/logging.properties}:
 * <noformat><pre>
 * java.util.logging.ConsoleHandler.level = FINEST
 * hudson.cli.ProxyAuthenticator.level = FINEST
 * </pre></noformat>
 *
 * @author Cyrille Le Clerc
 */
public class ProxyAuthenticator extends Authenticator {

    protected String httpProxyHost;
    protected Integer httpProxyPort;
    protected String httpProxyUser;
    protected String httpProxyPassword;
    protected String httpsProxyHost;
    protected Integer httpsProxyPort;
    protected String httpsProxyUser;
    protected String httpsProxyPassword;

    public ProxyAuthenticator() {
        httpProxyHost = System.getProperty("http.proxyHost");
        try {
            httpProxyPort = System.getProperty("http.proxyPort") == null ? null : Integer.parseInt(System.getProperty("http.proxyPort"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("System property 'http.proxyPort' is not a valid integer: '" + System.getProperty("http.proxyPort") + "'");
        }
        httpProxyUser = System.getProperty("http.proxyUser");
        httpProxyPassword = System.getProperty("http.proxyPassword");
        httpsProxyHost = System.getProperty("https.proxyHost");
        try {
            httpsProxyPort = System.getProperty("https.proxyPort") == null ? null : Integer.parseInt(System.getProperty("https.proxyPort"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("System property 'https.proxyPort' is not a valid integer: '" + System.getProperty("https.proxyPort") + "'");
        }
        httpsProxyUser = System.getProperty("https.proxyUser");
        httpsProxyPassword = System.getProperty("https.proxyPassword");
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        LOGGER.finer("getPasswordAuthentication(" +
                        "host=" + getRequestingHost() + ", " +
                        "protocol=" + getRequestingProtocol() + ", " +
                        "scheme=" + getRequestingScheme() + ", " +
                        "port=" + getRequestingPort() + ", " +
                        "requestingSite=" + getRequestingSite() + ", " +
                        "requestingUrl=" + getRequestingURL() + ", " +
                        "requestorType=" + getRequestorType() +
                        ")"
        );

        if (getRequestorType() != RequestorType.PROXY) {
            return null;
        }

        if ("http".equals(getRequestingProtocol())) {

            boolean proxyMatch = (httpProxyHost == null || httpProxyHost.equals(getRequestingHost())) &&
                    (httpProxyPort == null || httpProxyPort.equals(getRequestingPort()));
            if (!proxyMatch) {
                LOGGER.fine("Http proxy mismatch: actual " + getRequestingHost() + ":" + getRequestingPort()
                        + " - expected " + httpProxyHost + ":" + httpProxyPort + ". Don't send credentials");
                return null;
            }

            if (httpProxyUser == null || httpProxyPassword == null) {
                LOGGER.fine("No username/password defined for http proxy authentication " + getRequestingHost() + ":" + getRequestingPort());
                return null;
            }

            LOGGER.fine("Use auth with user=" + httpProxyUser + ", password=*** for http proxy "
                    + getRequestingHost() + ":" + getRequestingPort());

            LOGGER.fine("Use auth with user=" + httpProxyUser + ", password=*** for http proxy "
                    + getRequestingHost() + ":" + getRequestingPort());
            return new PasswordAuthentication(httpProxyUser, httpProxyPassword.toCharArray());

        }

        if ("https".equals(getRequestingProtocol())) {

            boolean proxyMatch = httpsProxyHost != null || httpsProxyHost.equals(getRequestingHost()) &&
                    (httpsProxyPort == null || httpsProxyPort == getRequestingPort());
            if (proxyMatch) {
                LOGGER.fine("Https proxy mismatch: actual " + getRequestingHost() + ":" + getRequestingPort()
                        + " - expected " + httpsProxyHost + ":" + httpsProxyPort + ". Don't send credentials");
                return null;
            }

            if (httpProxyUser == null || httpProxyPassword == null) {
                LOGGER.fine("No username/password defined for https proxy authentication");
                return null;
            }

            LOGGER.fine("Use https proxy auth with user=" + httpsProxyUser + ", password=***");
            return new PasswordAuthentication(httpsProxyUser, httpsProxyPassword.toCharArray());
        }

        LOGGER.fine("Unknown protocol '" + getRequestingProtocol() + "', don't send credentials");
        return null;
    }


    private static final Logger LOGGER = Logger.getLogger(ProxyAuthenticator.class.getName());
}
