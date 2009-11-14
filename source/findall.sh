#!/bin/bash
#
# grep the source and print the filenames only
# maybe grep -R would be better
#
find . -type f \( -name *.java -o -name *.jsp \) -exec grep -l -i "$@" {} \;
