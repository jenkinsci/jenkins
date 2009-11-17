# norootforbuild

%define _prefix	%{_usr}/lib/hudson
%define workdir	%{_localstatedir}/lib/hudson

Name:		hudson
Version:	%{ver}
Release:	0
Summary:	Continous Build Server
Source:		https://hudson.dev.java.net/files/documents/2402/99798/hudson.war
Source1:	hudson.init.in
Source2:	hudson.sysconfig.in
Source3:	hudson.logrotate
URL:		https://hudson.dev.java.net/
Group:		Development/Tools/Building
License:	MIT/X License, GPL/CDDL, ASL2
BuildRoot:	%{_tmppath}/build-%{name}-%{version}
Requires:	java-sdk >= 1.5.0
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
%__install -D -m0644 "%{SOURCE0}" "%{buildroot}%{_prefix}/%{name}.war"
%__install -d "%{buildroot}%{workdir}"
%__install -d "%{buildroot}%{workdir}/plugins"

%__install -d "%{buildroot}/var/log/hudson"

%__install -D -m0755 "%{SOURCE1}" "%{buildroot}/etc/init.d/%{name}"
%__sed -i 's,@@WAR@@,%{_prefix}/%{name}.war,g' "%{buildroot}/etc/init.d/%{name}"
%__install -d "%{buildroot}/usr/sbin"
%__ln_s "../../etc/init.d/%{name}" "%{buildroot}/usr/sbin/rc%{name}"

%__install -D -m0600 "%{SOURCE2}" "%{buildroot}/var/adm/fillup-templates/sysconfig.%{name}"
%__sed -i 's,@@HOME@@,%{workdir},g' "%{buildroot}/var/adm/fillup-templates/sysconfig.%{name}"

%__install -D -m0644 "%{SOURCE3}" "%{buildroot}/etc/logrotate.d/%{name}"

%pre
/usr/sbin/groupadd -r hudson &>/dev/null || :
/usr/sbin/useradd -o -g hudson -s /bin/false -r -c "Hudson Continuous Build server" \
	-d "%{workdir}" hudson &>/dev/null || :

%post
%{fillup_only hudson}

%preun
%stop_on_removal hudson

%postun
%restart_on_update hudson
%insserv_cleanup

%clean
%__rm -rf "%{buildroot}"

%files
%defattr(-,root,root)
%{_prefix}/%{name}.war
%attr(0755,hudson,hudson) %dir %{workdir}
%attr(0755,hudson,hudson) %dir %{workdir}/plugins
%attr(0750,hudson,hudson) /var/log/hudson
%config /etc/logrotate.d/%{name}
%config /etc/init.d/%{name}
/usr/sbin/rc%{name}
/var/adm/fillup-templates/sysconfig.%{name}

%changelog
