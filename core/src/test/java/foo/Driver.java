package foo;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scope;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
//import com.google.inject.multibindings.Multibinder;

import java.util.Set;

/**
 * @author Kohsuke Kawaguchi
 */
public class Driver {
    @Inject
    public Set<Animal> animals;

    @Inject
    public Set<Cute> cute;

    /*
        If Cat has @Singleton, multiple bindings result in the same instance.
        How can I achieve that without changing annotation?
     */

    public static void main(String[] args) {
//        Injector i = Guice.createInjector(new AbstractModule() {
//            @Override
//            protected void configure() {
//                bind(Cat.class).in(Singleton.class);
//
//                Multibinder<Animal> ab = Multibinder.newSetBinder(binder(), Animal.class);
//                ab.addBinding().to(Dog.class);
//                ab.addBinding().to(Cat.class);
//
//                Multibinder<Cute> cb = Multibinder.newSetBinder(binder(), Cute.class);
//                cb.addBinding().to(Cat.class);
//                cb.addBinding().to(Banana.class);
//            }
//        });
//        i.getInstance(Driver.class).run();
//
//        // Dog isn't singleton scoped, so you get a different instance, but Cat is singleton,
//        // so you get the same value
//        System.out.println(i.getInstance(Key.get(new TypeLiteral<Set<Animal>>(){})));
    }

    public void run() {
        System.out.println(animals);
        System.out.println(cute);
    }
}
