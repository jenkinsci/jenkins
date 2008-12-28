package org.jvnet.hudson.test.recipes;

import org.jvnet.hudson.test.HudsonTestCase;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.net.URL;

/**
 * Installs the specified plugin before launching Hudson. 
 *
 * @author Kohsuke Kawaguchi
 */
@Documented
@Recipe(WithPlugin.RunnerImpl.class)
@Target(METHOD)
@Retention(RUNTIME)
public @interface WithPlugin {
    /**
     * Name of the plugin.
     *
     * For now, this has to be one of the plugins statically available in resources
     * "/plugins/NAME". TODO: support retrieval through Maven repository.
     */
    String value();

    public class RunnerImpl extends Recipe.Runner<WithPlugin> {
        private WithPlugin a;

        @Override
        public void setup(HudsonTestCase testCase, WithPlugin recipe) throws Exception {
            a = recipe;
        }

        @Override
        public void decorateHome(HudsonTestCase testCase, File home) throws Exception {
            URL res = getClass().getClassLoader().getResource("plugins/" + a.value());
            FileUtils.copyURLToFile(res,new File(home,"plugins/"+a.value()));
        }
    }
}
