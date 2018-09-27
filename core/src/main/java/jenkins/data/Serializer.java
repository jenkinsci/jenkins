package jenkins.data;

import java.io.InputStream;
import java.io.OutputStream;

public abstract class Serializer {
    public abstract <T> VersionedResource<T> read(Class<T> type, InputStream in);

    public abstract void write(VersionedResource<?> data, OutputStream out);
}
