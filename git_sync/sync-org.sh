#! /usr/bin/env sh

. ./config

URLLIST=$(./list-org-cloneurls.sh)

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

DO="1 2 3 4 5 6 7 8 9 10"

for URL in $URLLIST; do
  BASEURL=$(echo $URL | sed "s|github.com:$ORG|i2pgit.org:i2p-hackers|g" | sed 's|i2p\.plugins\.i2psnark-rpc|i2psnark-rpc|g')
  CLONEDIR=$(echo $URL | sed "s|git@github.com:$ORG/||g" | sed 's|.git||g')
  echo "Syncing: $URL, $BASEURL, $CLONEDIR"
  if [ -d "$CLONEDIR" ]; then
    echo "Changing to $CLONEDIR"
    cd "$CLONEDIR" || exit 1
    for d in $DO; do
      # pull all the updates
      echo "Pulling updates for $CLONEDIR... attempt $d"
      git pull --all && break
    done
    for d in $DO; do
      # pull all the tags
      echo "Pulling tags for $CLONEDIR... attempt $d"
      git pull origin --tags && break
    done
    for d in $DO; do
      # push all the updates
      echo "Merging updates for $CLONEDIR... attempt $d"
      git pull origin master && break
    done
    for d in $DO; do
      # push all the branches
      echo "Pushing updates for $CLONEDIR... attempt $d"
      git push github --all && break
    done
    for d in $DO; do
      # push all the tags
      echo "Pushing tags for $CLONEDIR... attempt $d"
      git push github --tags && break
    done
    echo "Returning home"
    cd "$HERE"
  fi
done