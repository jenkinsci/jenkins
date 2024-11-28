package jenkins.security.stapler;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.ExtensionList;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Function;
import org.kohsuke.stapler.FunctionList;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerOverridable;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.interceptor.InterceptorAnnotation;
import org.kohsuke.stapler.lang.FieldRef;

@Restricted(NoExternalUse.class)
public class TypedFilter implements FieldRef.Filter, FunctionList.Filter {
    private static final Logger LOGGER = Logger.getLogger(TypedFilter.class.getName());

    private boolean isClassAcceptable(Class<?> clazz) {
        if (clazz.isArray()) {
            // special case to allow klass.isArray() dispatcher
            Class<?> elementClazz = clazz.getComponentType();
            // does not seem possible to fall in an infinite loop since array cannot be recursively defined
            if (isClassAcceptable(elementClazz)) {
                LOGGER.log(Level.FINE,
                        "Class {0} is acceptable because it is an Array of acceptable elements {1}",
                        new Object[]{clazz.getName(), elementClazz.getName()}
                );
                return true;
            } else {
                LOGGER.log(Level.FINE,
                        "Class {0} is not acceptable because it is an Array of non-acceptable elements {1}",
                        new Object[]{clazz.getName(), elementClazz.getName()}
                );
                return false;
            }
        }
        return SKIP_TYPE_CHECK || isStaplerRelevant.get(clazz);
    }

    private static final ClassValue<Boolean> isStaplerRelevant = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> clazz) {
            return isSpecificClassStaplerRelevant(clazz) || isSuperTypesStaplerRelevant(clazz);
        }
    };

    private static boolean isSuperTypesStaplerRelevant(@NonNull Class<?> clazz) {
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && isStaplerRelevant.get(superclass)) {
            return true;
        }
        for (Class<?> interfaceClass : clazz.getInterfaces()) {
            if (isStaplerRelevant.get(interfaceClass)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSpecificClassStaplerRelevant(@NonNull Class<?> clazz) {
        if (clazz.isAnnotationPresent(StaplerAccessibleType.class)) {
            return true;
        }

        // Classes implementing these Stapler types can be considered routable
        if (StaplerProxy.class.isAssignableFrom(clazz)) {
            return true;
        }
        if (StaplerFallback.class.isAssignableFrom(clazz)) {
            return true;
        }
        if (StaplerOverridable.class.isAssignableFrom(clazz)) {
            return true;
        }

        for (Method m : clazz.getMethods()) {
            if (isRoutableMethod(m)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isRoutableMethod(@NonNull Method m) {
        for (Annotation a : m.getDeclaredAnnotations()) {
            if (WebMethodConstants.WEB_METHOD_ANNOTATION_NAMES.contains(a.annotationType().getName())) {
                return true;
            }
            if (a.annotationType().isAnnotationPresent(InterceptorAnnotation.class)) {
                // This is a Stapler interceptor annotation like RequirePOST or JsonResponse
                return true;
            }
        }

        for (Annotation[] set : m.getParameterAnnotations()) {
            for (Annotation a : set) {
                if (WebMethodConstants.WEB_METHOD_PARAMETER_ANNOTATION_NAMES.contains(a.annotationType().getName())) {
                    return true;
                }
            }
        }

        for (Class<?> parameterType : m.getParameterTypes()) {
            if (WebMethodConstants.WEB_METHOD_PARAMETERS_NAMES.contains(parameterType.getName())) {
                return true;
            }
        }

        return WebApp.getCurrent().getFilterForDoActions().keep(new Function.InstanceFunction(m));
    }

    @Override
    public boolean keep(@NonNull FieldRef fieldRef) {

        if (fieldRef.getAnnotation(StaplerNotDispatchable.class) != null) {
            // explicitly marked as an invalid field
            return false;
        }

        if (fieldRef.getAnnotation(StaplerDispatchable.class) != null) {
            // explicitly marked as a valid field
            return true;
        }

        String signature = fieldRef.getSignature();

        // check whitelist
        ExtensionList<RoutingDecisionProvider> decisionProviders = ExtensionList.lookup(RoutingDecisionProvider.class);
        if (!decisionProviders.isEmpty()) {
            for (RoutingDecisionProvider provider : decisionProviders) {
                RoutingDecisionProvider.Decision fieldDecision = provider.decide(signature);
                if (fieldDecision == RoutingDecisionProvider.Decision.ACCEPTED) {
                    LOGGER.log(Level.CONFIG, "Field {0} is acceptable because it is whitelisted by {1}", new Object[]{signature, provider});
                    return true;
                }
                if (fieldDecision == RoutingDecisionProvider.Decision.REJECTED) {
                    LOGGER.log(Level.CONFIG, "Field {0} is not acceptable because it is blacklisted by {1}", new Object[]{signature, provider});
                    return false;
                }
                Class<?> type = fieldRef.getReturnType();
                if (type != null) {
                    String typeSignature = "class " + type.getCanonicalName();
                    RoutingDecisionProvider.Decision fieldTypeDecision = provider.decide(typeSignature);
                    if (fieldTypeDecision == RoutingDecisionProvider.Decision.ACCEPTED) {
                        LOGGER.log(Level.CONFIG, "Field {0} is acceptable because its type is whitelisted by {1}", new Object[]{signature, provider});
                        return true;
                    }
                    if (fieldTypeDecision == RoutingDecisionProvider.Decision.REJECTED) {
                        LOGGER.log(Level.CONFIG, "Field {0} is not acceptable because its type is blacklisted by {1}", new Object[]{signature, provider});
                        return false;
                    }
                }
            }
        }

        if (PROHIBIT_STATIC_ACCESS && fieldRef.isStatic()) {
            // unless whitelisted or marked as routable, reject static fields
            return false;
        }


        Class<?> returnType = fieldRef.getReturnType();

        boolean isOk = isClassAcceptable(returnType);
        LOGGER.log(Level.FINE, "Field analyzed: {0} => {1}", new Object[]{fieldRef.getName(), isOk});
        return isOk;
    }

    @Override
    public boolean keep(@NonNull Function function) {

        if (function.getAnnotation(StaplerNotDispatchable.class) != null) {
            // explicitly marked as an invalid getter
            return false;
        }

        if (function.getAnnotation(StaplerDispatchable.class) != null) {
            // explicitly marked as a valid getter
            return true;
        }

        String signature = function.getSignature();

        // check whitelist
        ExtensionList<RoutingDecisionProvider> decision = ExtensionList.lookup(RoutingDecisionProvider.class);
        if (!decision.isEmpty()) {
            for (RoutingDecisionProvider provider : decision) {
                RoutingDecisionProvider.Decision methodDecision = provider.decide(signature);
                if (methodDecision == RoutingDecisionProvider.Decision.ACCEPTED) {
                    LOGGER.log(Level.CONFIG, "Function {0} is acceptable because it is whitelisted by {1}", new Object[]{signature, provider});
                    return true;
                }
                if (methodDecision == RoutingDecisionProvider.Decision.REJECTED) {
                    LOGGER.log(Level.CONFIG, "Function {0} is not acceptable because it is blacklisted by {1}", new Object[]{signature, provider});
                    return false;
                }

                Class<?> type = function.getReturnType();
                if (type != null) {
                    String typeSignature = "class " + type.getCanonicalName();
                    RoutingDecisionProvider.Decision returnTypeDecision = provider.decide(typeSignature);
                    if (returnTypeDecision == RoutingDecisionProvider.Decision.ACCEPTED) {
                        LOGGER.log(Level.CONFIG, "Function {0} is acceptable because its type is whitelisted by {1}", new Object[]{signature, provider});
                        return true;
                    }
                    if (returnTypeDecision == RoutingDecisionProvider.Decision.REJECTED) {
                        LOGGER.log(Level.CONFIG, "Function {0} is not acceptable because its type is blacklisted by {1}", new Object[]{signature, provider});
                        return false;
                    }
                }
            }
        }

        if (PROHIBIT_STATIC_ACCESS && function.isStatic()) {
            // unless whitelisted or marked as routable, reject static methods
            return false;
        }

        if (function.getName().equals("getDynamic")) {
            Class[] parameterTypes = function.getParameterTypes();
            if (parameterTypes.length > 0 && parameterTypes[0] == String.class) {
                // While this is more general than what Stapler can invoke on these types,
                // The above is the only criterion for Stapler to attempt dispatch.
                // Therefore prohibit this as a regular getter.
                return false;
            }
        }

        if (function.getName().equals("getStaplerFallback") && function.getParameterTypes().length == 0) {
            // A parameter-less #getStaplerFallback() implements special fallback behavior for the
            // StaplerFallback interface. We do not check for the presence of the interface on the current
            // class, or the return type, as that could change since the implementing component was last built.
            return false;
        }

        if (function.getName().equals("getTarget") && function.getParameterTypes().length == 0) {
            // A parameter-less #getTarget() implements special redirection behavior for the
            // StaplerProxy interface. We do not check for the presence of the interface on the current
            // class, or the return type, as that could change since the implementing component was last built.
            return false;
        }

        Class<?> returnType = function.getReturnType();

        boolean isOk = isClassAcceptable(returnType);
        LOGGER.log(Level.FINE, "Function analyzed: {0} => {1}", new Object[]{signature, isOk});
        return isOk;
    }

    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* Script Console modifiable */ boolean SKIP_TYPE_CHECK = SystemProperties.getBoolean(TypedFilter.class.getName() + ".skipTypeCheck");

    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* Script Console modifiable */ boolean PROHIBIT_STATIC_ACCESS = SystemProperties.getBoolean(TypedFilter.class.getName() + ".prohibitStaticAccess", true);
}
