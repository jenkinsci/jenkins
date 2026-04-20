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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import java.lang.annotation.Annotation;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Function;
import org.kohsuke.stapler.FunctionList;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.interceptor.InterceptorAnnotation;

@Restricted(NoExternalUse.class)
public class DoActionFilter implements FunctionList.Filter {
    private static final Logger LOGGER = Logger.getLogger(DoActionFilter.class.getName());

    /**
     * if a method has "do" as name (not possible in pure Java but doable in Groovy or other JVM languages)
     * the new system does not consider it as a web method.
     * <p>
     * Use {@code @WebMethod(name="")} or {@code doIndex} in such case.
     */
    private static final Pattern DO_METHOD_REGEX = Pattern.compile("^do[^a-z].*");

    @Override
    public boolean keep(@NonNull Function m) {

        if (m.getAnnotation(StaplerNotDispatchable.class) != null) {
            return false;
        }

        if (m.getAnnotation(StaplerDispatchable.class) != null) {
            return true;
        }

        String methodName = m.getName();
        String signature = m.getSignature();

        // check whitelist
        ExtensionList<RoutingDecisionProvider> whitelistProviders = ExtensionList.lookup(RoutingDecisionProvider.class);
        if (!whitelistProviders.isEmpty()) {
            for (RoutingDecisionProvider provider : whitelistProviders) {
                RoutingDecisionProvider.Decision methodDecision = provider.decide(signature);
                if (methodDecision == RoutingDecisionProvider.Decision.ACCEPTED) {
                    LOGGER.log(Level.CONFIG, "Action " + signature + " is acceptable because it is whitelisted by " + provider);
                    return true;
                }
                if (methodDecision == RoutingDecisionProvider.Decision.REJECTED) {
                    LOGGER.log(Level.CONFIG, "Action " + signature + " is not acceptable because it is blacklisted by " + provider);
                    return false;
                }
            }
        }

        if (methodName.equals("doDynamic")) {
            // reject doDynamic because it's treated separately by Stapler.
            return false;
        }

        for (Annotation a : m.getAnnotations()) {
            if (WebMethodConstants.WEB_METHOD_ANNOTATION_NAMES.contains(a.annotationType().getName())) {
                return true;
            }
            if (a.annotationType().getAnnotation(InterceptorAnnotation.class) != null) {
                // This is a Stapler interceptor annotation like RequirePOST or JsonResponse
                return true;
            }
        }

        // there is rarely more than two annotations in a method signature
        for (Annotation[] perParameterAnnotation : m.getParameterAnnotations()) {
            for (Annotation annotation : perParameterAnnotation) {
                if (WebMethodConstants.WEB_METHOD_PARAMETER_ANNOTATION_NAMES.contains(annotation.annotationType().getName())) {
                    return true;
                }
            }
        }

        if (!DO_METHOD_REGEX.matcher(methodName).matches()) {
            return false;
        }

        // after the method name check to avoid allowing methods that are meant to be used by routable ones
        // normally they should be private in such case
        for (Class<?> parameterType : m.getParameterTypes()) {
            if (WebMethodConstants.WEB_METHOD_PARAMETERS_NAMES.contains(parameterType.getName())) {
                return true;
            }
        }

        Class<?> returnType = m.getReturnType();
        if (HttpResponse.class.isAssignableFrom(returnType)) {
            return true;
        }

        // as HttpResponseException inherits from RuntimeException,
        // there is no requirement for the developer to explicitly checks it.
        Class<?>[] checkedExceptionTypes = m.getCheckedExceptionTypes();
        for (Class<?> checkedExceptionType : checkedExceptionTypes) {
            if (HttpResponse.class.isAssignableFrom(checkedExceptionType)) {
                return true;
            }
        }

        return false;
    }
}
