package jenkins;

import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Module;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.Element;
import com.google.inject.spi.ElementVisitor;
import com.google.inject.spi.Elements;
import com.google.inject.spi.HasDependencies;

import java.util.Collection;

/**
 * @author Kohsuke Kawaguchi
 */
public class OptionalComponentValidator implements Module {
    private final Collection<? extends Module> sources;

    public OptionalComponentValidator(Collection<? extends Module> sources) {
        this.sources = sources;
    }

    public void configure(final Binder binder) {
        ElementVisitor<Boolean> visitor = new DefaultElementVisitor<Boolean>() {
            @Override
            public <T> Boolean visit(Binding<T> binding) {
                if (binding instanceof HasDependencies) {
                    for (Dependency d : ((HasDependencies)binding).getDependencies()) {
                    }
                }
                return true;
            }

            @Override
            protected Boolean visitOther(Element element) {
                return true;
            }
        };

        for (Element e : Elements.getElements(sources)) {
            if (e.acceptVisitor(visitor))
                e.applyTo(binder);
        }
    }
}
