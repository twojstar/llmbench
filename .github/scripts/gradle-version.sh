#!/usr/bin/env bash
# Keep the CI Gradle version derived from the wrapper properties.
set -euo pipefail

version=$(sed -n 's#^distributionUrl=.*/gradle-\([0-9][A-Za-z0-9.-]*\)-\(bin\|all\)\.zip$#\1#p' \
  gradle/wrapper/gradle-wrapper.properties)
if [ -z "$version" ]; then
  echo "::error::could not parse a Gradle version out of gradle-wrapper.properties"
  exit 1
fi
echo "version=$version" >> "$GITHUB_OUTPUT"
