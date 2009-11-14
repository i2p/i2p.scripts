#!/bin/bash
#
# grep the source
# maybe grep -R would be better
# add /dev/null to force filename print - seems like there should be a grep option but guess not
#
find . -type f \( -name \*.java -o -name \*.jsp -o -name \*.xml \) -exec grep -i "$@" {} /dev/null \;
