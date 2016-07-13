#!/usr/bin/env bash
set -euo pipefail
IFS=$'\t\n'
set -x

# Build the template itself
lein do clean, test

# Generate a project based on the template and run tests in it
cd target
# We don't need to install it to ~/.m2, because it's already available on the classpath
DEBUG=1 lein new friboo com.example/foo-bar
cd foo-bar
lein test
lein uberjar
touch scm-source.json
lein docker build
