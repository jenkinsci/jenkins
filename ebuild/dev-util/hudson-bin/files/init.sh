#!/sbin/runscript

extra_commands="backup"

depend() {
    need net
    use dns logger mysql postgresql
}

HUDSON_PIDFILE=/var/run/hudson/hudson.pid
HUDSON_WAR=/usr/lib/hudson/hudson.war

RUN_AS=hudson

checkconfig() {
    if [ ! -n "$HUDSON_HOME" ] ; then
        eerror "HUDSON_HOME not configured"
        return 1
    fi
    if [ ! -d "$HUDSON_HOME" ] ; then
        eerror "HUDSON_HOME directory does not exist: $HUDSON_HOME"
        return 1
    fi
    if [ ! -n "$HUDSON_BACKUP" ] ; then
        eerror "HUDSON_BACKUP not configured"
        return 1
    fi
    if [ ! -d "$HUDSON_BACKUP" ] ; then
        eerror "HUDSON_BACKUP directory does not exist: $HUDSON_BACKUP"
        return 1
    fi
    return 0
}

start() {
    checkconfig || return 1

    JAVA_HOME=`java-config --jre-home`
    COMMAND=$JAVA_HOME/bin/java

    JAVA_PARAMS="$HUDSON_JAVA_OPTIONS -DHUDSON_HOME=$HUDSON_HOME -jar $HUDSON_WAR"

    # Don't use --daemon here, because in this case stop will not work
    PARAMS="--logfile=/var/log/hudson/hudson.log"
    [ -n "$HUDSON_PORT" ] && PARAMS="$PARAMS --httpPort=$HUDSON_PORT"
    [ -n "$HUDSON_DEBUG_LEVEL" ] && PARAMS="$PARAMS --debug=$HUDSON_DEBUG_LEVEL"
    [ -n "$HUDSON_HANDLER_STARTUP" ] && PARAMS="$PARAMS --handlerCountStartup=$HUDSON_HANDLER_STARTUP"
    [ -n "$HUDSON_HANDLER_MAX" ] && PARAMS="$PARAMS --handlerCountMax=$HUDSON_HANDLER_MAX"
    [ -n "$HUDSON_HANDLER_IDLE" ] && PARAMS="$PARAMS --handlerCountMaxIdle=$HUDSON_HANDLER_IDLE"
    [ -n "$HUDSON_ARGS" ] && PARAMS="$PARAMS $HUDSON_ARGS"

    if [ "$HUDSON_ENABLE_ACCESS_LOG" = "yes" ]; then
        PARAMS="$PARAMS --accessLoggerClassName=winstone.accesslog.SimpleAccessLogger --simpleAccessLogger.format=combined --simpleAccessLogger.file=/var/log/hudson/access_log"
    fi

    ebegin "Starting ${SVCNAME}"
    start-stop-daemon --start --quiet --background \
        --make-pidfile --pidfile $HUDSON_PIDFILE \
        --chuid $RUN_AS \
        --exec "${COMMAND}" -- $JAVA_PARAMS $PARAMS
    eend $?
}

stop() {
    ebegin "Stopping ${SVCNAME}"
    start-stop-daemon --stop --quiet --pidfile $HUDSON_PIDFILE
    eend $?
}

backup() {
    checkconfig || return 1

    DATE=`date +%Y%m%d-%H%M`
    tar -czvf $HUDSON_BACKUP/hudson-backup-$DATE.tar.gz -C $HUDSON_HOME .
}
