# Netscaler CLI

This is a simple command line interface for accessing a Netscaler load balancer.  It is currently alpha software, so use with caution.

# Installing

The command line tools can be installed with:

    gem install netscaler-cli

# Using

The following commands are currently a part of the system:

  * *netscaler vserver* -- An interface for enabling, disabling, querying, and binding responder policies to a specific virtual server.
  * *netscaler service* -- An interface for enabling, disabling, querying, and binding servers to specific services.
  * *netscaler server*  -- An interface for enabling, disabling, querying, and binding servers to virtual servers.
  
Each command requires at least the --netscaler flag (which can be the full netscaler host name in the configuration file, or its alias, see below).

# Configuration

All of the commands rely upon a configuration file in the YAML format.  By default, it looks for a file in your home directory

    ~/.netscaler-cli.yml

Each load balancer requires an entry in the file in the form:

    netscaler.loadbalancer.somecompany.com:
       username: 'some.username'
       password: 'super!duper!secret!'
       alias:  prod
       version: '9.2'

Multiple entries can be in the file; the password and the alias settings are not required.  An alias can be used as a shortcut name on the command line for a particular netscaler server.  However, if no password is given in the file for a given configuration, the tool will ask you for it.

The version information is optional, but breaking API changes were introduced in version 9.2 of Netscaler software, so some commands may fail if this isn't set for this Netscaler software version.
