#!/bin/sh

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

# Configure as necessary
MTN=mtn
SERVER="127.0.0.1:8998"
PUSH_TO_GITHUB=0

cd $(dirname "$0")
TMP=$(mktemp XXXXXX)
trap 'rm -f $TMP;exit' 0 1 2 15

if [ $# -lt 1 ]; then
  BRANCH=i2p.i2p
else
  BRANCH=$1
fi

DB=${BRANCH}.mtn

if [ ! -f $DB ]; then
  $MTN --db ${DB} db init
fi

# 1st: set the field separator to '.'
# 2nd: assign the first digit of the version to MTN_VERSION
# 3rd: restore original field separator
OLDIFS=$IFS
IFS='.'
set -- $($MTN --version | cut -d' ' -f2)
MTN_VERSION=$1
IFS=$OLDIFS

kill_rev() {
  # Here we remove revisions that will cause the git import to fail.
  echo "Killing 'bad' rev $1" >&2
  if [ $MTN_VERSION -eq 1 ]; then
    $MTN --db ${DB} local kill_rev $1
  else
    $MTN --db ${DB} db kill_rev_locally $1
  fi
}

mtn_pull() {
  if [ $MTN_VERSION -eq 1 ]; then
    $MTN --db ${DB} pull mtn://"$SERVER"?"$BRANCH" --key=""
  else
    $MTN --db ${DB} pull "$SERVER" "$BRANCH" --key=""
  fi
}

echo "Pulling branch $BRANCH from mtn" >&2
# Try up to 10 times
COUNT=0
while [ $COUNT -lt 10 ]; do
  if mtn_pull; then
    break
  fi
  COUNT=$(expr $COUNT + 1)
done
if [ $COUNT -ge 10 ]; then
  echo "Can't pull from mtn, aborting." >&2
  exit 1
fi
echo

if [ $BRANCH = "i2p.i2p" ]; then
    kill_rev 18c652e0722c4e4408b28036306e5fb600f63472
    kill_rev 7d2f18d277a34eb2772fa9380449c7fdb4dcafcf
  echo
fi
if [ $BRANCH = "i2p.syndie" ]; then
    kill_rev d7cd3cc1b9d676c250918b583c4da41d48ea70bc
  echo
fi
if [ $BRANCH = "i2p.www" ]; then
    kill_rev 14e8b3cb0370ca30b884ec1a750b99199a61da55
  echo
fi
if [ $BRANCH = "i2p.i2p-bote" ]; then
    kill_rev 07103f5f968363c284ce3ebe58863861dc10a07a
    kill_rev 245793c1e59b5ab5dc088ec5cc89f61c3068a729
    kill_rev 84140d0de55bae627fc0aed7feb55d72b929a43c
    kill_rev c20050d9e5180c5a568c3f5ab12a356e253d1ed0
    # FIXME Is there a < 1.0 compatible way of doing this?
    $MTN --db ${DB} local kill_certs ec0ec8bca6aa7c8d6b314688662891ed0c80aa34 date
    $MTN --db ${DB} cert ec0ec8bca6aa7c8d6b314688662891ed0c80aa34 date 2012-01-01T00:00:00
  echo
fi

if [ $MTN_VERSION -eq 1 ]; then
  # mtn 1.0 syntax
  HEADS=`$MTN --db ${DB} head --no-standard-rcfiles --ignore-suspend-certs -b $BRANCH 2> /dev/null | wc -l`
else
  # mtn 0.48 syntax
  HEADS=`$MTN --db ${DB} head --norc --ignore-suspend-certs -b $BRANCH 2> /dev/null | wc -l`
fi
if [ $HEADS -gt 1 ]; then
  echo "Heads:"
  $MTN --db ${DB} head --ignore-suspend-certs -b $BRANCH
  echo "Multiple heads, aborting!" >&2
  exit
fi

# Older versions will abort without already existing marks files,
# so we make sure that there's *something* here.
test -f .${BRANCH}.mtn.import || touch .${BRANCH}.mtn.import
echo "Exporting to git format"
if $MTN --db ${DB} git_export --import-marks=.${BRANCH}.mtn.import --export-marks=.${BRANCH}.mtn.export > $TMP; then
  IMP=$(sum .${BRANCH}.mtn.import)
  EXP=$(sum .${BRANCH}.mtn.export)
  mv .${BRANCH}.mtn.export .${BRANCH}.mtn.import
fi
echo

  if [ "$IMP" = "$EXP" ]; then
    echo "No new check-ins found" >&2
    exit 4
  fi

if [ ! -d $BRANCH.git ]; then
  mkdir $BRANCH.git
  cd $BRANCH.git
  git init
  git remote add origin git@github.com:i2p/${BRANCH}.git
else
  cd $BRANCH.git
fi

# Older versions will abort without already existing marks files,
# so we make sure that there's *something* here.
test -f ../.${BRANCH}.git.import || touch ../.${BRANCH}.git.import
echo "Importing into git" >&2
git checkout -f $BRANCH
if git fast-import --import-marks=../.${BRANCH}.git.import --export-marks=../.${BRANCH}.git.export < ../$TMP ; then
  mv ../.${BRANCH}.git.export ../.${BRANCH}.git.import
fi

if [ $PUSH_TO_GITHUB -eq 1 ]; then
  echo "Pushing branch $BRANCH to github" >&2
  git checkout $BRANCH
  git push -f --tags origin HEAD:master
fi

cd ..
