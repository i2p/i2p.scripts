#! /usr/bin/env sh
#
# view the changes from the last release to the current head;
# files not checked in are not included
# Note that this actually diffs the workspace base (w:) not the head (h:)
# zzz 2008-10
#
# Re-written for git. The git version uses the current head.
# idk 2020-10

REL=$(git diff `git tag --sort=committerdate | tail -1`)
git diff $(git tag --sort=committerdate | tail -1) $(git rev-parse --short HEAD) > $REL.diff
$EDITOR $REL.diff
