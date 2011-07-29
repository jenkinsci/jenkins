package jenkins.plugins.ui_samples.ModularizeViewScript
import org.kohsuke.stapler.jelly.groovy.JellyBuilder
import lib.FormTagLib

namespace("/lib/samples").sample(title:_("Define View Fragments Elsewhere")) {

    // normally this is how you generate tags,
    // but these are actually just a syntax sugar for method calls to the "delegate" object.
    h2("One")
    div (style:"border:1px solid blue") {
        p("some pointless text")
    }

    // so all we need to do is to pass around this delegate object and then you can generate fragments
    // from elsewhere
    new SomeGenerator(builder).generateSomeFragment()
}


// I defined this class here just to make the sample concise.
// this class can be defined anywhere, and typically you'd do this somewhere in your src/main/groovy
static class SomeGenerator {
    JellyBuilder builder;
    FormTagLib f;

    SomeGenerator(JellyBuilder builder) {
        this.builder = builder
        f = builder.namespace(FormTagLib.class)
    }

    // this wrapper makes it so that we don't have to explicitly call with "builder."
    // the 'with' method in GDK doesn't work nicely because it does DELEGATE_FIRST resolution,
    // and you can accidentally pick up variables defined in the ancestor JellyContexts.
    def generate(Closure closure) {
        closure = closure.clone();
        closure.delegate = builder;
        return closure.call();
    }

    def generateSomeFragment() {
        generate {
            h2("Two")   // this call is a shorthand for builder.h2("Two")

            div(style:"background-color:gray; padding:2em") {
                // calling other methods
                generateMoreFragment("Testing generation");
            }
        }
    }

    def generateMoreFragment(String msg) {
        generate {
            h2(msg);
            f.textarea();
        }
    }
}

