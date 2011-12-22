#!/bin/sh
#
# grep the source
# maybe grep -R would be better
#
find . -type f \( -name \*.java -o -name \*.jsp -o -name \*.xml \) -exec grep -il "$@" {} \;
