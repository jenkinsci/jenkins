package jenkins.data;

import jenkins.data.model.CNode;

import java.util.Collection;
import java.util.function.Function;

public interface DataModel<T> {
    CNode write(T object, WriteDataContext context);
    T read(CNode input, ReadDataContext context);

    /**
     * Returns all the parameters of the model the binder represents
     */
    Collection<DataModelParameter> getParameters();

    default DataModelParameter getParameter(String name) {
        for (DataModelParameter p : getParameters()) {
            if (p.getName().equals(name))
                return p;
        }
        return null;
    }



    static <X,Y> DataModel<Y> byTranslation(Class<X> dto, Function<X,Y> reader, Function<Y,X> writer) {
        throw new UnsupportedOperationException(); // TODO
    }

    static <T> DataModel<T> byReflection(Class<T> type) {
        throw new UnsupportedOperationException(); // TODO
    }
}
