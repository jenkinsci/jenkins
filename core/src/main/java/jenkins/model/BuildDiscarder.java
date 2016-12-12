package jenkins.model;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Job;
import hudson.model.Run;
import hudson.tasks.LogRotator;
import hudson.util.RobustReflectionConverter;

import java.io.IOException;

/**
 * Implementation of "Discard old build records" feature.
 *
 * <p>
 * This extension point allows plugins to implement a different strategy to decide what builds to discard
 * and what builds to keep.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.503
 */
public abstract class BuildDiscarder extends AbstractDescribableImpl<BuildDiscarder> implements ExtensionPoint {
    /**
     * Called to perform "garbage collection" on the job to discard old build records.
     *
     * <p>
     * Normally invoked automatically jobs when new builds occur.
     * The general expectation is that those marked as {@link Run#isKeepLog()} will be kept untouched.
     * To delete the build record, call {@link Run#delete()}.
     *
     * @see Job#logRotate()
     */
    public abstract void perform(Job<?,?> job) throws IOException, InterruptedException;

    @Override
    public BuildDiscarderDescriptor getDescriptor() {
        return (BuildDiscarderDescriptor)super.getDescriptor();
    }

    /**
     * {@link AbstractProject#logRotator} used to be typed as {@link LogRotator},
     * so such configuration file ends up trying to unmarshal {@link BuildDiscarder} and
     * not its subtype.
     *
     * This converter makes this work by unmarshalling a {@link LogRotator}.
     */
    public static class ConverterImpl implements Converter {
        private RobustReflectionConverter ref;

        public ConverterImpl(Mapper m) {
            ref = new RobustReflectionConverter(m,new JVM().bestReflectionProvider()) {
                @Override
                protected Object instantiateNewInstance(HierarchicalStreamReader reader, UnmarshallingContext context) {
                    return reflectionProvider.newInstance(LogRotator.class);
                }
            };
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            // abstract class, so there shouldn't be any instance.
            throw new UnsupportedOperationException();
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            // force unmarshal as LogRotator
            return ref.unmarshal(reader,context);
        }

        public boolean canConvert(Class type) {
            return type==BuildDiscarder.class;
        }
    }
}
