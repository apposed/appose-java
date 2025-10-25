#!/bin/sh

if [ ! -d target/dependency ]; then
  echo "Copying dependencies..." >&2
  mvn -q dependency:copy-dependencies -DincludeScope=test
fi

testJar=$(echo target/appose-*-SNAPSHOT-tests.jar)

# Build the test JAR if necessary (contains DumpApi).
if [ ! -f "$testJar" ]; then
  echo "Compiling Java code..." >&2
  mvn -q -DskipTests package
  ls -l "$testJar" >&2
fi

testJar=$(echo target/appose-*-SNAPSHOT-tests.jar)

# Run DumpApi on source directories.
java \
  -cp 'target/dependency/*':"$testJar" \
  org.apposed.appose.DumpApi src/main/java src/test/java
