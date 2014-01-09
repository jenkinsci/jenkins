package hudson.matrix;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import hudson.Util;
import hudson.matrix.Combination.BooleanCategory;
import hudson.matrix.MatrixBuild.MatrixBuildExecution;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;

import java.util.Map;

import static java.lang.Boolean.*;

/**
 * Groovy filter script that accepts or rejects matrix {@link Combination}.
 *
 * Instances of this class is thread unsafe.
 *
 * @author Kohsuke Kawaguchi
 */
class FilterScript {
    private final Script script;

    FilterScript(Script script) {
        this.script = script;
    }

    /**
     * @param context
     *      Variables the script will see.
     */
    boolean evaluate(Binding context) {
        script.setBinding(context);
        return TRUE.equals(script.run());
    }

    /**
     * Obtains a number N such that "N%M==0" would create
     * a reasonable sparse matrix for integer M.
     *
     * <p>
     * This is bit like {@link Combination#toIndex(AxisList)}, but instead
     * of creating a continuous number (which often maps different
     * values of the same axis to the same index in modulo N residue ring,
     * we use a prime number P as the base. I think this guarantees the uniform
     * distribution in any N smaller than 2P (but proof, anyone?)
     */
    private long toModuloIndex(AxisList axis, Combination c) {
        long r = 0;
        for (Axis a : axis) {
            r += a.indexOf(c.get(a));
            r *= 31;
        }
        return r;
    }

    /**
     * Applies the filter to the specified combination in the context of {@code context}.
     */
    public boolean apply(MatrixBuildExecution context, Combination combination) {
        return apply(context.getProject().getAxes(), combination, getConfiguredBinding(context));
    }

    /*package*/ boolean apply(AxisList axes, Combination c, Binding binding) {
        for (Map.Entry<String, String> e : c.entrySet())
            binding.setVariable(e.getKey(),e.getValue());

        binding.setVariable("index",toModuloIndex(axes,c));
        binding.setVariable("uniqueId", c.toIndex(axes));

        return evaluate(binding);
    }

    private Binding getConfiguredBinding(final MatrixBuildExecution execution) {
        final Binding binding = new Binding();
        final ParametersAction parameters = execution.getBuild().getAction(ParametersAction.class);

        if (parameters == null) return binding;

        for (final ParameterValue pv: parameters) {
            if (pv == null) continue;
            final String name = pv.getName();
            final String value = pv.createVariableResolver(null).resolve(name);
            binding.setVariable(name, value);
        }

        return binding;
    }

    public static FilterScript parse(String expression) {
        return parse(expression, ACCEPT_ALL);
    }

    /**
     * @since 1.541
     */
    public static FilterScript parse(String expression, FilterScript defaultScript) {
        if (Util.fixEmptyAndTrim(expression)==null)
            return defaultScript;

        GroovyShell shell = new GroovyShell();

        return new FilterScript(shell.parse("use("+BooleanCategory.class.getName().replace('$','.')+") {"+expression+"}"));
    }

    private static final Script EMPTY = new Script() {
        @Override
        public Object run() {
            return true;
        }
    };

    /**
     * Constant that always applies to any combination.
     * @since 1.541
     */
    /*package*/ static final FilterScript ACCEPT_ALL = new FilterScript(EMPTY) {
        @Override
        public boolean apply(MatrixBuildExecution context, Combination combination) {
            return true;
        }
    };

    /**
     * Constant that does not apply to any combination.
     * @since 1.541
     */
    /*package*/ static final FilterScript REJECT_ALL = new FilterScript(EMPTY) {
        @Override
        public boolean apply(MatrixBuildExecution context, Combination combination) {
            return false;
        }
    };
}
