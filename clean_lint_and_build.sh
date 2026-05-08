#!/bin/bash
set -e
# Run spotlessApply serially to avoid the flaky ktlint InvocationTargetException
# triggered by parallel ktlint initialization in Spotless 8.x.
./gradlew clean spotlessApply --no-build-cache --no-parallel --max-workers=1 "$@"
./gradlew build --no-build-cache "$@"
