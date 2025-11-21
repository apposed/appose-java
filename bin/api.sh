#!/bin/sh

cd "$(dirname "$0")/.."

# Check for required cross-implementation script.
postprocessScript="../appose/bin/postprocess-api.py"
if [ ! -f "$postprocessScript" ]; then
  echo "Error: $postprocessScript not found" >&2
  echo "Please ensure appose repository is cloned as a sibling directory." >&2
  exit 1
fi

if [ ! -d target/dependency ]; then
  echo "Copying dependencies..." >&2
  mvn -q dependency:copy-dependencies -DincludeScope=test
fi

# Recompile DumpApi.java to ensure latest changes are used.
echo "Compiling DumpApi..." >&2
mvn -q test-compile

# Clean old api files.
rm -rf api

# Run DumpApi on source directories.
# Output will be written to api/appose/*.api files
java \
  -cp 'target/dependency/*:target/test-classes' \
  org.apposed.appose.DumpApi api src/main/java src/test/java

# Post-process API: normalize | None to ?, expand optional parameters.
python3 "$postprocessScript" api
