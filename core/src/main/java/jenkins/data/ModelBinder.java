package jenkins.data;

import jenkins.data.model.CNode;

public interface ModelBinder<T> {
    CNode write(T object, WriteDataContext context);
    T read(CNode input, ReadDataContext context);
}
