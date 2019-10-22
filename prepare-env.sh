#!/usr/bin/env bash
#set -ex

build() {
  mvn package
}

build_if_src_changed_after_jar_was_build() {
  # find latest file changed (will not work too well for big folders)
  lastFileChangesInSrc=$(find src/ -type f | \
    sed 's/.*/"&"/' | \
    xargs ls --full-time | \
    awk '{ print $6," ",$7 }' | \
    sort | \
    tail -1)
  # get version of artifact from maven
  artifactVersion=$(mvn -q \
    -Dexec.executable=echo \
    -Dexec.args='${project.version}' \
    --non-recursive \
    exec:exec)
  # check last modified time
  lastTimeTargetChanged=$(ls --full-time target/jira-cli-${artifactVersion}-jar-with-dependencies.jar | \
    awk '{ print $6, " ", $7 }')

  # convert to epoch
  lastFileChangedAt=$(date -d "${lastFileChangesInSrc}" -u +%s)
  lastTargetCahngedAt=$(date -d "${lastTimeTargetChanged}" -u +%s)

  # build only if src/ was changed after targe/*jar was created
  if [ $lastFileChangedAt -gt $lastTargetCahngedAt ]; then
    build
  fi
}

# re-build jar only if src/ was changed, otherwuse use jar directly
if [ -d "target" ]; then
  # target folder exists ..

  build_if_src_changed_after_jar_was_build
else
  # target/ folder is missing .. so build it
  build
fi
