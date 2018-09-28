package jenkins.data;

import jenkins.data.tree.TreeNode;
import org.jvnet.tiger_types.Types;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class Serializer {
    protected DataModelRegistry registry = DataModelRegistry.get();

    public void setRegistry(DataModelRegistry registry) {
        this.registry = registry;
    }

    public <T> VersionedEnvelope<T> read(Class<T> payloadType, InputStream in) throws IOException {
        return read(payloadType,new InputStreamReader(in, UTF_8));
    }

    public <T> VersionedEnvelope<T> read(Class<T> payloadType, Reader in) throws IOException {
        Type type = Types.createParameterizedType(VersionedEnvelope.class, payloadType);
        return (VersionedEnvelope<T>) registry.lookupOrFail(type).read(unstring(in), createContext());
    }

    protected abstract TreeNode unstring(Reader in) throws IOException;

    public void write(VersionedEnvelope<?> data, Writer out) throws IOException {
        TreeNode tree = registry.lookupOrFail(data.getClass()).write(data, createContext());
        stringify(tree,out);
    }

    public void write(VersionedEnvelope<?> data, OutputStream out) throws IOException {
        write(data,new OutputStreamWriter(out,UTF_8));
    }

    protected abstract void stringify(TreeNode tree, Writer out) throws IOException;

    private DataContext createContext() {
        return new DataContext(registry);
    }
}
