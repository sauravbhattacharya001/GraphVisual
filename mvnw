#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# Maven Wrapper bootstrap script
# Downloads Maven if not present and delegates to it

set -e

# Resolve project base directory
MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(cd "$(dirname "$0")" && pwd)}"
MAVEN_WRAPPER_PROPERTIES="${MAVEN_PROJECTBASEDIR}/.mvn/wrapper/maven-wrapper.properties"

if [ ! -f "$MAVEN_WRAPPER_PROPERTIES" ]; then
  echo "Error: .mvn/wrapper/maven-wrapper.properties not found" >&2
  exit 1
fi

# Read distribution URL from properties
distributionUrl=$(grep '^distributionUrl=' "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)

if [ -z "$distributionUrl" ]; then
  echo "Error: distributionUrl not found in maven-wrapper.properties" >&2
  exit 1
fi

# Determine Maven home
MAVEN_USER_HOME="${MAVEN_USER_HOME:-${HOME}/.m2}"
WRAPPER_DIR="${MAVEN_USER_HOME}/wrapper/dists"
DIST_NAME=$(basename "$distributionUrl" .zip)
MAVEN_HOME="${WRAPPER_DIR}/${DIST_NAME}"

# Download and extract Maven if not present
if [ ! -d "${MAVEN_HOME}/bin" ]; then
  echo "Downloading Maven from: ${distributionUrl}" >&2
  mkdir -p "${WRAPPER_DIR}"
  TMPFILE=$(mktemp)
  if command -v curl > /dev/null 2>&1; then
    curl -fsSL -o "$TMPFILE" "$distributionUrl"
  elif command -v wget > /dev/null 2>&1; then
    wget -q -O "$TMPFILE" "$distributionUrl"
  else
    echo "Error: curl or wget required to download Maven" >&2
    rm -f "$TMPFILE"
    exit 1
  fi
  mkdir -p "${MAVEN_HOME}"
  unzip -q -o "$TMPFILE" -d "${WRAPPER_DIR}"
  rm -f "$TMPFILE"
fi

# Find Maven executable
if [ -x "${MAVEN_HOME}/bin/mvn" ]; then
  MVN="${MAVEN_HOME}/bin/mvn"
else
  # Fallback: use system Maven
  MVN="mvn"
fi

exec "$MVN" "$@"
