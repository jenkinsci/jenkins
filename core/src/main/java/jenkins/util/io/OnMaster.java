package jenkins.util.io;

/**
 * Marks the objects in Jenkins that only exist in the core
 * and not on agents.
 *
 * <p>
 * This marker interface is for plugin developers to quickly
 * tell if they can take a specific object from a controller to
 * an agent.
 *
 * (Core developers, if you find classes/interfaces that extend
 * from this, please be encouraged to add them.)
 *
 * @author Kohsuke Kawaguchi
 * @since 1.475
 */
public interface OnMaster {
// TODO uncomment once we can have a delegating ClassFilter, also add SystemProperty to toggle feature
//    @Extension
//    @Restricted(NoExternalUse.class)
//    class ChannelConfiguratorImpl extends ChannelConfigurator {
//        @Override
//        public void onChannelBuilding(ChannelBuilder builder, @Nullable Object context) {
//            if (context instanceof AgentComputer) {
//                builder.withClassFilter(new ClassFilterImpl(builder.getClassFilter(), OnMaster.class.getName, ...));
//            }
//        }
//    }
//
//    @Restricted(NoExternalUse.class)
//    class ClassFilterImpl extends ClassFilter {
//        private final ClassFilter delegate;
//        private final Set<String> blacklist;
//
//        public ClassFilterImpl(ClassFilter delegate, String... blacklist) {
//            this.blacklist = new HashSet<>(blacklist);
//            this.delegate = delegate;
//        }
//
//        @Override
//        protected boolean isBlacklisted(String name) {
//            return blacklist.contains(name) || delegate.isBlacklisted(name);
//        }
//
//        @Override
//        protected boolean isBlacklisted(Class c) {
//            return c.getAnnotation(MasterJVMOnly.class) != null || delegate.isBlacklisted(c);
//        }
//    }
}
