package hudson;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.JellyException;
import org.apache.commons.jelly.expression.Expression;
import org.apache.commons.jelly.expression.ExpressionFactory;
import org.apache.commons.jelly.expression.ExpressionSupport;
import org.apache.commons.jexl.JexlContext;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.springframework.security.access.AccessDeniedException;

/**
 * {@link ExpressionFactory} so that security exception aborts the page rendering.
 *
 * @author Kohsuke Kawaguchi
*/
final class ExpressionFactory2 implements ExpressionFactory {
    @Override
    public Expression createExpression(String text) throws JellyException {
        try {
            return new JexlExpression(
                org.apache.commons.jexl.ExpressionFactory.createExpression(text)
            );
        } catch (Exception e) {
            throw new JellyException("Unable to create expression: " + text, e);
        }
    }

    /*
     * Copyright 2002,2004 The Apache Software Foundation.
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *      http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
    static final class JexlExpression extends ExpressionSupport {

        /** The Jexl expression object */
        private org.apache.commons.jexl.Expression expression;

        JexlExpression(org.apache.commons.jexl.Expression expression) {
            this.expression = expression;
        }

        @Override
        public String toString() {
            return super.toString() + "[" + expression.getExpression() + "]";
        }

        // Expression interface
        //-------------------------------------------------------------------------
        @Override
        public String getExpressionText() {
            return "${" + expression.getExpression() + "}";
        }

        @Override
        public Object evaluate(JellyContext context) {
            try {
                CURRENT_CONTEXT.set(context);
                JexlContext jexlContext = new JellyJexlContext(context);
                return expression.evaluate(jexlContext);
            } catch (AccessDeniedException e) {
                // let the security exception pass through
                throw e;
            } catch (Exception e) {
                StaplerRequest2 currentRequest = Stapler.getCurrentRequest2();
                LOGGER.log(Level.WARNING, "Caught exception evaluating: " + expression + " in " + (currentRequest != null ? currentRequest.getOriginalRequestURI() : "?") + ". Reason: " + e, e);
                return null;
            } finally {
                CURRENT_CONTEXT.set(null);
            }
        }

        private static final Logger LOGGER = Logger.getLogger(JexlExpression.class.getName());
    }

    static final class JellyJexlContext implements JexlContext {
        private Map vars;

        JellyJexlContext(JellyContext context) {
            this.vars = new JellyMap(context);
        }

        @Override
        public void setVars(Map vars) {
            this.vars.clear();
            this.vars.putAll(vars);
        }

        @Override
        public Map getVars() {
            return this.vars;
        }
    }


    static final class JellyMap implements Map {

        private JellyContext context;

        JellyMap(JellyContext context) {
            this.context = context;
        }

        @Override
        public Object get(Object key) {
            return context.getVariable((String) key);
        }

        @Override
        public void clear() {
            // not implemented
        }

        @Override
        public boolean containsKey(Object key) {
            return get(key) != null;
        }

        @Override
        public boolean containsValue(Object value) {
            return false;
        }

        @Override
        public Set entrySet() {
            return null;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public Set keySet() {
            return null;
        }

        @Override
        public Object put(Object key, Object value) {
            return null;
        }

        @Override
        public void putAll(Map t) {
            // not implemented
        }

        @Override
        public Object remove(Object key) {
            return null;
        }

        @Override
        public int size() {
            return -1;
        }

        @Override
        public Collection values() {
            return null;
        }
    }

    /**
     * When called from within the JEXL expression evaluation,
     * retains the current {@link JellyContext}.
     *
     * @see Functions#getCurrentJellyContext()
     */
    protected static final ThreadLocal<JellyContext> CURRENT_CONTEXT = new ThreadLocal<>();
}
