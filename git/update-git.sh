#!/bin/bash

###
### Export from monotone to git
###
### Monotone doesn't handle suspend certs correctly when doing
### a git_export, so they must be killed locally before exporting.
###
### This script takes the branch name as an optional argument.
### It defaults to i2p.i2p.
###
### By duck and zzz May 2011
###

cd $(dirname "$0")

MTN=mtn
MTN_VERSION=`$MTN --version | cut -d' ' -f2`

if [ $# -lt 1 ]; then
  BRANCH=i2p.i2p
else
  BRANCH=$1
fi

DB=${BRANCH}.mtn

if [ ! -f $DB ]; then
  $MTN --db ${DB} db init
fi

echo "Pulling branch $BRANCH from mtn"
# Try up to 10 times
COUNT=0
while [ $COUNT -lt 10 ]; do
  $MTN --db ${DB} pull 127.0.0.1:8998 $BRANCH --key=
  if [ $? -eq 0 ]; then
    break
  fi
  let "COUNT+=1"
done
if [ $COUNT -ge 10 ]; then
  echo Can\'t pull from mtn, aborting.
  exit 1
fi
echo

if [ $BRANCH = "i2p.i2p" ]; then
  # echo "Killing bad revs"
  if [[ $MTN_VERSION == 1* ]]; then
    # mtn 1.0 syntax
    $MTN --db ${DB} local kill_rev 18c652e0722c4e4408b28036306e5fb600f63472
    $MTN --db ${DB} local kill_rev 7d2f18d277a34eb2772fa9380449c7fdb4dcafcf
  else
    # mtn 0.48 syntax
    $MTN --db ${DB} db kill_rev_locally 18c652e0722c4e4408b28036306e5fb600f63472
    $MTN --db ${DB} db kill_rev_locally 7d2f18d277a34eb2772fa9380449c7fdb4dcafcf
  fi
  echo
fi
if [ $BRANCH = "i2p.syndie" ]; then
  # echo "Killing bad revs"
  if [[ $MTN_VERSION == 1* ]]; then
    # mtn 1.0 syntax
    $MTN --db ${DB} local kill_rev d7cd3cc1b9d676c250918b583c4da41d48ea70bc
  else
    # mtn 0.48 syntax
    $MTN --db ${DB} db kill_rev_locally d7cd3cc1b9d676c250918b583c4da41d48ea70bc
  fi
  echo
fi
if [ $BRANCH = "i2p.i2p-bote" ]; then
  # echo "Killing bad revs"
  if [[ $MTN_VERSION == 1* ]]; then
    # mtn 1.0 syntax
    $MTN --db ${DB} local kill_rev 07103f5f968363c284ce3ebe58863861dc10a07a
    $MTN --db ${DB} local kill_rev 245793c1e59b5ab5dc088ec5cc89f61c3068a729
    $MTN --db ${DB} local kill_rev 84140d0de55bae627fc0aed7feb55d72b929a43c
    $MTN --db ${DB} local kill_rev c20050d9e5180c5a568c3f5ab12a356e253d1ed0
    $MTN --db ${DB} local kill_certs ec0ec8bca6aa7c8d6b314688662891ed0c80aa34 date
    $MTN --db ${DB} cert ec0ec8bca6aa7c8d6b314688662891ed0c80aa34 date 2012-01-01T00:00:00
  else
    # mtn 0.48 syntax
    $MTN --db ${DB} db kill_rev_locally 07103f5f968363c284ce3ebe58863861dc10a07a
    $MTN --db ${DB} db kill_rev_locally 245793c1e59b5ab5dc088ec5cc89f61c3068a729
    $MTN --db ${DB} db kill_rev_locally 84140d0de55bae627fc0aed7feb55d72b929a43c
    $MTN --db ${DB} db kill_rev_locally c20050d9e5180c5a568c3f5ab12a356e253d1ed0
  fi
  echo
fi
if [ $BRANCH = "i2p.www" ]; then
  # echo "Killing bad revs"
  if [[ $MTN_VERSION == 1* ]]; then
    # mtn 1.0 syntax
    $MTN --db ${DB} local kill_rev 14e8b3cb0370ca30b884ec1a750b99199a61da55
  else
    # mtn 0.48 syntax
    $MTN --db ${DB} db kill_rev_locally 14e8b3cb0370ca30b884ec1a750b99199a61da55
  fi
  echo
fi

if [[ $MTN_VERSION == 1* ]]; then
  # mtn 1.0 syntax
  HEADS=`$MTN --db ${DB} head --no-standard-rcfiles --ignore-suspend-certs -b $BRANCH 2> /dev/null | wc -l`
else
  # mtn 0.48 syntax
  HEADS=`$MTN --db ${DB} head --ignore-suspend-certs -b $BRANCH 2> /dev/null | wc -l`
fi
if [ $HEADS -gt 1 ]; then
  echo "Heads:"
  $MTN --db ${DB} head --ignore-suspend-certs -b $BRANCH
  echo Multiple heads, aborting!
  exit
fi

echo "Exporting to git format"
$MTN --db ${DB} git_export > i2p.git_export
echo

rm -rf i2p.git
mkdir i2p.git
cd i2p.git
git init
echo "Importing into git"
git fast-import < ../i2p.git_export

echo "Pushing branch $BRANCH to github"
git checkout $BRANCH
git remote add origin git@github.com:i2p/${BRANCH}.git
git push origin HEAD:master --force

cd ..

