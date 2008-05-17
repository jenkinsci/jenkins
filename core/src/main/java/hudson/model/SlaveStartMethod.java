package hudson.model;

import hudson.ExtensionPoint;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.io.OutputStream;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;

/**
 * Extension point to allow control over how Slaves are started.
 *
 * @author Stephen Connolly
 * @since 24-Apr-2008 22:12:35
 */
public abstract class SlaveStartMethod implements Describable<SlaveStartMethod>, ExtensionPoint {

    public boolean isStartSupported() {
        return true;
    }

    public abstract void start(Slave.ComputerImpl computer, Slave slave, OutputStream launchLog, Logger logger);

    public static final List<Descriptor<SlaveStartMethod>> LIST = new ArrayList<Descriptor<SlaveStartMethod>>();
}
