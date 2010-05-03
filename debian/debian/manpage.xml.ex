<?xml version='1.0' encoding='ISO-8859-1'?>
<!DOCTYPE refentry PUBLIC "-//OASIS//DTD DocBook XML V4.2//EN"
"http://www.oasis-open.org/docbook/xml/4.2/docbookx.dtd" [

<!--

Process this file with an XSLT processor: `xsltproc \
-''-nonet /usr/share/sgml/docbook/stylesheet/xsl/nwalsh/\
manpages/docbook.xsl manpage.dbk'.  A manual page
<package>.<section> will be generated.  You may view the
manual page with: nroff -man <package>.<section> | less'.  A
typical entry in a Makefile or Makefile.am is:

DB2MAN=/usr/share/sgml/docbook/stylesheet/xsl/nwalsh/\
manpages/docbook.xsl
XP=xsltproc -''-nonet

manpage.1: manpage.dbk
        $(XP) $(DB2MAN) $<
    
The xsltproc binary is found in the xsltproc package.  The
XSL files are in docbook-xsl.  Please remember that if you
create the nroff version in one of the debian/rules file
targets (such as build), you will need to include xsltproc
and docbook-xsl in your Build-Depends control field.

-->

  <!-- Fill in your name for FIRSTNAME and SURNAME. -->
  <!ENTITY dhfirstname "<firstname>FIRSTNAME</firstname>">
  <!ENTITY dhsurname   "<surname>SURNAME</surname>">
  <!-- Please adjust the date whenever revising the manpage. -->
  <!ENTITY dhdate      "<date>April 25, 2008</date>">
  <!-- SECTION should be 1-8, maybe w/ subsection other parameters are
       allowed: see man(7), man(1). -->
  <!ENTITY dhsection   "<manvolnum>SECTION</manvolnum>">
  <!ENTITY dhemail     "<email>kk@kohsuke.org</email>">
  <!ENTITY dhusername  "Kohsuke Kawaguchi">
  <!ENTITY dhucpackage "<refentrytitle>HUDSON</refentrytitle>">
  <!ENTITY dhpackage   "hudson">

  <!ENTITY debian      "<productname>Debian</productname>">
  <!ENTITY gnu         "<acronym>GNU</acronym>">
  <!ENTITY gpl         "&gnu; <acronym>GPL</acronym>">
]>

<refentry>
  <refentryinfo>
    <address>
      &dhemail;
    </address>
    <copyright>
      <year>2007</year>
      <holder>&dhusername;</holder>
    </copyright>
    &dhdate;
  </refentryinfo>
  <refmeta>
    &dhucpackage;

    &dhsection;
  </refmeta>
  <refnamediv>
    <refname>&dhpackage;</refname>

    <refpurpose>program to do something</refpurpose>
  </refnamediv>
  <refsynopsisdiv>
    <cmdsynopsis>
      <command>&dhpackage;</command>

      <arg><option>-e <replaceable>this</replaceable></option></arg>

      <arg><option>--example <replaceable>that</replaceable></option></arg>
    </cmdsynopsis>
  </refsynopsisdiv>
  <refsect1>
    <title>DESCRIPTION</title>

    <para>This manual page documents briefly the
      <command>&dhpackage;</command> and <command>bar</command>
      commands.</para>

    <para>This manual page was written for the &debian; distribution
      because the original program does not have a manual page.
      Instead, it has documentation in the &gnu;
      <application>Info</application> format; see below.</para>

    <para><command>&dhpackage;</command> is a program that...</para>

  </refsect1>
  <refsect1>
    <title>OPTIONS</title>

    <para>These programs follow the usual &gnu; command line syntax,
      with long options starting with two dashes (`-').  A summary of
      options is included below.  For a complete description, see the
      <application>Info</application> files.</para>

    <variablelist>
      <varlistentry>
        <term><option>-h</option>
          <option>--help</option>
        </term>
        <listitem>
          <para>Show summary of options.</para>
        </listitem>
      </varlistentry>
      <varlistentry>
        <term><option>-v</option>
          <option>--version</option>
        </term>
        <listitem>
          <para>Show version of program.</para>
        </listitem>
      </varlistentry>
    </variablelist>
  </refsect1>
  <refsect1>
    <title>SEE ALSO</title>

    <para>bar (1), baz (1).</para>

    <para>The programs are documented fully by <citetitle>The Rise and
      Fall of a Fooish Bar</citetitle> available via the
      <application>Info</application> system.</para>
  </refsect1>
  <refsect1>
    <title>AUTHOR</title>

    <para>This manual page was written by &dhusername; &dhemail; for
      the &debian; system (but may be used by others).  Permission is
      granted to copy, distribute and/or modify this document under
      the terms of the &gnu; General Public License, Version 2 any 
	  later version published by the Free Software Foundation.
    </para>
	<para>
	  On Debian systems, the complete text of the GNU General Public
	  License can be found in /usr/share/common-licenses/GPL.
	</para>

  </refsect1>
</refentry>

