#! /bin/bash -e
# Additional wrapper, which adds custom environment options for the run (if needed)

# Lazy debug
extra_java_opts=()
if [[ "$DEBUG" ]] ; then
  extra_java_opts+=( \
    '-Xdebug' \
    '-Xrunjdwp:server=y,transport=dt_socket,address=5005,suspend=y' \
  )
fi

if [ ${#extra_java_opts[@]} -eq 0 ]; then
  if [  -n "$JAVA_OPTS" ] ; then
    export JAVA_OPTS="$JAVA_OPTS ${extra_java_opts[@]}"
  else
    export JAVA_OPTS="${extra_java_opts[@]}"
  fi
fi

exec /usr/local/bin/jenkins.sh "$@"
