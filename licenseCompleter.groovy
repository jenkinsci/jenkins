/*
 * This script augments the missing license information in our dependencies.
 */
complete {
  // license constants
  def apacheLicense = license("The Apache Software License, Version 2.0", "http://www.apache.org/licenses/LICENSE-2.0.txt")
  def cddl = license("CDDL", "http://www.sun.com/cddl/")
  def lgpl = license("LGPL 2.1", "http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html")
  def mitLicense = license("MIT License", "http://www.opensource.org/licenses/mit-license.php")
  def bsdLicense = license("BSD License", "http://opensource.org/licenses/BSD-2-Clause")
  def jenkinsLicense = license("MIT License", "https://www.jenkins.io/mit-license")
  def ccby = license("Creative Commons Attribution License", "http://creativecommons.org/licenses/by/2.5")


  match("asm:*") {
    if (dependency.licenses.isEmpty())
      rewriteLicense([], license("BSD License", "http://asm.ow2.org/license.html"))
  }

  // Apache components
  // logkit is a part of Avalon
  match([
    "org.apache.ant:*",
    "commons-jelly:*",
    "log4j:*",
    "avalon-framework:*",
    "logkit:logkit",
    "oro:oro",
    "commons-codec:*",
    "commons-beanutils:*",
    "commons-net:*",
    "commons-cli:*",
    "*:commons-jelly",
    "org.jvnet.hudson:commons-jelly-tags-define",
    "slide:slide-webdavlib"
  ]) {
    if (dependency.licenses.isEmpty())
      rewriteLicense([], apacheLicense)
  }

  // GlassFish components are dual-licensed between CDDL and GPL+Classpath Exception
  // we elect to take them under CDDL.
  // note that central has a different POM from m.g.o-public (http://repo2.maven.org/maven2/javax/mail/mail/1.4/mail-1.4.pom
  // vs http://maven.glassfish.org/content/groups/public/javax/mail/mail/1.4/mail-1.4.pom), so we aren't using  rewriteLicense here
  match([
    "javax.mail:*",
    "org.jvnet.hudson:activation",
    "org.jvnet:tiger-types",
    "javax.servlet:jstl",
    "javax.xml.stream:stax-api"
  ]) {
    if (dependency.licenses.isEmpty())
      dependency.licenses=[cddl]
  }

  /* TODO
   // according to JSR-250 1.0-20050927.133100 POM, it came from JAX-WS, which is under CDDL.
   match("javax.annotation:jsr250-api") {
   rewriteLicense([], cddl)
   }
   */

  match("org.jenkins-ci.dom4j:dom4j") {
    rewriteLicense([], license("BSD License", "http://dom4j.sourceforge.net/dom4j-1.6.1/license.html"))
  }

  match(["org.jenkins-ci.groovy:*"]) {
    // see https://groovy-lang.org/faq.html
    // see http://jmdns.sourceforge.net/license.html
    rewriteLicense([], apacheLicense)
  }

  match("relaxngDatatype:relaxngDatatype") {
    // see http://sourceforge.net/projects/relaxng/
    rewriteLicense([], bsdLicense)
  }

  match(["org.kohsuke.jinterop:j-interop", "org.kohsuke.jinterop:j-interopdeps"]) {
    rewriteLicense([license("MIT license", "http://www.opensource.org/licenses/mit-license.php")], license("LGPL v3", "http://www.j-interop.org/license.html"))
  }

  // these are our own modules that have license in the trunk but not in these released versions
  // as we upgrade them, we should just pick up the license info from POM
  match(["*:maven2.1-interceptor", "*:lib-jenkins-maven-embedder"]) {
    rewriteLicense([], jenkinsLicense)
  }

  match("org.codehaus.plexus:plexus-interactivity-api") {
    rewriteLicense([], mitLicense)
  }

  match("de.zeigermann.xml:xml-im-exporter:1.1") {
    rewriteLicense([], license("BSD License", "http://xml-im-exporter.cvs.sourceforge.net/viewvc/xml-im-exporter/xml-im-exporter/Copying.txt?revision=1.3&view=markup"))
  }

  match("*:sezpoz") {
    // GPL-phobia people react to "GPL" strongly, so accept sezpoz under CDDL
    rewriteLicense([license("CDDL or GPL 2 with Classpath Exception", null)], cddl)
  }

  match("net.jcip:jcip-annotations") {
    rewriteLicense([], ccby)
  }
}
