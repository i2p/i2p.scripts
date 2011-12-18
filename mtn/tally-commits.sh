#!/bin/sh -e

# tally-commits.sh
# A really simple script to display the number of commits by a single comitter.
# use: tally-commits.sh [start] [end]

# For example, to see commits from i2p-0.8.1 to i2p-0.8.4: ./tally-commits.sh t:i2p-0.8.1 t:i2p-0.8.4
# To see the commits since i2p 0.8.7: ./tally-commits.sh t:i2p-0.8.7
# Commits since June 2011: ./tally-commits.sh d:2011-06
#
# author: killyourtv, Dec 2011
# license: public domain

FULL=$(mktemp)
LIST=$(mktemp)


if  [ -n "$1" ] &&  [ -n "$2" ] ; then
    _TO="--to ${1}"
    _FROM="--from ${2}"
elif [ -n "$1" ] && [ ! -n "$2" ] ; then
    _TO="--to ${1}"
    _FROM="--from h:"
elif [ ! -n "$1" ] && [ ! -n "$2" ] ; then
    _TO="--clear-to"
    _FROM="--clear-from"
fi

(mtn log ${_FROM} ${_TO} --brief --no-graph --no-merges | awk '{print $2}') 1> $FULL

sed -e '/@/d' -e 's/$/@mail.i2p/' $FULL > $LIST
sed '/@/!d' $FULL >> $LIST

for each in $(cat $LIST|sort -u); do
    /bin/echo -e "$each => " $(grep -c $each $LIST)
done |sort -n -k3,3
