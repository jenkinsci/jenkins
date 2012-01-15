package jenkins.plugins.ui_samples.ModularizeViewScript

import org.kohsuke.stapler.jelly.groovy.JellyBuilder
import jenkins.util.groovy.AbstractGroovyViewModule

namespace("/lib/samples").sample(title:_("Define View Fragments Elsewhere")) {

    // normally this is how you generate tags,
    // but these are actually just a syntax sugar for method calls to the "builder" object (which is set as the delegate of the script for you)
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
class SomeGenerator extends AbstractGroovyViewModule {
    SomeGenerator(JellyBuilder builder) {
      super(builder)
    }

    def generateSomeFragment() {
        h2("Two")
        div(style:"background-color:gray; padding:2em") {
            p("Hello")  // once inside a closure, no explicit 'b.' reference is needed. this is just like other Groovy builders

            // calling other methods
            generateMoreFragment("Testing generation");
        }
    }

    def generateMoreFragment(String msg) {
        h2(msg);
        f.textarea();
    }
}

