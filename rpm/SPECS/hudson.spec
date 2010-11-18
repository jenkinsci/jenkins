# TODO:
# - how to add to the trusted service of the firewall?

%define _prefix	%{_usr}/lib/hudson
%define workdir	%{_var}/lib/hudson

Name:		hudson
Version:	%{ver}
Release:	1.1
Summary:	Continous Build Server
Source:		hudson.war
Source1:	hudson.init.in
Source2:	hudson.sysconfig.in
Source3:	hudson.logrotate
Source4:    hudson.repo
URL:		https://hudson.dev.java.net/
Group:		Development/Tools/Building
License:	MIT/X License, GPL/CDDL, ASL2
BuildRoot:	%{_tmppath}/build-%{name}-%{version}
# see the comment below from java-1.6.0-openjdk.spec that explains this dependency
# java-1.5.0-ibm from jpackage.org set Epoch to 1 for unknown reasons,
# and this change was brought into RHEL-4.  java-1.5.0-ibm packages
# also included the epoch in their virtual provides.  This created a
# situation where in-the-wild java-1.5.0-ibm packages provided "java =
# 1:1.5.0".  In RPM terms, "1.6.0 < 1:1.5.0" since 1.6.0 is
# interpreted as 0:1.6.0.  So the "java >= 1.6.0" requirement would be
# satisfied by the 1:1.5.0 packages.  Thus we need to set the epoch in
# JDK package >= 1.6.0 to 1, and packages referring to JDK virtual
# provides >= 1.6.0 must specify the epoch, "java >= 1:1.6.0".
#
# Kohsuke - 2009/09/29
#    test by mrooney on what he believes to be RHEL 5.2 indicates
#    that there's no such packages. JRE/JDK RPMs from java.sun.com
#    do not have this virtual package declarations. So for now,
#    I'm dropping this requirement.
# Requires:	java >= 1:1.6.0
PreReq:		/usr/sbin/groupadd /usr/sbin/useradd
#PreReq:		%{fillup_prereq}
BuildArch:	noarch

%description
Hudson monitors executions of repeated jobs, such as building a software
project or jobs run by cron. Among those things, current Hudson focuses on the
following two jobs:
- Building/testing software projects continuously, just like CruiseControl or
  DamageControl. In a nutshell, Hudson provides an easy-to-use so-called
  continuous integration system, making it easier for developers to integrate
  changes to the project, and making it easier for users to obtain a fresh
  build. The automated, continuous build increases the productivity.
- Monitoring executions of externally-run jobs, such as cron jobs and procmail
  jobs, even those that are run on a remote machine. For example, with cron,
  all you receive is regular e-mails that capture the output, and it is up to
  you to look at them diligently and notice when it broke. Hudson keeps those
  outputs and makes it easy for you to notice when something is wrong.




Authors:
--------
    Kohsuke Kawaguchi <Kohsuke.Kawaguchi@sun.com>

%prep
%setup -q -T -c

%build

%install
rm -rf "%{buildroot}"
%__install -D -m0644 "%{SOURCE0}" "%{buildroot}%{_prefix}/%{name}.war"
%__install -d "%{buildroot}%{workdir}"
%__install -d "%{buildroot}%{workdir}/plugins"

%__install -d "%{buildroot}/var/log/hudson"

%__install -D -m0755 "%{SOURCE1}" "%{buildroot}/etc/init.d/%{name}"
%__sed -i 's,@@WAR@@,%{_prefix}/%{name}.war,g' "%{buildroot}/etc/init.d/%{name}"
%__install -d "%{buildroot}/usr/sbin"
%__ln_s "../../etc/init.d/%{name}" "%{buildroot}/usr/sbin/rc%{name}"

%__install -D -m0600 "%{SOURCE2}" "%{buildroot}/etc/sysconfig/%{name}"
%__sed -i 's,@@HOME@@,%{workdir},g' "%{buildroot}/etc/sysconfig/%{name}"

%__install -D -m0644 "%{SOURCE3}" "%{buildroot}/etc/logrotate.d/%{name}"
%__install -D -m0644 "%{SOURCE4}" "%{buildroot}/etc/yum.repos.d/hudson.repo"

%pre
/usr/sbin/groupadd -r hudson &>/dev/null || :
# SUSE version had -o here, but in Fedora -o isn't allowed without -u
/usr/sbin/useradd -g hudson -s /bin/false -r -c "Hudson Continuous Build server" \
	-d "%{workdir}" hudson &>/dev/null || :

%post
/sbin/chkconfig --add hudson

%preun
if [ "$1" = 0 ] ; then
    # if this is uninstallation as opposed to upgrade, delete the service
    /sbin/service hudson stop > /dev/null 2>&1
    /sbin/chkconfig --del hudson
fi
exit 0

%postun
if [ "$1" -ge 1 ]; then
    /sbin/service hudson condrestart > /dev/null 2>&1
fi
exit 0

%clean
%__rm -rf "%{buildroot}"

%files
%defattr(-,root,root)
%dir %{_prefix}
%{_prefix}/%{name}.war
%attr(0755,hudson,hudson) %dir %{workdir}
%attr(0750,hudson,hudson) /var/log/hudson
%config /etc/logrotate.d/%{name}
%config /etc/init.d/%{name}
%config /etc/sysconfig/%{name}
/etc/yum.repos.d/hudson.repo
/usr/sbin/rc%{name}

%changelog
* Mon Jul  6 2009 dmacvicar@suse.de
- update to 1.314:
  * Added option to advanced project configuration to clean workspace before each build.
    (issue 3966 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3966])
  * Fixed workspace deletion issue on subversion checkout.
    (issue 3580 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3580])
  * Hudson failed to notice a build result status change if aborted builds were in the middle.
    (report [http://www.nabble.com/Losing-build-state-after-aborts--td24335949.html])
  * Hudson CLI now tries to connect to Hudson via plain TCP/IP, then fall back to tunneling over HTTP.
  * Fixed a possible "Cannot create a file when that file already exists" error in managed Windows slave launcher. report [http://www.nabble.com/%%22Cannot-create-a-file-when-that-file-already-exists%%22---huh--td23949362.html#a23950643]
  * Made Hudson more robust in parsing CVS/Entries report [http://www.nabble.com/Exception-while-checking-out-from-CVS-td24256117.html]
  * Fixed a regression in the groovy CLI command report [http://d.hatena.ne.jp/tanamon/20090630/1246372887]
  * Fixed regression in handling of usernames containing <, often used by Mercurial.
    (issue 3964 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3964])
  * Allow Maven projects to have their own artifact archiver settings.
    (issue 3289 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3289])
  * Added copy-job, delete-job, enable-job, and disable-job command.
  * Fixed a bug in the non-ASCII character handling in remote bulk file copy.
    (report [http://www.nabble.com/WG%%3A-Error-when-saving-Artifacts-td24106649.html])
  * Supported self restart on all containers in Unix
    (report [http://www.nabble.com/What-are-your-experiences-with-Hudson-and-different-containers--td24075611.html])
  * Added Retry Logic to SCM Checkout
  * Fix bug in crumb validation when client is coming through a proxy.
    (issue 3854 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3854])
  * Replaced "appears to be stuck" with an icon.
    (issue 3891 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3891])
  * WebDAV deployment from Maven was failing with VerifyError.
  * Subversion checkout/update gets in an infinite loop when a previously valid password goes invalid.
    (issue 2909 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2909])
  * 1.311 jars were not properly signed
  * Subversion SCM browsers were not working.
    (report [http://www.nabble.com/Build-311-breaks-change-logs-td24150221.html])
  * Gracefully handle IBM JVMs on PowerPC.
    (issue 3875 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3875])
  * Fixed NPE with GlassFish v3 when CSRF protection is on.
    (issue 3878 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3878])
  * Fixed a bug in CLI where the state of command executions may interfere with each other.
  * CLI should handle the situation gracefully if the server doesn't use crumb.
  * Fixed a performance problem in CLI execution.
  * Don't populate the default value of the Subversion local directory name.
    (report [http://www.nabble.com/Is-%%22%%22Local-module-directory%%22-really-optional--td24035475.html])
  * Integrated SVNKit 1.3.0
  * Implemented more intelligent polling assisted by commit-hook from SVN repository. (details [http://wiki.hudson-ci.org/display/HUDSON/Subversion+post-commit+hook])
  * Subversion support is moved into a plugin to isolate SVNKit that has GPL-like license.
  * Fixed a performance problem in master/slave file copy.
    (issue 3799 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3799])
  * Set time out to avoid infinite hang when SMTP servers don't respond in time.
    (report [http://www.nabble.com/Lockup-during-e-mail-notification.-td23718820.html])
  * Ant/Maven installers weren't setting the file permissions on Unix.
    (issue 3850 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3850])
  * Fixed cross-site scripting vulnerabilities, thanks to Steve Milner.
  * Changing number of executors for master node required Hudson restart.
    (issue 3092 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3092])
  * Improved validation and help text regarding when number of executors setting may be zero.
    (issue 2110 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2110])
  * NPE fix in the remote API if @xpath is used without @exclude. (patch [http://www.nabble.com/Adding-Remote-API-support-to-findbugs-and-emma-plugins-td23819499.html])
  * Expose the node name as 'NODE_NAME' environment varilable to build.
  * Added a CLI command to clear the job queue.
    (report [http://www.nabble.com/cancel-all--td23930886.html])
  * Sundry improvements to automatic tool installation. You no longer need to configure an absolute tool home directory. Also some Unix-specific fixes.
  * Generate nonce values to prevent cross site request forgeries. Extension point to customize the nonce generation algorithm.
  * Reimplemented JDK auto installer to reduce Hudson footprint by 5MB. This also fix a failure to run on JBoss.
    (report [http://www.nabble.com/Hudson-1.308-seems-to-be-broken-with-Jboss-td23780609.html])
  * Unit test trend graph was not displayed if there's no successful build.
    (report [http://www.nabble.com/Re%%3A-Test-Result-Trend-plot-not-showing-p23707741.html])
  * init script ($HUDSON_HOME/init.groovy) is now run with uber-classloader.
  * Maven2 projects may fail with "Cannot lookup required component".
    (issue 3706 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3706])
  * Toned down the form validation of JDK, Maven, Ant installations given that we can now auto-install them.
  * Ant can now be automatically installed from ant.apache.org.
  * Maven can now be automatically installed from maven.apache.org.
  * AbstractProject.doWipeOutWorkspace() wasn't calling SCM.processWorkspaceBeforeDeletion.
    (issue 3506 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3506])
  * X-Hudson header was sent for all views, not just the top page.
    (report [http://www.netbeans.org/issues/show_bug.cgi?id=165067])
  * Remote API served incorrect absolute URLs when Hudson is run behind a reverse proxy.
    (report [http://www.netbeans.org/issues/show_bug.cgi?id=165067])
  * Further improved the JUnit report parsing.
    (report [http://www.nabble.com/NPE-%%28Fatal%%3A-Null%%29-in-recording-junit-test-results-td23562964.html])
  * External job monitoring was ignoring the possible encoding difference between Hudson and the remote machine that executed the job.
    (report [http://www.nabble.com/windows%%E3%%81%%AEhudson%%E3%%81%%8B%%E3%%82%%89ssh%%E3%%82%%B9%%E3%%83%%AC%%E3%%83%%BC%%E3%%83%%96%%E3%%82%%92%%E4%%BD%%BF%%E3%%81%%86%%E3%%81%%A8%%E3%%81%%8D%%E3%%81%%AE%%E6%%96%%87%%E5%%AD%%97%%E3%%82%%B3%%E3%%83%%BC%%E3%%83%%89%%E5%%8F%%96%%E3%%82%%8A%%E6%%89%%B1%%E3%%81%%84%%E3%%81%%AB%%E3%%81%%A4%%E3%%81%%84%%E3%%81%%A6-td23583532.html])
  * Slave launch log was doing extra buffering that can prevent error logs (and so on) from showing up instantly.
    (report [http://www.nabble.com/Selenium-Grid-Plugin-tp23481283p23486010.html])
  * Some failures in Windows batch files didn't cause Hudson to fail the build.
    (report [http://www.nabble.com/Propagating-failure-in-Windows-Batch-actions-td23603409.html])
  * Maven 2.1 support was not working on slaves.
    (report [http://www.nabble.com/1.305-fully-break-native-maven-support-td23575755.html])
  * Fixed a bug that caused Hudson to delete slave workspaces too often.
    (issue 3653 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3653])
  * If distributed build isn't enabled, slave selection drop-down shouldn't be displayed in the job config.
  * Added support for Svent 2.x SCM browsers.
    (issue 3357 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3357])
  * Fixed unexpanded rootURL in CLI.
    (report [http://d.hatena.ne.jp/masanobuimai/20090511#1242050331])
  * Trying to see the generated maven site results in 404.
    (issue 3497 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3497])
  * Long lines in console output are now wrapped in most browsers.
  * Hudson can now automatically install JDKs from java.sun.com
  * The native m2 mode now works with Maven 2.1
    (issue 2373 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2373])
  * CLI didn't work with "java -jar hudson.war"
    (report [http://d.hatena.ne.jp/masanobuimai/20090503#1241357664])
  * Link to the jar file in the CLI usage page is made more robust.
    (issue 3621 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3621])
  * "Build after other projects are built" wasn't loading the proper setting.
    (issue 3284 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3284])
  * Hudson started as "java -jar hudson.war" can now restart itself on all Unix flavors.
  * When run on GlassFish, Hudson configures GF correctly to handle URI encoding always in UTF-8
  * Added a new extension point to contribute fragment to UDP-based auto discovery.
  * Rolled back changes for HUDSON-3580 - workspace is once again deleted on svn checkout.
    (issue 3580 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3580])
  * Fixed a binary incompatibility in UpstreamCause that results in NoSuchMethodError. Regression in 1.302.
    (report [http://www.nabble.com/URGENT%%3A-parameterizedtrigger-plugin-gone-out-of-compatible-with-the--latest-1.302-release....-Re%%3A-parameterized-plugin-sometime-not-trigger-a--build...-td23349444.html])
  * The "groovysh" CLI command puts "println" to server stdout, instead of CLI stdout.
  * The elusive 'Not in GZIP format' exception is finally fixed thanks to cristiano_k's great detective work
    (issue 2154 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2154])
  * Hudson kept relaunching the slave under the "on-demand" retention strategy.
    (report [http://www.nabble.com/SlaveComputer.connect-Called-Multiple-Times-td23208903.html])
  * Extra slash (/) included in path to workspace copy of svn external.
    (issue 3533 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3533])
  * NPE prevents Hudson from starting up on some environments
    (report [http://www.nabble.com/Failed-to-initialisze-Hudson-%%3A-NullPointerException-td23252806.html])
  * Workspace deleted when subversion checkout happens.
    (issue 3580 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3580])
  * Hudson now handles unexpected failures in trigger plugins more gracefully.
  * Use 8K buffer to improve remote file copy performance.
    (issue 3524 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3524])
  * Hudson now has a CLI
  * Hudson's start up performance is improved by loading data concurrently.
  * When a SCM plugin is uninstalled, projects using it should fall back to "No SCM".
  * When polling SCMs, boolean parameter sets default value collectly.
  * Sidebar build descriptions will not have "..." appended unless they have been truncated.
    (issue 3541 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3541])
  * Workspace with symlink causes svn exception when updating externals.
    (issue 3532 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3532])
  * Hudson now started bundling ssh-slaves plugin out of the box.
  * Added an extension point to programmatically contribute a Subversion authentication credential.
    (report [http://www.nabble.com/Subversion-credentials-extension-point-td23159323.html])
  * You can now configure which columns are displayed in a view. "Last Stable" was also added as an optional column (not displayed by default).
    (issue 3465 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3465])
  * Preventive node monitoring like /tmp size check, swap space size check can be now disabled selectively.
    (issue 2596 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2596], issue 2552 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2552])
  * Per-project read permission support.
    (issue 2324 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2324])
  * Javadoc browsing broken since 1.297.
    (issue 3444 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3444])
  * Fixed a JNLP slave problem on JDK6u10 (and later)
  * Added @ExportedBean to DiskSpaceMonitorDescriptor#DiskSpace so that Remote API(/computer/api/) works
  * Fixed a Jelly bug in CVS after-the-fact tagging
  * Cross site scripting vulnerability in the search box.
    (issue 3415 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3415])
  * Auto-completion in the "copy job from" text box was not working.
    (issue 3396 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3396])
  * Allow descriptions for parameters
    (issue 2557 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2557])
  * support for boolean and choice parameters
    (issue 2558 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2558])
  * support for run parameters. Allows you to pick one run from a configurable project, and exposes the url of that run to the build.
  * JVM arguments of JNLP slaves can be now controlled.
    (issue 3398 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3398])
  * $HUDSON_HOME/userContent/ is now exposed under http://server/hudson/userContent/.
    (report [http://www.nabble.com/Is-it-possible-to-add-a-custom-page-Hudson--td22794858.html])
  * Fixed a plugin compatibility regression issue introduced in 1.296
    (issue 3436 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3436])
  * Programmatically created jobs started builds at #0 rather than #1.
    (issue 3361 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3361])
  * Drop-down combobox to select a repository browser all had the same title.
    (report [http://www.nabble.com/Possible-bug--Showing-%%22Associated-Mantis-Website%%22-in-scm-repository-browser-tt22786295.html])
  * Disk space monitoring was broken since 1.294.
    (issue 3381 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3381])
  * Native m2 support is moved to a plugin bundled out-of-the-box in the distribution
    (issue 3251 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3251])
  * Hudson now suggests to users to create a view if there are too many jobs but no views yet.
  * NPE in the global configuration of CVS.
    (issue 3382 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3382])
  * Generated RSS 2.0 feeds weren't properly escaping e-mail addresses.
  * Hudson wasn't capturing stdout/stderr from Maven surefire tests.
  * Improved the error diagnostics when retrieving JNLP file from CLI.
    (report [http://www.nabble.com/Install-jnlp-failure%%3A-java-IOException-to22469576.html])
  * Making various internal changes to make it easier to create custom job types.
  * Introduced a revised form field validation mechanism for plugins and core (FormValidation)
  * Hudson now monitors the temporary directory to forestall disk out of space problems.
  * XML API now exposes information about modules in a native Maven job.
  * ZIP archives created from workspace contents will render properly in Windows' built-in "compressed folder" views.
    (issue 3294 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3294])
  * Fixed an infinite loop (eventually leading to OOME) if Subversion client SSL certificate authentication fails.
    (report [http://www.nabble.com/OutOfMemoryError-when-uploading-credentials-td22430818.html])
  * NPE from artifact archiver when a slave is down.
    (issue 3330 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3330])
  * Hudson now monitors the disk consumption of HUDSON_HOME by itself.
  * Fixed the possible "java.io.IOException: Not in GZIP format" problem when copying a file remotely.
    (issue 3134 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3134])
  * Tool location name in node-specific properties always showed first list entry instead of saved value.
    (issue 3264 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3264])
  * Parse the Subversion tunnel configuration properly.
    (report [http://www.nabble.com/Problem-using-external-ssh-client-td22413572.html])
  * Improved JUnit result display to handle complex suite setups involving non-unique class names.
    (issue 2988 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2988])
  * If the master is on Windows and slave is on Unix or vice versa, PATH environment variable was not properly handled.
  * Improved the access denied error message to be more human readable.
    (report [http://www.nabble.com/Trouble-in-logging-in-with-Subversion-td22473876.html])
  * api/xml?xpath=...&wrapper=... behaved inconsistently for results of size 0 or 1.
    (issue 3267 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3267])
  * Fixed NPE in the WebSphere start up.
    (issue 3069 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3069])
  * Fixed a bug in the parsing of the -tunnel option in slave.jar
    (issue 2869 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2869])
  * Updated XStream to support FreeBSD.
    (issue 2356 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2356])
  * Only show last 150KB of console log in HTML view, with link to show entire log
    (issue 261 [https://hudson.dev.java.net/issues/show_bug.cgi?id=261])
  * Can specify build cause when triggering build remotely via token
    (issue 324 [https://hudson.dev.java.net/issues/show_bug.cgi?id=324])
  * Recover gracefully from failing to load winp.
    (report [http://www.nabble.com/Unable-to-load-winp.dll-td22423157.html])
  * Fixed a regression in 1.286 that broke findbugs and other plugins.
    (report [http://www.nabble.com/tasks-and-compiler-warnings-plugins-td22334680.html])
  * Fixed backward compatibility problem with the old audit-trail plugin and the old promoted-build plgin.
    (report [http://www.nabble.com/Problems-with-1.288-upgraded-from-1.285-td22360802.html], report [http://www.nabble.com/Promotion-Plugin-Broken-in-Build--1.287-td22376101.html])
  * On Solaris, ZFS detection fails if $HUDSON_HOME is on the top-level file system.
  * Fixed a regression in the fingerprinter & archiver interaction in the matrix project
    (report [http://www.nabble.com/1.286-version-and-fingerprints-option-broken-.-td22236618.html])
  * Added usage screen to slave.jar. Slaves now also do ping by default to proactively detect failures in the master. (from IRC)
  * Could not run java -jar hudson.war using JDK 5.
    (issue 3200 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3200])
  * Infinite loop reported when running on Glassfish.
    (issue 3163 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3163])
  * Hudson failed to poll multiple Subversion repository locations since 1.286.
    (issue 3168 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3168])
  * Avoid broken images/links in matrix project when some combinations are not run due to filter.
    (issue 3167 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3167])
  * Add back LDAP group query on uniqueMember (lost in 1.280) and fix memberUid query
    (issue 2256 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2256])
  * Adding a slave node was not possible in French locale
    (issue 3156 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3156])
  * External builds were shown in Build History of all slave nodes
  * Renewed the certificate for signing hudson.war
    (report [http://www.nabble.com/1.287%%3A-java.lang.IllegalStateException%%3A-zip-file-closed-td22272400.html])
  * Do not archive empty directories.
    (issue 3227 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3227])
  * Hyperlink URLs in JUnit output.
    (issue 3225 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3225])
  * Automatically lookup email addresses in LDAP when LDAP authentication is used
    (issue 1475 [https://hudson.dev.java.net/issues/show_bug.cgi?id=1475])
  * The project-based security configuration didn't survive the configuration roundtrip since 1.284.
    (issue 3116 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3116])
  * Form error check on the new node was checking against job names, not node names.
    (issue 3176 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3176])
  * Plugin class is now optional.
  * Display a more prominent message if Hudson is going to shut down.
  * Builds blocked by the shut-down process now reports its status accordingly.
    (issue 3152 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3152])
  * Custom widgets were not rendered.
    (issue 3161 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3161])
  * Fixed a regression in 1.286 about handling form field validations in some plugins.
    (report [http://www.nabble.com/1.286-version-and-description-The-requested-resource-%%28%%29-is-not--available.-td22233801.html])
  * Improved the robustness in changlog computation when a build fails at check out.
    (report [http://www.nabble.com/CVSChangeLogSet.parse-yields-SAXParseExceptions-when-parsing-bad-*AccuRev*-changelog.xml-files-td22213663.html])
  * Fixed a bug in loading winp.dll when directory names involve whitespaces.
    (issue 3111 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3111])
  * Switched to Groovy 1.6.0, and did so in a way that avoids some of the library conflicts, such as asm.
    (report [http://www.nabble.com/Hudson-library-dependency-to-asm-2.2-td22233542.html])
  * Fixed NPE in Pipe.EOF(0)
    (issue 3077 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3077])
  * Improved error handling when Maven fails to start correctly in the m2 project type.
    (issue 2394 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2394])
  * Improved the error handling behaviors when non-serializable exceptions are involved in distributed operations.
    (issue 1041 [https://hudson.dev.java.net/issues/show_bug.cgi?id=1041])
  * Allow unassigning a job from a node after the last slave node is deleted.
    (issue 2905 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2905])
  * Fix "Copy existing job" autocomplete on new job page if any existing job names have a quote character.
  * Keep last successful build (or artifacts from it) now tries to keep last stable build too.
    (issue 2417 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2417])
  * LDAP authentication realm didn't support the built-in "authenticated" role.
    (report [http://www.nabble.com/Hudson-security-issue%%3A-authenticated-user-does-not-work-td22176902.html])
  * Added RemoteCause for triggering build remotely.
  * Hudson is now capable of launching Windows slave headlessly and remotely.
  * Better SVN polling support - Trigger a build for changes in certain regions.
    (issue 3124 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3124])
  * New extension point NodeProperty.
  * Internal restructuring for reducing singleton dependencies and automatic Descriptor discovery.
  * Build parameter settings are lost when you save the configuration. Regression since 1.284.
    (report [http://www.nabble.com/Build-parameters-are-lost-in-1.284-td22077058.html])
  * Indicate the executors of offline nodes as "offline", not "idle" to avoid confusion.
    (issue 2263 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2263])
  * Fixed a boot problem in Solaris < 10.
    (issue 3044 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3044])
  * In matrix build, show axes used for that build rather than currently configured axes.
  * Don't let containers persist authentication information, which may not deserialize correctly.
    (report [http://www.nabble.com/ActiveDirectory-Plugin%%3A-ClassNotFoundException-while-loading--persisted-sessions%%3A-td22085140.html])
  * Always use some timeout value for Subversion HTTP access to avoid infinite hang in the read.
  * Better CVS polling support - Trigger a build for changes in certain regions.
    (issue 3123 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3123])
  * ProcessTreeKiller was not working on 64bit Windows, Windows 2000, and other minor platforms like PowerPC.
    (issue 3050 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3050], issue 3060 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3060])
  * Login using Hudson's own user database did not work since 1.283.
    (issue 3043 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3043])
  * View of parameters used for a build did not work since 1.283.
    (issue 3061 [https://hudson.dev.java.net/issues/show_bug.cgi?id=3061])
  * Equal quiet period and SCM polling schedule could trigger extra build with no changes.
    (issue 2671 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2671])
  * Fix localization of some messages in build health reports.
    (issue 1670 [https://hudson.dev.java.net/issues/show_bug.cgi?id=1670])
  * Fixed a possible memory leak in the distributed build.
    (report [http://www.nabble.com/Possible-memory-leak-in-hudson.remoting.ExportTable-td12000299.html])
  * Suppress more pointless error messages in Winstone when clients cut the connection in the middle.
    (report [http://www.nabble.com/javax.servlet.%%2CServletException-td22002253.html])
  * Fixed a concurrent build problem in the parallel parameterized build.
    (issue 2997 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2997])
  * Maven2 job types didn't handle property-based profile activations correctly.
    (issue 1454 [https://hudson.dev.java.net/issues/show_bug.cgi?id=1454])
  * LDAP group permissions were not applied when logged in via remember-me cookie.
    (issue 2329 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2329])
  * Record how each build was started and show this in build page and remote API.
    (issue 291 [https://hudson.dev.java.net/issues/show_bug.cgi?id=291])
  * Added the --version option to CLI to show the version. The version information is also visible in the help screen.
    (report [http://www.nabble.com/Naming-of-the-Hudson-Warfile-td21921859.html])
  * Hudson's own user database now stores SHA-256 encrypted passwords instead of reversible base-64 scrambling.
    (issue 2381 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2381])
  * Built-in servlet container no longer reports pointless error messages when clients abort the TCP connection.
    (report [http://www.nabble.com/Hudson-Evaluation---Log-output-td21943690.html])
  * On Solaris, Hudson now supports the migration of the data to ZFS.
  * Plugin manager now honors the plugin URL from inside .hpi, not just from the update center.
    (report [http://www.nabble.com/Plugin-deployment-issues-td21982824.html])
  * Hudson will now kill all the processes that are left behind by a build, to maintain the stability of the cluster.
    (issue 2729 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2729])
  * Matrix security username/groupname validation required admin permission even in project-specific permissions
  * Fixed a JavaScript bug in slave configuration page when locale is French.
    (report [http://www.nabble.com/Javascript-problem-for-french-version-td21875477.html])
  * File upload from HTML forms doesn't work with Internet Explorer.
    (report [http://www.nabble.com/IE%%E3%%81%%8B%%E3%%82%%89%%E3%%81%%AE%%E3%%83%%95%%E3%%82%%A1%%E3%%82%%A4%%E3%%83%%AB%%E3%%82%%A2%%E3%%83%%83%%E3%%83%%97%%E3%%83%%AD%%E3%%83%%BC%%E3%%83%%89-td21853837.html])
  * Jobs now expose JobProperties via the remote API.
    (issue 2990 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2990])
  * Hudson now responds to a UDP packet to port 33848 for better discovery.
  * Improved the error diagnostics when XML marshalling fails.
    (report [http://www.nabble.com/Trouble-configuring-Ldap-in-Hudson-running-on-JBoss-5.0.0.GA-td21849403.html])
  * Remote API access to test results was broken since 1.272.
    (issue 2760 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2760])
  * Fixed problem in 1.280 where saving top-level settings with LDAP authentication resulted in very large config.xml
    (issue 2958 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2958])
  * Username/groupname validation added in 1.280 had broken images, and got exception in groupname lookup with LDAP.
    (issue 2959 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2959])
  * hudson.war now supports the --daemon option for forking into background on Unix.
  * Remote API supports stdout/stderr from test reports.
    (issue 2760 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2760])
  * Fixed unnecessary builds triggered by SVN polling
    (issue 2825 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2825])
  * Hudson can now run on JBoss5 without any hassle.
    (issue 2831 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2831])
  * Matrix security configuration now validates whether the username/groupname are valid.
  * "Disable build" checkbox was moved to align with the rest of the checkboxes.
    (issue 2951 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2951])
  * Added an extension point to manipulate Launcher used during a build.
  * Fixed a security related regression in 1.278 where authorized users won't get access to the system.
    (issue 2930 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2930])
  * Possible subversion polling problems due to debug code making polling take one minute, since 1.273.
    (issue 2913 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2913])
  * Computer page now shows the executor list of that computer.
    (issue 2925 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2925])
  * Maven surefire test recording is made robust when clocks are out of sync
    (report [http://www.nabble.com/Hudson---test-parsing-failure-tt21520694.html])
  * Matrix project type now supports sparse matrix.
    (issue 2813 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2813])
  * Plugins can now add a new column to the list view.
    (issue 2862 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2862])
  * The "administer" permission should allow a person to do everything.
    (discussion [http://www.nabble.com/Deleting-users-from-Matrix-Based-security-td21566030.html#a21571137])
  * Parameterized projects supported for matrix configurations
    (issue 2903 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2903])
  * Improved error recovery when a build fails and javadoc/artifacts weren't created at all.
  * Support programmatic scheduling of parameterized builds by HTTP GET or POST to /job/.../buildWithParameters
    (issue 2409 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2409])
  * Create "lastStable" symlink on the file system to point to the applicable build.
    (report [http://www.nabble.com/lastStable-link-td21582859.html])
  * "Installing slave as Windows service" feature was broken since 1.272
    (issue 2886 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2886])
  * Top level People page showed no project information since 1.269.
    (report [http://www.nabble.com/N-A-in-%%22Last-Active%%22-column-in--people-page.-tp21553593p21553593.html])
  * Slave configuration page always showed "Utilize as much as possible" instead of saved value since 1.271.
    (issue 2878 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2878])
  * Require build permission to submit results for an external job.
    (issue 2873 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2873])
  * With the --logfile==/path/to/logfile option, Hudson now reacts SIGALRM and reopen the log file. This makes Hudson behave nicely wrt log rotation.
  * Ok button on New View page was always disabled when not using project-specific permissions.
    (issue 2809 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2809])
  * Fixed incompatibility with JBossAS 5.0.
    (issue 2831 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2831])
  * Unit test in plugin will now automatically load the plugin.
    (discussion [http://www.nabble.com/patch%%3A-WithPlugin-annotation%%2C-usable-in-plugin-projects-td21444423.html])
  * Added direct configuration links for the "manage nodes" page.
    (discussion [http://www.nabble.com/How-to-manage-slaves-after-upgrade-to-1.274-td21480759.html])
  * If a build has no change, e-mail shouldn't link to the empty changeset page.
  * Display of short time durations failed on locales with comma as fraction separator.
    (issue 2843 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2843])
  * Saving main Hudson config failed if more than one plugin used Plugin/config.jelly.
  * Added a scheduled slave availability.
  * Hudson supports auto-upgrade on Solaris when managed by SMF [http://docs.sun.com/app/docs/doc/819-2252/smf-5?a=view].
  * Removed unused spring-support-1.2.9.jar from Hudson as it was interfering with the Crowd plugin.
    (report [http://www.nabble.com/Getting-NoSuchClassDefFoundError-for-ehcache-tt21444908.html])
  * Debian package now has the RUN_STANDALONE switch to control if hudson should be run as a daemon.
    (report [http://www.nabble.com/Debian-repo-tt21467102.html])
  * Failure to compute Subversion changelog should result in a build failure.
    (issue 2461 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2461])
  * XML API caused NPE when xpath=... is specified.
    (issue 2828 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2828])
  * Artifact/workspace browser was unable to serve directories/files that contains ".." in them.
    (report [http://www.nabble.com/Status-Code-400-viewing-or-downloading-artifact-whose-filename-contains-two-consecutive-periods-tt21407604.html])
  * Hudson renders durations in milliseconds if the total duration is very short.
    (report [http://www.nabble.com/Unit-tests-duration-in-milliseconds-tt21417767.html])
  * On Unix, Hudson will contain symlinks from build numbers to their corresponding build directories.
  * Load statistics chart had the blue line and the red line swapped.
    (issue 2818 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2818])
  * Artifact archiver and workspace browser didn't handle filenames with spaces since 1.269.
    (issue 2793 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2793])
  * The executor status and build queue weren't updated asynchronously in the "manage slaves" page.
    (issue 2821 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2821])
  * If SCM polling activities gets clogged, Hudson shows a warning in the management page.
    (issue 1646 [https://hudson.dev.java.net/issues/show_bug.cgi?id=1646])
  * Added AdministrativeMonitor extension point for improving the autonomous monitoring of the system in Hudson.
  * Sometimes multi-line input field is not properly rendered as a text area.
    (issue 2816 [https://hudson.dev.java.net/issues/show_bug.cgi?id=2816])
  * Hudson wasn't able to detect when .NET was completely missing.
    (report [http://www.nabble.com/error-installing-hudson-as-a-windows-service-tt21378003.html])
  * Fixed a bug where the "All" view can be lost if upgraded from pre-1.269 and the configuration is reloaded from disk without saving.
    (report [http://www.nabble.com/all-view-disappeared-tt21380658.html])
  * If for some reason "All" view is deleted, allow people to create it again.
    (report [http://www.nabble.com/all-view-disappeared-tt21380658.html])
  * JNLP slave agents is made more robust in the face of configuration errors.
    (report [http://d.hatena.ne.jp/w650/20090107/1231323990])
  * Upgraded JNA to 3.0.9 to support installation as a service on 64bit Windows.
    (report [http://www.nabble.com/error-installing-hudson-as-a-windows-service-tt21378003.html])
  * Remote XML API now suports the 'exclude' query parameter to remove nodes that match the specified XPath.
    (report [http://d.hatena.ne.jp/masanobuimai/20090109#1231507972])
* Sat Jan 10 2009 guru@unixtech.be
- update to 1.271:
  * Fix URLs in junit test reports to handle some special characters. (https://hudson.dev.java.net/issues/show_bug.cgi?id=1768)
  * Project name for external job was not shown in Build History.
  * SecurityRealms can now better control the servlet filter chain. (http://www.nabble.com/proposed-patch-to-expose-filters-through-SecurityRealms-tt21062397.html)
  * Configuration of slaves are moved to individual pages.
  * Hudson now tracks the load statistics on slaves and labels.
  * Labels can be now assigned to the master. (https://hudson.dev.java.net/issues/show_bug.cgi?id=754)
  * Added cloud support and internal restructuring to deal with this change. Note that a plugin is required to use any particular cloud implementation.
* Tue Jan  6 2009 guru@unixtech.be
- update to 1.270:
  * Hudson version number was not shown at bottom of pages since 1.269.
  * Hudson system message was not shown on main page since 1.269.
  * Top level /api/xml wasn't working since 1.269. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2779)
  * Fix posting of external job results. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2786)
  * Windows service wrapper messes up environment variables to lower case. (http://www.nabble.com/Hudson-feedback-(and-windows-service-variable-lower-case-issue-continuation)-td21206812.html)
  * Construct proper Next/Previous Build links even if request URL has extra slashes. (https://hudson.dev.java.net/issues/show_bug.cgi?id=1457)
  * Subversion polling didn't notice when you change your project configuration. (http://www.nabble.com/Proper-way-to-switch---relocate-SVN-tree---tt21173306.html)
* Tue Jan  6 2009 guru@unixtech.be
- update to 1.269:
  * Manually making a Maven project as upstream and free-style project as a downstream wasn't working. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2778)
  * Allow BuildWrapper plugins to contribute project actions to Maven2 jobs (https://hudson.dev.java.net/issues/show_bug.cgi?id=2777)
  * Error pages should return non-200 HTTP status code.
  * Logger configuration wasn't working since 1.267.
  * Fix artifact archiver and workspace browser to handle filenames that need URL-encoding. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2379)
  * Only show form on tagBuild page if user has tag permission. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2366)
  * Don't require admin permission to view node list page, just hide some columns from non-admins. (https://hudson.dev.java.net/issues/show_bug.cgi?id=1207)
  * Fix login redirect when anonymous user tries to access some pages. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2408)
  * Redirect up to build page if next/previousBuild link jumps to an action not present in that build. (https://hudson.dev.java.net/issues/show_bug.cgi?id=117)
  * Subversion checkout/update now supports fixed revisions. (https://hudson.dev.java.net/issues/show_bug.cgi?id=262)
  * Views are now extensible and can be contributed by plugins. (https://hudson.dev.java.net/issues/show_bug.cgi?id=234)
* Sun Dec 21 2008 guru@unixtech.be
- update to 1.266:
  * If there was no successful build, test result trend wasn't displayed. (http://www.nabble.com/Test-Result-Trend-plot-not-showing-td21052568.html)
  * Windows service installer wasn't handling the situation correctly if Hudson is started at the installation target. (http://www.nabble.com/how-to-setup-hudson-%%2B-cvsnt-%%2B-ssh-as-a-service-on-windows--tp20902739p20902739.html)
  * Always display the restart button on the update center page, if the current environment supports it. (http://www.nabble.com/Restarting-Hudson-%%28as-Windows-service%%29-from-web-UI--td21069038.html)
  * slave.jar now supports the -noCertificateCheck to bypass (or cripple) HTTPS certificate examination entirely. Useful for working with self-signed HTTPS that are often seen in the intranet. (http://www.nabble.com/Getting-hudson-slaves-to-connect-to-https-hudson-with-self-signed-certificate-td21042660.html)
  * Add extension point to allow alternate update centers. (http://hudson.dev.java.net/issues/show_bug.cgi?id=2732)
  * Improved accessibility for visually handicapped.
* Fri Dec 12 2008 guru@unixtech.be
- update to 1.263:
  * Fixed a problem in handling of '\' introduced in 1.260. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2584)
  * Fixed possible NPE when rendering a build history after a slave removal. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2622)
  * JNLP descriptor shouldn't rely on the manually configured root URL for HTTP access. (http://www.nabble.com/Moved-master-to-new-machine%%2C-now-when-creating-new-slave%%2C-jnlp-tries-to-connect-to-old-machine-td20465637.html)
  * Use unique id which javascript uses when removing users from Matrix-based securoty. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2652)
  * Hudson is now made 5 times more conservative in marking an item in the queue as stuck. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2618)
  * Improved the variable expansion mechanism in handling of more complex cases.
  * XPath matching numbers and booleans in the remote API will render text/plain, instead of error.
* Sat Nov 15 2008 guru@unixtech.be
- update to 1.262:
  * Fixed a Java6 dependency crept in in 1.261. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2623)
  * Setting up a manual dependency from a freestyle project to a Maven project didn't work. (http://ml.seasar.org/archives/operation/2008-November/004003.html)
  * Use Project Security setting wasn't being persisted. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2305)
  * Slave installed as a Windows service didn't attempt automatic reconnection when initiail connection fails. (http://www.nabble.com/Loop-Slave-Connection-Attempts-td20471873.html)
  * Maven builder has the advanced section in the freestyle job, just like Ant builder. (http://ml.seasar.org/archives/operation/2008-November/004003.html)
* Wed Nov 12 2008 guru@unixtech.be
- update to 1.261:
  * Using Maven inside a matrix project, axes were not expanded in the maven command line. (http://ml.seasar.org/archives/operation/2008-November/003996.html)
  * Fixed authentication so that login successfully after signing up. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2594)
  * Fixed Project-based Matrix Authorization Strategy reverting to Matrix Authorization Strategy after restarting Hudson (https://hudson.dev.java.net/issues/show_bug.cgi?id=2305)
  * LDAP membership query now recognizes uniqueMember and memberUid (https://hudson.dev.java.net/issues/show_bug.cgi?id=2256)
  * If a build appears to be hanging for too long, Hudson turns the progress bar to red.
* Thu Nov  6 2008 guru@unixtech.be
- update to 1.260:
  * Fixed tokenization handling in command line that involves quotes (like -Dabc="abc def" (https://hudson.dev.java.net/issues/show_bug.cgi?id=2584)
  * Hudson wasn't using persistent HTTP connections properly when using Subversion over HTTP.
  * Fixed svn:executable handling on 64bit Linux systems.
  * When a build is aborted, Hudson now kills all the descendant processes recursively to avoid run-away processes. This is available on Windows, Linux, and Solaris. Contributions for other OSes welcome.
  * Improved error diagnostics in the update center when the proxy configuration is likely necessary. (http://www.nabble.com/update-center-proxy-td20205568.html)
  * Improved error diagnostics when a temp file creation failed. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2587)
* Wed Nov  5 2008 guru@unixtech.be
- update to 1.259:
  * If a job is cancelled while it's already in the queue, remove the job from the queue. (http://www.nabble.com/Disabled-jobs-and-triggered-builds-td20254776.html)
  * Installing Hudson as a Windows service requires .NET 2.0 or later. Hudson now checks this before attempting a service installation.
  * On Hudson installed as Windows service, Hudson can now upgrade itself when a new version is available.
  * Hudson can now be pre-packaged with plugins. (http://www.nabble.com/How-can-I-distribute-Hudson-with-my-plug-in-td20278601.html)
  * Supported the javadoc:aggregate goal (https://hudson.dev.java.net/issues/show_bug.cgi?id=2562)
  * Bundled StAX implementation so that plugins would have easier time using it. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2547)
* Thu Oct 30 2008 guru@unixtech.be
- update to 1.258:
  * Fixed a class incompatibility introduced in 1.257 that breaks TFS and ClearCase plugins. (http://www.nabble.com/Build-257-IncompatibleClassChangeError-td20229011.html)
  * Fixed a NPE when dealing with broken Action implementations. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2527)
* Wed Oct 29 2008 guru@unixtech.be
- update to 1.257:
  * Fixed an encoding issue when the master and a slave use incompatible encodings.
  * Permission matrix was missing tooltip text. (http://www.nabble.com/Missing-hover-text-for-matrix-security-permissions--td20140205.html)
  * Parameter expansion in Ant builder didn't honor build parameters. (http://www.nabble.com/Missing-hover-text-for-matrix-security-permissions--td20140205.html)
  * Added tooltip for 'S' and 'W' in the top page for showing what it is. (http://www.nabble.com/Re%%3A-What-are-%%27S%%27-and-%%27W%%27-in-Hudson-td20199851.html)
  * Expanded the remote API to disable/enable jobs remotely.
* Sat Oct 25 2008 guru@unixtech.be
- update to 1.256:
  * Hudson had two dom4j.jar that was causing a VerifyError in WebSphere. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2435)
  * Fixed NPE in case of changelog.xml data corruption (https://hudson.dev.java.net/issues/show_bug.cgi?id=2428)
  * Improved the error detection when a Windows path is given to a Unix slave. (http://www.nabble.com/windows-32-bit-hudson-talking-to-linux-64-bit-slave--td19755708.html)
  * Automatic dependency management for Maven projects can be now disabled. (https://hudson.dev.java.net/issues/show_bug.cgi?id=1714)
* Thu Oct  2 2008 guru@unixtech.be
- update to 1.255:
  * Project-level ACL matrix shouldn't display "CREATE" permission. (http://www.nabble.com/Project-based-authorization-%%3A-Create-permission-td19729677.html)
  * Fixed the serialized form of project-based matrix authorization strategy.
  * Fixed a bug where Hudson installed as service gets killed when the interactive user logs off. (http://www.nabble.com/Project-based-authorization-%%3A-Create-permission-td19729677.html)
  * Timer-scheduled build shouldn't honor the quiet period. (http://www.nabble.com/Build-periodically-Schedule-time-difference-vs-actual-execute-time-td19736583.html)
  * Hudson slave launched from JNLP is now capable of installing itself as a Windows service.
* Sat Sep 27 2008 guru@unixtech.be
- update to 1.254:
  * IllegalArgumentException in DeferredCreationLdapAuthoritiesPopulator if groupSearchBase is omitted. (http://www.nabble.com/IllegalArgumentException-in-DeferredCreationLdapAuthoritiesPopulator-if-groupSearchBase-is-omitted-td19689475.html)
  * Fixed "Failed to tag" problem when tagging some builds (https://hudson.dev.java.net/issues/show_bug.cgi?id=2213)
  * Hudson is now capable of installing itself as a Windows service.
  * Improved the diagnostics of why Hudson needs to do a fresh check out. (http://www.nabble.com/Same-job-gets-rescheduled-again-and-again-td19662648.html)
* Thu Sep 25 2008 guru@unixtech.be
- update to 1.253:
  * Fixed FormFieldValidator check for Windows path (https://hudson.dev.java.net/issues/show_bug.cgi?id=2334)
  * Support empty cvs executable and Shell executable on configure page (https://hudson.dev.java.net/issues/show_bug.cgi?id=1851)
  * Fixed parametric Project build when scheduled automatically from SCM changes (https://hudson.dev.java.net/issues/show_bug.cgi?id=2336)
  * "Tag this build" link shouldn't show up in the navigation bar if the user doesn't have a permission. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2380)
  * Improved LDAP support so that it doesn't rely on ou=groups.
  * Project-based security matrix shouldn't show up in project config page unless the said option is enabled in the global config
  * Fixed NPE during the sign up of a new user (https://hudson.dev.java.net/issues/show_bug.cgi?id=2376)
  * Suppress the need for a scroll-bar on the configure page when the PATH is very long (https://hudson.dev.java.net/issues/show_bug.cgi?id=2317)
  * Now UserProperty objects can provide a summary on a user's main page. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2331)
  * Validate Maven installation directory just like Ant installation (https://hudson.dev.java.net/issues/show_bug.cgi?id=2335)
  * Show summary.jelly files for JobProperties in the project page (https://hudson.dev.java.net/issues/show_bug.cgi?id=2398)
  * Improvements in French and Japanese localization.
  * JNLP slaves now support port tunneling for security-restricted environments.
  * slave.jar now supports a proactive connection initiation (like JNLP slaves.) This can be used to replace JNLP slaves, especially when you want to run it as a service.
  * Added a new extension to define permalinks
  * Supported a file submission as one of the possible parameters for a parameterized build.
  * The logic to disable slaves based on its response time is made more robust, to ignore temporary spike.
  * Improved the robustness of the loading of persisted records to simplify plugin evolution.
* Wed Sep  3 2008 guru@unixtech.be
- update to 1.252:
  * Fixed a queue persistence problem where sometimes executors die with NPEs.
  * PageDecorator with a global.jelly is now shown in the System configuration page (https://hudson.dev.java.net/issues/show_bug.cgi?id=2289)
  * On security-enabled Hudson, redirection for a login didn't work correctly since 1.249. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2290)
  * Hudson didn't give SCMs an opportunity to clean up the workspace before project deletion. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2271)
  * Subversion SCM enhancement for allowing parametric builds on Tags and Branches. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2298)
* Sat Aug 23 2008 guru@unixtech.be
- update to 1.251:
  * JavaScript error in the system configuration prevents a form submission. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2260)
* Sat Aug 23 2008 guru@unixtech.be
- update to 1.250:
  * Fixed NPE in the workspace clean up thread when the slave is offline. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2221)
  * Hudson was still using deprecated configure methods on some of the extension points. (http://www.nabble.com/Hudson.configure-calling-deprecated-Descriptor.configure-td19051815.html)
  * Abbreviated time representation in English (e.g., "seconds" -> "secs") to reduce the width of the job listing. (https://hudson.dev.java.net/issues/show_bug.cgi?id=2251)
  * Added LDAPS support (https://hudson.dev.java.net/issues/show_bug.cgi?id=1445)
* Wed Aug 20 2008 guru@unixtech.be
- update to 1.249:
  * JNLP slave agent fails to launch when the anonymous user doesn't have a read access. (http://www.nabble.com/Launching-slave-by-JNLP-with-Active-Directory-plugin-and-matrix-security-problem-td18980323.html)
  * Trying to access protected resources anonymously now results in 401 status code, to help programmatic access.
  * When the security realm was delegated to the container, users didn't have the built-in "authenticated" role.
  * Fixed IllegalMonitorStateException (https://hudson.dev.java.net/issues/show_bug.cgi?id=2230)
  * Intorduced a mechanism to perform a bulk change to improve performance (and in preparation of version controlling config files)
