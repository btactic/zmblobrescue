#!/bin/bash
JARPATH="/opt/zimbra/lib/jars"
JARS="\
zimbracommon.jar \
zimbrastore.jar \
commons-cli-1.2.jar \
zimbrasoap.jar \
dom4j-1.5.2.jar \
guava-13.0.1.jar \
"
BUILD_CLASSPATH=""
CLASS_TARGET_DIR="com/zimbra/cs/store/file"
CLASS_NAME="BlobConsistencyRescue"
for njar in $JARS ; do
    if [ "${BUILD_CLASSPATH}" = "" ] ; then
        SEPARATOR=""
    else
        SEPARATOR=":"
    fi
    BUILD_CLASSPATH="${BUILD_CLASSPATH}${SEPARATOR}${JARPATH}/${njar}"
done


javac -classpath "${BUILD_CLASSPATH}" ${CLASS_NAME}.java
# TODO: Check if build was successful or not
if [ ! -d "${CLASS_TARGET_DIR}" ] ; then
    mkdir -p "${CLASS_TARGET_DIR}"
fi
cp ${CLASS_NAME}.class "${CLASS_TARGET_DIR}"/${CLASS_NAME}.class
