package jenkins.security.stapler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.Function;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.json.JsonResponse;
import org.kohsuke.stapler.lang.FieldRef;

class StaplerSignaturesTest {

    @Test
    void testSignaturesSimple() {
        Set<String> methodSignatures = Arrays.stream(SomeClass.class.getMethods()).map(it -> new Function.InstanceFunction(it).getSignature()).collect(Collectors.toSet());
        assertEquals(SomeClass.METHOD_SIGNATURES, methodSignatures);

        Set<String> fieldSignatures = Arrays.stream(SomeClass.class.getFields()).map(it -> FieldRef.wrap(it).getSignature()).collect(Collectors.toSet());
        assertEquals(SomeClass.FIELD_SIGNATURES, fieldSignatures);
    }

    @Test
    void testSignaturesInheritance() {
        Set<String> methodSignatures = Arrays.stream(SomeSubclass.class.getMethods()).map(it -> new Function.InstanceFunction(it).getSignature()).collect(Collectors.toSet());
        assertEquals(SomeSubclass.METHOD_SIGNATURES, methodSignatures);

        Set<String> fieldSignatures = Arrays.stream(SomeSubclass.class.getFields()).map(it -> FieldRef.wrap(it).getSignature()).collect(Collectors.toSet());
        assertEquals(SomeSubclass.FIELD_SIGNATURES, fieldSignatures);
    }

    public static class SomeClass {
        static Set<String> METHOD_SIGNATURES = new HashSet<>(Arrays.asList(
                "method jenkins.security.stapler.StaplerSignaturesTest$SomeClass getFoo",
                "method jenkins.security.stapler.StaplerSignaturesTest$SomeClass getFoo java.lang.String",
                "staticMethod jenkins.security.stapler.StaplerSignaturesTest$SomeClass getFoo int",
                "staticMethod jenkins.security.stapler.StaplerSignaturesTest$SomeClass getFoo long",
                "method jenkins.security.stapler.StaplerSignaturesTest$SomeClass getFoo jenkins.security.stapler.StaplerSignaturesTest$SomeClass",
                "method jenkins.security.stapler.StaplerSignaturesTest$SomeClass doFoo org.kohsuke.stapler.StaplerRequest2 org.kohsuke.stapler.StaplerResponse2",
                "method jenkins.security.stapler.StaplerSignaturesTest$SomeClass doWhatever java.lang.String",
                "method java.lang.Object getClass",
                "method java.lang.Object equals java.lang.Object",
                "method java.lang.Object hashCode",
                "method java.lang.Object notify",
                "method java.lang.Object notifyAll",
                "method java.lang.Object toString",
                "method java.lang.Object wait long int",
                "method java.lang.Object wait long",
                "method java.lang.Object wait"
        ));

        public void getFoo() {}

        public void getFoo(String arg) {}

        public static void getFoo(int arg) {}

        public static void getFoo(long arg) {}

        public void getFoo(SomeClass arg) {}

        public void doFoo(StaplerRequest2 req, StaplerResponse2 rsp) {}

        @StaplerDispatchable @JsonResponse
        public void doWhatever(@QueryParameter String arg) {}

        static Set<String> FIELD_SIGNATURES = new HashSet<>(Arrays.asList(
                "field jenkins.security.stapler.StaplerSignaturesTest$SomeClass whatever",
                "field jenkins.security.stapler.StaplerSignaturesTest$SomeClass thing",
                "staticField jenkins.security.stapler.StaplerSignaturesTest$SomeClass staticField",
                "field jenkins.security.stapler.StaplerSignaturesTest$SomeClass stringList"
        ));
        public String whatever;
        public Object thing;
        public static Object staticField;
        public List<String> stringList;

    }

    public static class SomeSubclass extends SomeClass {
        static Set<String> METHOD_SIGNATURES = new HashSet<>(Arrays.asList(
                "method jenkins.security.stapler.StaplerSignaturesTest$SomeSubclass getFoo",
                "method jenkins.security.stapler.StaplerSignaturesTest$SomeSubclass subtypeExclusive",
                "method jenkins.security.stapler.StaplerSignaturesTest$SomeSubclass subtypeExclusive java.lang.String",
                "method jenkins.security.stapler.StaplerSignaturesTest$SomeSubclass varargMethod [Ljava.lang.String;",
                "method jenkins.security.stapler.StaplerSignaturesTest$SomeClass getFoo java.lang.String",
                "staticMethod jenkins.security.stapler.StaplerSignaturesTest$SomeClass getFoo int",
                "staticMethod jenkins.security.stapler.StaplerSignaturesTest$SomeClass getFoo long",
                "method jenkins.security.stapler.StaplerSignaturesTest$SomeClass getFoo jenkins.security.stapler.StaplerSignaturesTest$SomeClass",
                "method jenkins.security.stapler.StaplerSignaturesTest$SomeClass doFoo org.kohsuke.stapler.StaplerRequest2 org.kohsuke.stapler.StaplerResponse2",
                "method jenkins.security.stapler.StaplerSignaturesTest$SomeClass doWhatever java.lang.String",
                "method java.lang.Object getClass",
                "method java.lang.Object equals java.lang.Object",
                "method java.lang.Object hashCode",
                "method java.lang.Object notify",
                "method java.lang.Object notifyAll",
                "method java.lang.Object toString",
                "method java.lang.Object wait long int",
                "method java.lang.Object wait long",
                "method java.lang.Object wait"
        ));

        @Override
        public void getFoo() {}

        public void subtypeExclusive(){}

        public void subtypeExclusive(String arg){}

        public void varargMethod(String... args){}

        static Set<String> FIELD_SIGNATURES = new HashSet<>(Arrays.asList(
                "field jenkins.security.stapler.StaplerSignaturesTest$SomeSubclass whatever",
                "field jenkins.security.stapler.StaplerSignaturesTest$SomeClass whatever",
                "field jenkins.security.stapler.StaplerSignaturesTest$SomeClass thing",
                "staticField jenkins.security.stapler.StaplerSignaturesTest$SomeSubclass staticField",
                "staticField jenkins.security.stapler.StaplerSignaturesTest$SomeClass staticField",
                "field jenkins.security.stapler.StaplerSignaturesTest$SomeClass stringList"
        ));
        public String whatever;
        public static Object staticField;
    }
}
