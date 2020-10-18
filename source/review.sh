#! /bin/sh
#
# view the diff for the files listed in filelist.txt
# zzz 2008-10
#
mtn dif `cat filelist.txt` > out.diff 2>&1
$EDITOR out.diff
