#!/bin/sh
# Starts Открытый диплом with the bundled runtime. The memory settings keep the
# program under 300 MB, measured in docs/prototype.md. Arguments are JVM options,
# for example -Dopendiplom.port=8091.
cd "$(dirname "$0")"
exec ./runtime/bin/java -Xmx128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k \
    -Dfile.encoding=UTF-8 "$@" -jar open-diplom.jar
