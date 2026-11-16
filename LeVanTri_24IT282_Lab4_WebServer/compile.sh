#!/bin/bash
cd "$(dirname "${BASH_SOURCE[0]}")" || exit 1
exec mvn -B compile org.apache.maven.plugins:maven-dependency-plugin:3.9.0:copy-dependencies -DincludeScope=runtime
