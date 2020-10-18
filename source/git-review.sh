#! /usr/bin/env sh
#
# view the diff for the files listed in filelist.txt
# zzz 2008-10
#
# This does not diff based on the latest release tag, it diffs 
# the workspace against the latest checked in code.
# idk 2020-10
git diff `cat filelist.txt` > out.diff 2>&1
$EDITOR out.diff
