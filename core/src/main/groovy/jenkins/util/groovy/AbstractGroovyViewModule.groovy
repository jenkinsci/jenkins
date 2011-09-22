package jenkins.util.groovy

import lib.FormTagLib
import lib.LayoutTagLib
import org.kohsuke.stapler.jelly.groovy.JellyBuilder
import org.kohsuke.stapler.jelly.groovy.Namespace
import lib.JenkinsTagLib

/**
 * Base class for utility classes for Groovy view scripts
 * <p />
 * Usage from script of a subclass, say ViewHelper:
 * <p />
 * <tt>new ViewHelper(delegate).method();</tt>
 * <p />
 * see <tt>ModularizeViewScript</tt> in ui-samples for an example how to use this class.
 *
 * @author Stefan Wolf (wolfs)
 */
abstract class AbstractGroovyViewModule {
  JellyBuilder builder
  FormTagLib f
  LayoutTagLib l
  JenkinsTagLib t
  Namespace st

  public AbstractGroovyViewModule(JellyBuilder b) {
    builder = b
    f= builder.namespace(FormTagLib)
    l=builder.namespace(LayoutTagLib)
    t=builder.namespace(JenkinsTagLib)
    st=builder.namespace("jelly:stapler")

  }

  def methodMissing(String name, args) {
    builder.invokeMethod(name,args)
  }

  def propertyMissing(String name) {
    builder.getProperty(name)
  }

  def propertyMissing(String name, value) {
    builder.setProperty(name, value)
  }
}
