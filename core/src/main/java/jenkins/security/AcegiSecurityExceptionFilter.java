/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc.
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

package jenkins.security;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.security.UnwrapSecurityExceptionFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.function.Function;
import org.acegisecurity.AcegiSecurityException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.web.access.ExceptionTranslationFilter;

/**
 * Translates {@link AcegiSecurityException}s to Spring Security equivalents.
 * Used by other filters like {@link UnwrapSecurityExceptionFilter} and {@link ExceptionTranslationFilter}.
 */
@Restricted(NoExternalUse.class)
public class AcegiSecurityExceptionFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (IOException x) {
            throw translate(x, IOException::new);
        } catch (ServletException x) {
            throw translate(x, ServletException::new);
        } catch (RuntimeException x) {
            throw translate(x, RuntimeException::new);
        }
    }

    private static <T extends Throwable> T translate(T t, Function<Throwable, T> ctor) {
        RuntimeException cause = convertedCause(t);
        if (cause != null) {
            T t2 = ctor.apply(cause);
            t2.addSuppressed(t);
            return t2;
        } else {
            return t;
        }
    }

    private static @CheckForNull RuntimeException convertedCause(@CheckForNull Throwable t) {
        if (t instanceof AcegiSecurityException) {
            return ((AcegiSecurityException) t).toSpring();
        } else if (t != null) {
            return convertedCause(t.getCause());
        } else {
            return null;
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void destroy() {}

}
