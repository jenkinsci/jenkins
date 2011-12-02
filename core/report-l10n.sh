#!/bin/bash
# this script counts available localizations in the core
(
    for f in $(find src/main/resources -name '*.properties'); do
        f=$(basename "$f" | sed -n -e 's/.*\(\(_.._..\.\)\|\(_..\.\)\).*$/\1/p')
        echo $f
    done
) | sort | uniq
