For efficient debugging, set the system property "stapler.jelly.noCache" to true on the container.
For example in Tomcat, this can be done by:

  $ export CATALINA_OPTS="-Dstapler.jelly.noCache=true"
  $ catalina.sh run

This setting tells Stapler not to cache compiled Jelly scripts, so every change you make to
Jelly scripts will be reflected instantly without reloading the whole app (at the expense
of slow page rendering performance.) 