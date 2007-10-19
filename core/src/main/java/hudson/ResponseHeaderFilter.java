package hudson;

import java.util.*;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;

/**
 * This filter allows you to modify headers set by the container or other servlets
 * that are out of your control.  The particular headers you wish to change are configured
 * in web.xml.
 * <p>
 * One particular header you you may wish to deal with is "Cache-Control: no-cache"
 * This is a problem with Tomcat when security is used.  Continue reading for further details.
 * <p>
 * If a web app has a &lt;security-constraint&gt; in its web.xml, Tomcat will
 * add a Cache-Control header to every file it serves from that location. This
 * header will prevent browsers from caching the file locally and this drastically slows
 * down Hudson page load times.
 * <p>
 * To enable this filter, edit the web.xml file to include:
 * 
 * <pre>
 * &lt;filter&gt;
 * 		&lt;filter-name&gt;change-headers-filter&lt;/filter-name&gt;
 * 		&lt;filter-class&gt;hudson.ResponseHeaderFilter&lt;/filter-class&gt;
 * 		&lt;init-param&gt;
 * 			&lt;param-name&gt;Pragma&lt;/param-name&gt;
 * 			&lt;param-value&gt;public&lt;/param-value&gt;
 * 		&lt;/init-param&gt;
 * 		&lt;init-param&gt;
 * 			&lt;param-name&gt;Cache-Control&lt;/param-name&gt;
 * 			&lt;param-value&gt;max-age=86400, public&lt;/param-value&gt;
 * 		&lt;/init-param&gt;
 * &lt;/filter&gt;
 * 
 * And down below that:
 * 
 * &lt;filter-mapping&gt;
 * 		&lt;filter-name&gt;Headers&lt;/filter-name&gt;
 * 		&lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 * &lt;/filter-mapping&gt;
 * </pre>
 * 
 * <p>
 * In the case of the tomcat cache problem, it is important that the url-pattern for 
 * the filter matches the url-pattern set for the security-constraint.
 * 
 * @author Mike Wille
 */
public class ResponseHeaderFilter implements Filter {
	private ServletContext servletContext;
	private FilterConfig config;

	public void init(FilterConfig filterConfig) throws ServletException {
		config = filterConfig;
		servletContext = config.getServletContext();
	}

	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException,
			ServletException {
		HttpServletResponse httpResp = (HttpServletResponse) resp;

		Enumeration e = config.getInitParameterNames();

		// for each configuration element...
		while(e.hasMoreElements()) {
			String headerName = (String) e.nextElement();
			String headerValue = config.getInitParameter(headerName);
			// set the header with the given name and value
			httpResp.setHeader(headerName, headerValue);
		}
		chain.doFilter(req, resp);
	}

	public void destroy() {
	}
}
