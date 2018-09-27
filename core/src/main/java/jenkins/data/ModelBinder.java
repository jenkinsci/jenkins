package jenkins.data;

import jenkins.data.model.CNode;

public interface ModelBinder<T> {
    CNode write(T object, DataContext context);
    T read(CNode input, DataContext context);
}
