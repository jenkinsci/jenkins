package jenkins;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * {@link Filter} that sets up the context classloader.
 *
 * @author Kohsuke Kawaguchi
 */
public class ContextClasssLoaderFilter implements Filter {
    private final ClassLoader classLoader;

    public ContextClasssLoaderFilter(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public ContextClasssLoaderFilter() {
        this(Thread.currentThread().getContextClassLoader());
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse rsp, FilterChain chain) throws IOException, ServletException {
        Thread t = Thread.currentThread();
        ClassLoader cl = t.getContextClassLoader();
        t.setContextClassLoader(classLoader);
        try {
            chain.doFilter(req, rsp);
        } finally {
            t.setContextClassLoader(cl);
        }
    }

    @Override
    public void destroy() {
    }
}
