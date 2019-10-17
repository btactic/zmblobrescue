#!/bin/bash

JARPATH="/opt/zimbra/lib/jars"
JARS="\
commons-codec-1.7.jar \
zimbracommon.jar \
zimbrastore.jar \
commons-cli-1.2.jar \
zimbrasoap.jar \
dom4j-1.5.2.jar \
guava-13.0.1.jar \
log4j-1.2.16.jar \
commons-httpclient-3.1.jar \
zimbraclient.jar \
commons-logging.jar \
zimbra-native.jar \
json.jar \
commons-pool-1.6.jar \
commons-dbcp-1.4.jar \
mysql-connector-java-5.1.29-bin.jar \
concurrentlinkedhashmap-lru-1.3.1.jar \
unboundid-ldapsdk-2.3.5-se.jar \
javamail-1.4.5.jar \
lucene-core-3.5.0.jar \
memcached-2.6.jar \
"
RUN_CLASSPATH=""
for njar in $JARS ; do
    if [ "${RUN_CLASSPATH}" = "" ] ; then
        SEPARATOR=""
    else
        SEPARATOR=":"
    fi
    RUN_CLASSPATH="${RUN_CLASSPATH}${SEPARATOR}${JARPATH}/${njar}"
done
RUN_CLASSPATH=".:${RUN_CLASSPATH}" # Add current dir


java -classpath "${RUN_CLASSPATH}" com.zimbra.cs.store.file.BlobConsistencyRescue "$@"
