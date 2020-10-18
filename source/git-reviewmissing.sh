#! /usr/bin/env sh
#
# view the changes in the current workspace for files NOT listed
# in filelist.txt; this is a good way to make sure you're not
# forgetting something that should be in filelist.txt before you
# check it in
# zzz 2008-10
#
# Re-written for git.
# idk 2020-10
git diff `cat filelist.txt` > out.diff
git diff > all.diff
diff all.diff out.diff | cut -c3- > missing.diff
$EDITOR missing.diff
