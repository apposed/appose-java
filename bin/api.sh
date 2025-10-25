#!/bin/sh

if [ ! -d target/dependency ]; then
  echo "Copying dependencies..." >&2
  mvn -q dependency:copy-dependencies -DincludeScope=test
fi

testJar=$(ls target/appose-*-SNAPSHOT-tests.jar 2>/dev/null | head -1)

# Build the test JAR if necessary (contains DumpApi).
if [ ! -f "$testJar" ]; then
  echo "Compiling Java code..." >&2
  mvn -q -DskipTests package
  testJar=$(ls target/appose-*-SNAPSHOT-tests.jar 2>/dev/null | head -1)
  if [ -f "$testJar" ]; then
    ls -l "$testJar" >&2
  fi
fi

# Run DumpApi on source directories.
# Output will be written to api/appose/*.pyi files
java \
  -cp 'target/dependency/*':"$testJar" \
  org.apposed.appose.DumpApi api src/main/java src/test/java
