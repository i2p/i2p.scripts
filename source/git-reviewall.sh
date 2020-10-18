#! /usr/bin/env sh
#
# view the diff from the current head for all files changed in the workspace
# zzz 2008-10
#
# This does not diff based on the latest release tag, it diffs 
# the workspace against the latest checked in code.
# idk 2020-10
git diff > out.diff
$EDITOR out.diff
