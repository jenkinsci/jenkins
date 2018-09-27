package jenkins.data;

import jenkins.data.model.CNode;

import java.util.Collection;
import java.util.function.Function;

public interface ModelBinder<T> {
    CNode write(T object, WriteDataContext context);
    T read(CNode input, ReadDataContext context);

    /**
     * Returns all the parameters of the model the binder represents
     */
    Collection<DescribableParameter> getParameters();

    default DescribableParameter getParameter(String name) {
        for (DescribableParameter p : getParameters()) {
            if (p.name.equals(name))
                return p;
        }
        return null;
    }



    static <X,Y> ModelBinder<Y> byTranslation(Class<X> dto, Function<X,Y> reader, Function<Y,X> writer) {
        throw new UnsupportedOperationException(); // TODO
    }

    static <T> ModelBinder<T> byReflection(Class<T> type) {
        throw new UnsupportedOperationException(); // TODO
    }
}
