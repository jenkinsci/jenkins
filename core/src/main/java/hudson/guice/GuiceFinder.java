package hudson.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import hudson.Extension;
import hudson.ExtensionComponent;
import hudson.ExtensionFinder;
import hudson.model.Hudson;
import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
* @author Kohsuke Kawaguchi
*/
@Extension
public final class GuiceFinder extends ExtensionFinder {
    private Injector container;

    public GuiceFinder() {
        List<Module> modules = new ArrayList<Module>();
        modules.add(new AbstractModule() {
            @SuppressWarnings({"unchecked", "ChainOfInstanceofChecks"})
            @Override
            protected void configure() {
                ClassLoader cl = Hudson.getInstance().getPluginManager().uberClassLoader;
                int id=0;

                for (final IndexItem<Extension,Object> item : Index.load(Extension.class, Object.class, cl)) {
                    id++;
                    try {
                        AnnotatedElement e = item.element();
                        Class extType;
                        if (e instanceof Class) {
                            bind((Class<?>)e).in(FaultTorelantScope.INSTANCE);
                        } else {
                            if (e instanceof Field) {
                                extType = ((Field)e).getType();
                            } else
                            if (e instanceof Method) {
                                extType = ((Method)e).getReturnType();
                            } else
                                throw new AssertionError();

                            // use arbitrary
                            bind(extType).annotatedWith(Names.named(String.valueOf(id)))
                                .toProvider(new Provider() {
                                    public Object get() {
                                        return instantiate(item);
                                    }
                                }).in(FaultTorelantScope.INSTANCE);
                        }
                    } catch (LinkageError e) {
                        // sometimes the instantiation fails in an indirect classloading failure,
                        // which results in a LinkageError
                        LOGGER.log(item.annotation().optional() ? Level.FINE : Level.WARNING,
                                   "Failed to load "+item.className(), e);
                    } catch (InstantiationException e) {
                        LOGGER.log(item.annotation().optional() ? Level.FINE : Level.WARNING,
                                   "Failed to load "+item.className(), e);
                    }
                }
            }
        });

        for (ExtensionComponent<Module> ec : new Sezpoz().find(Module.class, Hudson.getInstance())) {
            modules.add(ec.getInstance());
        }

        container = Guice.createInjector(modules);
    }

    private Object instantiate(IndexItem<Extension, Object> item) {
        try {
            return item.instance();
        } catch (LinkageError e) {
            // sometimes the instantiation fails in an indirect classloading failure,
            // which results in a LinkageError
            LOGGER.log(item.annotation().optional() ? Level.FINE : Level.WARNING,
                       "Failed to load "+item.className(), e);
        } catch (InstantiationException e) {
            LOGGER.log(item.annotation().optional() ? Level.FINE : Level.WARNING,
                       "Failed to load "+item.className(), e);
        }
        return null;
    }

    public <T> Collection<ExtensionComponent<T>> find(Class<T> type, Hudson hudson) {
        List<ExtensionComponent<T>> result = new ArrayList<ExtensionComponent<T>>();

        for (Entry<Key<?>, Binding<?>> e : container.getBindings().entrySet()) {
            if (type.isAssignableFrom(e.getKey().getTypeLiteral().getRawType())) {
                // TODO: how do we get ordinal?
                Object o = e.getValue().getProvider().get();
                if (o!=null)
                    result.add(new ExtensionComponent<T>(type.cast(o)));
            }
        }

        return result;
    }

    @Override
    public void scout(Class extensionType, Hudson hudson) {
    }

    private static final Logger LOGGER = Logger.getLogger(GuiceFinder.class.getName());
}
