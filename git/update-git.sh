#!/usr/bin/env bash

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
### By nextllop 2017 and 2019
###

# Configure as necessary
export PATH="/home/kilian/i2pmtn:$PATH"
MTN=mtn
#SERVER="mtn.i2p-projekt.de:4691"
SERVER="localhost:8998"
INCLUDE_BRANCHES=0
PUSH_TO_GITHUB=0
BARE_REPO=1

#cd $(dirname "$0")
TMP=$(mktemp XXXXXX)
trap "rm -f $TMP; exit" 1 2 4 15

if [ $# -lt 1 ]; then
  BRANCH=i2p.i2p
else
  BRANCH=$1
fi

if [ $# -lt 2 ]; then
  #with trailing slash. .git is added by the script
  PUSH_LOCATION="git@github.com:i2p/"
else
  PUSH_LOCATION=$2
fi

DB=${BRANCH}.mtn
BRANCHES=${BRANCH}.branches

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
    if [ $INCLUDE_BRANCHES -eq 1 ]; then
      $MTN --db ${DB} pull mtn://"$SERVER"?"$BRANCH{,.*}" --key=""
    else
      $MTN --db ${DB} pull mtn://"$SERVER"?"$BRANCH" --key=""
    fi
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
    kill_rev f4677dff0b53f0b8f19dae982da5dac9fd08db8e
    kill_rev 4824470161121832aa6d1771fa2c2aca05b4ffe5
    kill_rev 0b397d300edb63b2b4bd04598ff34ec9ce788daf
    kill_rev d45cd59de58733e77b7d31d9e5558337c33174d2
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
if [ $HEADS -gt 2 ]; then
  echo "Heads:"
  $MTN --db ${DB} head --ignore-suspend-certs -b $BRANCH
  echo "Multiple heads, aborting!" >&2
  exit
fi

if [ $INCLUDE_BRANCHES -eq 1 ]; then
  $MTN --db ${DB} list branches >$BRANCHES
  sed -i "s/^${BRANCH}$/${BRANCH} = master/g" $BRANCHES
  sed -i "s/^${BRANCH}\\.\\(.*\\)$/${BRANCH}.\\1 = \\1/g" $BRANCHES
else
  echo "" >$BRANCHES
fi

# Older versions will abort without already existing marks files,
# so we make sure that there's *something* here.
test -f .${BRANCH}.mtn.import || touch .${BRANCH}.mtn.import
echo "Exporting to git format"
if $MTN --db ${DB} git_export --branches-file=${BRANCHES} --import-marks=.${BRANCH}.mtn.import --export-marks=.${BRANCH}.mtn.export > $TMP; then
  IMP=$(sum .${BRANCH}.mtn.import)
  EXP=$(sum .${BRANCH}.mtn.export)
  mv -f .${BRANCH}.mtn.export .${BRANCH}.mtn.import
fi
echo

  if [ "$IMP" = "$EXP" ]; then
      echo "No new check-ins found"
      rm -f "$TMP"
    exit 4
  fi

if [ ! -d $BRANCH.git ]; then
  mkdir $BRANCH.git
  cd $BRANCH.git
  if [ $BARE_REPO -eq 1 ]; then
    git init --bare
  else
    git init
  fi
  git remote add origin ${PUSH_LOCATION}${BRANCH}.git
else
  cd $BRANCH.git
fi

# Older versions will abort without already existing marks files,
# so we make sure that there's *something* here.
test -f ../.${BRANCH}.git.import || touch ../.${BRANCH}.git.import
echo "Importing into git" >&2
if git fast-import --force --import-marks=../.${BRANCH}.git.import --export-marks=../.${BRANCH}.git.export < ../$TMP ; then
  mv ../.${BRANCH}.git.export ../.${BRANCH}.git.import
fi

# There is a commit with a messed up timestamp in the mtn branch.
# Git will set the date to "today" during import. So we have to do
# manual fixing after the import. We need to filter for the GIT_AUTHOR,
# as the SHA1 depends on the date, which changes every time we run this
# script. We set the date according to the old git exports done by kytv.
# SHA1 refs should match after this.
if [ $BRANCH = "i2p.i2p-bote" ]; then
  echo "Fixing up bad date" >&2
  git filter-branch -f --env-filter '
        if [ "$GIT_AUTHOR_NAME" = "7c54f9fbcb80e56dd9c1e144e255982ef6396df2" ]; then
                export GIT_AUTHOR_DATE="1325376000 +0000"
                export GIT_COMMITTER_DATE="1325376000 +0000"
        fi' -- --all
fi

# Similar issue in i2p.i2p
if [ $BRANCH = "i2p.i2p" ]; then
    echo "Fixing bad commit on github" >&2
    git filter-branch -f \
        --env-filter "if [ \$GIT_COMMIT = '0a0f6a80b5d175e71cd14c147ed9301675ef584c' ]; then
                         export GIT_AUTHOR_DATE='Tue Apr 2 16:51:26 2019 +0000'
                         export GIT_COMMITTER_DATE='Tue Apr 2 16:51:26 2019 +0000'
                     fi" \
        --msg-filter "if [ \$GIT_COMMIT = '0a0f6a80b5d175e71cd14c147ed9301675ef584c' ]; then echo Say that it\'s an absolute file; else cat; fi" \
        -- --all
fi

if [ $BRANCH = "i2p.www" ]; then
    echo "Fixing bad commit on github" >&2
    git filter-branch -f \
        --env-filter "if [ \$GIT_COMMIT = '4881a6e03bcff91ff3dd0aee22f65f9d23fa09ae' ]; then
                         export GIT_AUTHOR_NAME='slumlord'
                         export GIT_AUTHOR_EMAIL='slumlord@mail.i2p'
                         export GIT_AUTHOR_DATE='Mon Mar 11 08:16:21 2019 +0000'
                         export GIT_COMMITTER_NAME='slumlord'
                         export GIT_COMMITTER_EMAIL='slumlord@mail.i2p'
                         export GIT_COMMITTER_DATE='Mon Mar 11 08:16:21 2019 +0000'
                       elif [ \$GIT_COMMIT = '8727c83ab059cebfaa3f6bb7fe886655e8711fa6' ]; then
                         export GIT_AUTHOR_NAME='zab2'
                         export GIT_AUTHOR_EMAIL='zab2@mail.i2p'
                         export GIT_AUTHOR_DATE='Sun Mar 31 16:56:30 2019 +0000'
                         export GIT_COMMITTER_NAME='zab2'
                         export GIT_COMMITTER_EMAIL='zab2@mail.i2p'
                         export GIT_COMMITTER_DATE='Sun Mar 31 16:56:30 2019 +0000'
                       fi" \
        --msg-filter "if [ \$GIT_COMMIT = '4881a6e03bcff91ff3dd0aee22f65f9d23fa09ae' ]; then
                         echo \
\"merge of '2d6cc1ff971fb53ddcde9ffd6219a411fd47f132'
     and '5c1aba11dd668f20c3f564a730d9bb13119643a4'


Merge heads\"
                      else cat; fi" \
        -- --all
fi


if [ $BARE_REPO -eq 0 ]; then
  git checkout $BRANCH
fi
if [ $PUSH_TO_GITHUB -eq 1 ]; then
  echo "Pushing branch $BRANCH to remote" >&2
  if [ $INCLUDE_BRANCHES -eq 1 ]; then
    if [ $PUSH_TO_GITHUB -eq 1 ]; then
      git branch -D unknown
      git push --all --tags origin
    else
      git push --tags origin master:master
    fi
  else
    git push --tags origin ${BRANCH}:master
  fi
fi

cd ..
rm -f $TMP
