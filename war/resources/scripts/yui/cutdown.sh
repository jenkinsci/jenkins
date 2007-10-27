find . -name README | xargs rm
find . | grep \\.js | grep -v \\-debug | grep -v \\-min | xargs rm

