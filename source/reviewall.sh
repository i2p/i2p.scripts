#! /bin/sh
#
# view the diff from the current head for all files changed in the workspace
# zzz 2008-10
#
mtn dif > out.diff
$EDITOR out.diff
