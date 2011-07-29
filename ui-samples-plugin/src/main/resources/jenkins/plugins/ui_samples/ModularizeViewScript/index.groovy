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
    JellyBuilder b;
    FormTagLib f;

    SomeGenerator(JellyBuilder builder) {
        this.b = builder
        f = builder.namespace(FormTagLib.class)
    }

    def generateSomeFragment() {
        b.h2("Two")
        b.div(style:"background-color:gray; padding:2em") {
            p("Hello")  // once inside a closure, no explicit 'b.' reference is needed. this is just like other Groovy builders

            // calling other methods
            generateMoreFragment("Testing generation");
        }
    }

    def generateMoreFragment(String msg) {
        b.h2(msg);
        f.textarea();
    }
}

