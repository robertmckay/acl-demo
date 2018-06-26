#!/usr/bin/env bash

pushd build/nodes/

set -euo pipefail
export CAPSULE_CACHE_DIR=cache

# If it is set, it means that java overrides the default system java, which is what we want.
if [ -n "${JAVA_HOME-}" ]; then
export PATH="$JAVA_HOME/bin:$PATH"
fi

tmux set pane-border-status top
tmux set pane-border-format "#{pane_title}"

count=0
command="java -jar corda.jar"
for dir in $(ls); do
    if [ -d "$dir" ]; then
        pushd "$dir" >/dev/null
        name=$(basename "$dir" + ' Node')
        tmux split-window "printf '\033]2;$name\033\\'; $command"
        tmux select-layout tiled
        popd >/dev/null
        count=$(( count + 1))
    fi
done

command="java -jar corda-webserver.jar"
for dir in $(ls); do
    if [ -d "$dir" ]; then
        pushd "$dir" >/dev/null
        name=$(basename "$dir" + ' Webserver')
        tmux split-window "printf '\033]2;$name\033\\'; $command"
        tmux select-layout tiled
        popd >/dev/null
        count=$(( count + 1))
    fi
done

popd