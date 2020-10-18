#! /usr/bin/env sh
#
# view the changes from the last release for all files in the workspace;
# this includes local changes not checked in
# zzz 2008-10
#
# Re-written for git.
# idk 2020-10

REL=$(git diff `git tag --sort=committerdate | tail -1`)
git diff $(git tag --sort=committerdate | tail -1) > $REL.diff
$EDITOR $REL.diff
