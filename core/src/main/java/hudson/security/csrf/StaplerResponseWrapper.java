package hudson.security.csrf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URL;
import java.util.Collection;
import java.util.Locale;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Flavor;

import net.sf.json.JsonConfig;

/**
 * A basic wrapper for a StaplerResponse, e.g. in order to override some method.
 * This simply delegates to the wrapped instance for everything and is not useful
 * by itself.
 * 
 * FIXME: move to Stapler core
 * 
 * @since TODO
 */
@SuppressWarnings("deprecation")
public abstract class StaplerResponseWrapper implements StaplerResponse {
    private final StaplerResponse wrapped;
    
    public StaplerResponseWrapper(StaplerResponse wrapped) {
        this.wrapped = wrapped;
    }
    
    /**
     * Returns the wrapped instance
     */
    public StaplerResponse getWrapped() {
        return wrapped;
    }

    /** {@inheritDoc} */
    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return getWrapped().getOutputStream();
    }

    /** {@inheritDoc} */
    @Override
    public PrintWriter getWriter() throws IOException {
        return getWrapped().getWriter();
    }

    /** {@inheritDoc} */
    @Override
    public void forward(Object it, String url, StaplerRequest request) throws ServletException, IOException {
        getWrapped().forward(it, url, request);
    }

    /** {@inheritDoc} */
    @Override
    public void forwardToPreviousPage(StaplerRequest request) throws ServletException, IOException {
        getWrapped().forwardToPreviousPage(request);
    }

    /** {@inheritDoc} */
    @Override
    public void sendRedirect(String url) throws IOException {
        getWrapped().sendRedirect(url);
    }

    /** {@inheritDoc} */
    @Override
    public void sendRedirect2(String url) throws IOException {
        getWrapped().sendRedirect2(url);
    }

    /** {@inheritDoc} */
    @Override
    public void sendRedirect(int statusCode, String url) throws IOException {
        getWrapped().sendRedirect(statusCode, url);
    }

    /** {@inheritDoc} */
    @Override
    public void serveFile(StaplerRequest req, URL resource, long expiration) throws ServletException, IOException {
        getWrapped().serveFile(req, resource, expiration);
    }

    /** {@inheritDoc} */
    @Override
    public void serveFile(StaplerRequest req, URL resource) throws ServletException, IOException {
        getWrapped().serveFile(req, resource);
    }

    /** {@inheritDoc} */
    @Override
    public void serveLocalizedFile(StaplerRequest request, URL res) throws ServletException, IOException {
        getWrapped().serveLocalizedFile(request, res);
    }

    /** {@inheritDoc} */
    @Override
    public void serveLocalizedFile(StaplerRequest request, URL res, long expiration)
            throws ServletException, IOException {
        getWrapped().serveLocalizedFile(request, res, expiration);
    }

    /** {@inheritDoc} */
    @Override
    public void serveFile(StaplerRequest req, InputStream data, long lastModified, long expiration,
            long contentLength, String fileName) throws ServletException, IOException {
        getWrapped().serveFile(req, data, lastModified, expiration, contentLength, fileName);
    }

    /** {@inheritDoc} */
    @Override
    public void serveFile(StaplerRequest req, InputStream data, long lastModified, long expiration,
            int contentLength, String fileName) throws ServletException, IOException {
        getWrapped().serveFile(req, data, lastModified, expiration, contentLength, fileName);
    }

    /** {@inheritDoc} */
    @Override
    public void serveFile(StaplerRequest req, InputStream data, long lastModified, long contentLength,
            String fileName) throws ServletException, IOException {
        getWrapped().serveFile(req, data, lastModified, contentLength, fileName);
    }

    /** {@inheritDoc} */
    @Override
    public void serveFile(StaplerRequest req, InputStream data, long lastModified, int contentLength,
            String fileName) throws ServletException, IOException {
        getWrapped().serveFile(req, data, lastModified, contentLength, fileName);
    }

    /** {@inheritDoc} */
    @Override
    public void serveExposedBean(StaplerRequest req, Object exposedBean, Flavor flavor)
            throws ServletException, IOException {
        getWrapped().serveExposedBean(req, exposedBean, flavor);
    }

    /** {@inheritDoc} */
    @Override
    public OutputStream getCompressedOutputStream(HttpServletRequest req) throws IOException {
        return getWrapped().getCompressedOutputStream(req);
    }

    /** {@inheritDoc} */
    @Override
    public Writer getCompressedWriter(HttpServletRequest req) throws IOException {
        return getWrapped().getCompressedWriter(req);
    }

    /** {@inheritDoc} */
    @Override
    public int reverseProxyTo(URL url, StaplerRequest req) throws IOException {
        return getWrapped().reverseProxyTo(url, req);
    }

    /** {@inheritDoc} */
    @Override
    public void setJsonConfig(JsonConfig config) {
        getWrapped().setJsonConfig(config);
    }

    /** {@inheritDoc} */
    @Override
    public JsonConfig getJsonConfig() {
        return getWrapped().getJsonConfig();
    }

    /** {@inheritDoc} */
    @Override
    public void addCookie(Cookie cookie) {
        getWrapped().addCookie(cookie);
    }

    /** {@inheritDoc} */
    @Override
    public void addDateHeader(String name, long date) {
        getWrapped().addDateHeader(name, date);
    }

    /** {@inheritDoc} */
    @Override
    public void addHeader(String name, String value) {
        getWrapped().addHeader(name, value);
    }

    /** {@inheritDoc} */
    @Override
    public void addIntHeader(String name, int value) {
        getWrapped().addIntHeader(name, value);
    }

    /** {@inheritDoc} */
    @Override
    public boolean containsHeader(String name) {
        return getWrapped().containsHeader(name);
    }

    /** {@inheritDoc} */
    @Override
    public String encodeRedirectURL(String url) {
        return getWrapped().encodeRedirectURL(url);
    }

    /** {@inheritDoc} */
    @Override
    public String encodeRedirectUrl(String url) {
        return getWrapped().encodeRedirectUrl(url);
    }

    /** {@inheritDoc} */
    @Override
    public String encodeURL(String url) {
        return getWrapped().encodeURL(url);
    }

    /** {@inheritDoc} */
    @Override
    public String encodeUrl(String url) {
        return getWrapped().encodeUrl(url);
    }

    /** {@inheritDoc} */
    @Override
    public String getHeader(String name) {
        return getWrapped().getHeader(name);
    }

    /** {@inheritDoc} */
    @Override
    public Collection<String> getHeaderNames() {
        return getWrapped().getHeaderNames();
    }

    /** {@inheritDoc} */
    @Override
    public Collection<String> getHeaders(String name) {
        return getWrapped().getHeaders(name);
    }

    /** {@inheritDoc} */
    @Override
    public int getStatus() {
        return getWrapped().getStatus();
    }

    /** {@inheritDoc} */
    @Override
    public void sendError(int sc) throws IOException {
        getWrapped().sendError(sc);
    }

    /** {@inheritDoc} */
    @Override
    public void sendError(int sc, String msg) throws IOException {
        getWrapped().sendError(sc, msg);
    }

    /** {@inheritDoc} */
    @Override
    public void setDateHeader(String name, long date) {
        getWrapped().setDateHeader(name, date);
    }

    /** {@inheritDoc} */
    @Override
    public void setHeader(String name, String value) {
        getWrapped().setHeader(name, value);
    }

    /** {@inheritDoc} */
    @Override
    public void setIntHeader(String name, int value) {
        getWrapped().setIntHeader(name, value);
    }

    /** {@inheritDoc} */
    @Override
    public void setStatus(int sc) {
        getWrapped().setStatus(sc);
    }

    /** {@inheritDoc} */
    @Override
    public void setStatus(int sc, String sm) {
        getWrapped().setStatus(sc, sm);
    }

    /** {@inheritDoc} */
    @Override
    public void flushBuffer() throws IOException {
        getWrapped().flushBuffer();
    }

    /** {@inheritDoc} */
    @Override
    public int getBufferSize() {
        return getWrapped().getBufferSize();
    }

    /** {@inheritDoc} */
    @Override
    public String getCharacterEncoding() {
        return getWrapped().getCharacterEncoding();
    }

    /** {@inheritDoc} */
    @Override
    public String getContentType() {
        return getWrapped().getContentType();
    }

    /** {@inheritDoc} */
    @Override
    public Locale getLocale() {
        return getWrapped().getLocale();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCommitted() {
        return getWrapped().isCommitted();
    }

    /** {@inheritDoc} */
    @Override
    public void reset() {
        getWrapped().reset();
    }

    /** {@inheritDoc} */
    @Override
    public void resetBuffer() {
        getWrapped().resetBuffer();
    }

    /** {@inheritDoc} */
    @Override
    public void setBufferSize(int size) {
        getWrapped().setBufferSize(size);
    }

    /** {@inheritDoc} */
    @Override
    public void setCharacterEncoding(String charset) {
        getWrapped().setCharacterEncoding(charset);
    }

    /** {@inheritDoc} */
    @Override
    public void setContentLength(int len) {
        getWrapped().setContentLength(len);
    }

    /** {@inheritDoc} */
    @Override
    public void setContentLengthLong(long len) {
        getWrapped().setContentLengthLong(len);
    }

    /** {@inheritDoc} */
    @Override
    public void setContentType(String type) {
        getWrapped().setContentType(type);
    }

    /** {@inheritDoc} */
    @Override
    public void setLocale(Locale loc) {
        getWrapped().setLocale(loc);
    }
}
