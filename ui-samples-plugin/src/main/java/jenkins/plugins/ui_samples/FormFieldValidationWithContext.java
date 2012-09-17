package jenkins.plugins.ui_samples;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * How to access values of the nearby input fields when you do form field validation.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class FormFieldValidationWithContext extends UISample {
    private List<State> states = new ArrayList<State>(Arrays.asList(
            new State("California",new City("Sacramento"),  Arrays.asList(new City("San Francisco"),new City("Los Angeles"))),
            new State("New York",new City("New York"), Arrays.asList(new City("Albany"),new City("Ithaca")))
    ));

    public FormFieldValidationWithContext() {
    }

    @DataBoundConstructor
    public FormFieldValidationWithContext(List<State> states) {
        this.states = states;
    }

    @Override
    public String getDescription() {
        return "How to access values of the nearby input fields when you do form field validation";
    }

    public List<State> getStates() {
        return states;
    }

    @Override
    public List<SourceFile> getSourceFiles() {
        List<SourceFile> r = super.getSourceFiles();
        r.add(new SourceFile("City/config.groovy"));
        r.add(new SourceFile("State/config.groovy"));
        return r;
    }

    public static class State extends AbstractDescribableImpl<State> {
        /*
            I'm lazy and just exposing fields as opposed to getter/setter.
            Jenkins doesn't care and works correctly either way.
         */
        public String name;
        public City capital;
        public List<City> cities;

        @DataBoundConstructor
        public State(String name, City capital, List<City> cities) {
            this.name = name;
            this.capital = capital;
            this.cities = cities;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<State> {
            @Override
            public String getDisplayName() {
                return "";
            }

            public FormValidation doCheckName(@QueryParameter String value,
                                                   @RelativePath("capital") @QueryParameter String name) {
                /*
                @RelativePath("capital") @QueryParameter
                 ... is short for
                @RelativePath("capital") @QueryParameter("name")
                 ... and thus can be thought of "capital/name"

                so this matches the current city name entered as the capital of this state
                */

                return FormValidation.ok("Are you sure " + name + " is a capital of " + value + "?");
            }
        }
    }

    public static class City extends AbstractDescribableImpl<City> {
        public String name;

        @DataBoundConstructor
        public City(String name) {
            this.name = name;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<City> {
            @Override
            public String getDisplayName() {
                return "";
            }

            public FormValidation doCheckName(@QueryParameter String value,
                                                   @RelativePath("..") @QueryParameter String name) {
                /*
                @RelativePath("..") @QueryParameter
                 ... is short for
                @RelativePath("..") @QueryParameter("name")
                 ... and thus can be thought of "../name"

                in the UI, fields for city is wrapped inside those of state, so "../name" binds
                to the name field in the state.
                */

                if (name==null || value==null || value.contains(name))             return FormValidation.ok();
                return FormValidation.warning("City name doesn't contain "+name);
            }
        }
    }

    @Extension
    public static class DescriptorImpl extends UISampleDescriptor {
    }
}
