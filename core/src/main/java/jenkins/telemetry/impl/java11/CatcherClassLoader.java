package jenkins.telemetry.impl.java11;

//TODO: should it be serializable, closeable?
public class CatcherClassLoader extends ClassLoader {
    // TODO: Temporary fields to audit where this class is created and if it's used more than once in the chain
    public String creationClass;
    public String creationMethod;
    public int creationLine;

    public CatcherClassLoader(ClassLoader parent) {
        super(parent);

        // Audit where I was created to avoid having too much of this objects on the classloader chain, only one is
        // needed and at the end of the chain. Probably createClassLoader in ClassicPluginStrategy is enough, besides
        // the ones during initializing Jenkins.
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        if (stackTraceElements.length >= 3) {
            creationClass = stackTraceElements[2].getClassName();
            creationMethod = stackTraceElements[2].getMethodName();
            creationLine = stackTraceElements[2].getLineNumber();
        }
        warnIfAlreadyInTheChain();
    }

    @Override
    /**
     * Usually, the {@link ClassLoader} calls its parent and finally this method. So if we are here, it's the last
     * element of the chain. It doesn't happen in {@link jenkins.util.AntClassLoader} so it has an special management
     * on {@link hudson.ClassicPluginStrategy}
     */
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        ClassNotFoundException e = new ClassNotFoundException(name);
        Java11Telemetry.reportException(name, e);
        throw e;
    }

    private void warnIfAlreadyInTheChain() {
        boolean found = false;
        ClassLoader parent = this.getParent();
        while (parent != null && !found) {
            if (parent instanceof CatcherClassLoader) {
                // Only look for the immediate parent of the same class, the grandparent was reported when creating the
                // parent, so no need to report it again.
                found = true;
                CatcherClassLoader parentClass = (CatcherClassLoader) parent;
                System.out.format("The %s was added on the chain on %s#%s[%s] and on %s#%s[%s]. Better remove the addition on %s#%s[%s]",
                        getClass().getName(), creationClass, creationMethod, creationLine,
                        parentClass.creationClass, parentClass.creationMethod, parentClass.creationLine,
                        parentClass.creationClass, parentClass.creationMethod, parentClass.creationLine);
            }
            parent = parent.getParent();
        }
    }
}