/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package jenkins.security.stapler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.Header;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.kohsuke.stapler.json.JsonBody;
import org.kohsuke.stapler.json.SubmittedForm;

@Restricted(NoExternalUse.class)
final class WebMethodConstants {
    /**
     * If a method has at least one of those parameters, it is considered as an implicit web method
     */
    private static final List<Class<?>> WEB_METHOD_PARAMETERS = List.of(
            StaplerRequest2.class,
            StaplerRequest.class,
            HttpServletRequest.class,
            javax.servlet.http.HttpServletRequest.class,
            StaplerResponse2.class,
            StaplerResponse.class,
            HttpServletResponse.class,
            javax.servlet.http.HttpServletResponse.class
    );

    static final Set<String> WEB_METHOD_PARAMETERS_NAMES = Collections.unmodifiableSet(
            WEB_METHOD_PARAMETERS.stream()
                    .map(Class::getName)
                    .collect(Collectors.toSet())
    );

    /**
     * If a method is annotated with one of those annotations,
     * the method is considered as an explicit web method
     */
    static final List<Class<? extends Annotation>> WEB_METHOD_ANNOTATIONS = List.of(
            WebMethod.class
            // plus every annotation that's annotated with InterceptorAnnotation
            // JavaScriptMethod.class not taken here because it's a special case
    );

    static final Set<String> WEB_METHOD_ANNOTATION_NAMES;

    static {
        Set<String> webMethodAnnotationNames = WEB_METHOD_ANNOTATIONS.stream()
                .map(Class::getName)
                .collect(Collectors.toSet());
        webMethodAnnotationNames.add(JavaScriptMethod.class.getName());
        WEB_METHOD_ANNOTATION_NAMES = Collections.unmodifiableSet(webMethodAnnotationNames);
    }

    /**
     * If at least one parameter of the method is annotated with one of those annotations,
     * the method is considered as an implicit web method
     */
    private static final List<Class<? extends Annotation>> WEB_METHOD_PARAMETER_ANNOTATIONS = List.of(
            QueryParameter.class,
            AncestorInPath.class,
            Header.class,
            JsonBody.class,
            SubmittedForm.class
    );

    static final Set<String> WEB_METHOD_PARAMETER_ANNOTATION_NAMES = Collections.unmodifiableSet(
            WEB_METHOD_PARAMETER_ANNOTATIONS.stream()
                    .map(Class::getName)
                    .collect(Collectors.toSet())
    );
}
