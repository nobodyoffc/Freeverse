#!/bin/sh
# Copies the FTSP cross-implementation vectors (Protocols/FTSP/vectors) into the implementations
# that test against a local copy of them. Run after regenerating the vectors. With --check, only
# reports copies that are out of date, and exits 1 if any are.
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SRC="$ROOT/Protocols/FTSP/vectors"
ANDROID_PROJECTS=${ANDROID_PROJECTS:-$HOME/AndroidStudioProjects}
MAC_APPS=${MAC_APPS:-$HOME/MacApp}
MODE=${1:-sync}
stale=0

handle() {
    project=$1
    dest=$2
    if [ ! -d "$project" ]; then
        echo "skip   $project not found"
        return
    fi
    if [ "$MODE" = "--check" ]; then
        if diff -rq "$SRC" "$dest" >/dev/null 2>&1; then
            echo "ok     $dest"
        else
            echo "stale  $dest"
            stale=1
        fi
    else
        mkdir -p "$dest"
        cp "$SRC"/*.json "$SRC"/README.md "$dest"/
        echo "synced $dest"
    fi
}

for app in Safe Freer; do
    handle "$ANDROID_PROJECTS/$app/FC-AJDK" "$ANDROID_PROJECTS/$app/FC-AJDK/src/test/resources/ftsp-vectors"
done
for package in FCCore FCDomain; do
    handle "$MAC_APPS/FreerForMac/Packages/$package" "$MAC_APPS/FreerForMac/Packages/$package/Tests/${package}Tests/Resources/ftsp-vectors"
done
exit $stale
