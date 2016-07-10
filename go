#!/bin/bash -x

# This really should be a Makefile

# Set a global classpath
export CLASSPATH=".:/Users/jf/.m2/repository/org/apache/httpcomponents/httpclient/4.5.2/httpclient-4.5.2.jar:/Users/jf/.m2/repository/org/apache/httpcomponents/httpcore/4.4.4/httpcore-4.4.4.jar:/Users/jf/.m2/repository/commons-logging/commons-logging/1.2/commons-logging-1.2.jar"

# Compile it IF we need to
if [ \! -e TestTcpHalfOpen.class -o TestTcpHalfOpen.java -nt TestTcpHalfOpen.class ]; then
	rm -f TestTcpHalfOpen*.class
	javac -g -Werror TestTcpHalfOpen.java
else
	true
fi || \
exit 1

# Prepare our list of args we will pass to the java executable
if [ $# -eq 0 ]; then
	args="http://www.github.com/thread0 http://www.github.com/thread1"
else
	args="$@"
fi

# Run the thing
java \
	-Djavax.net.ssl.trustStore=StartCom\ Certification\ Authority.jks \
	TestTcpHalfOpen \
	$args
