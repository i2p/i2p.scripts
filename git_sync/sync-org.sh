#! /usr/bin/env sh

. ./config

URLLIST=$(./list-org-cloneurls.sh)

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

for URL in $URLLIST; do
  BASEURL=$(echo $URL | sed "s|github.com:$ORG|i2pgit.org:i2p-hackers|g" | sed 's|i2p\.plugins\.i2psnark-rpc|i2psnark-rpc|g')
  CLONEDIR=$(echo $URL | sed "s|git@github.com:$ORG/||g" | sed 's|.git||g')
  echo "Syncing: $URL, $BASEURL, $CLONEDIR"
  if [ -d "$CLONEDIR" ]; then
    echo "Changing to $CLONEDIR"
    cd "$CLONEDIR" || exit 1
    while true; do
      # pull all the updates
      echo "Pulling updates for $CLONEDIR..."
      git pull --all && break
    done
    while true; do
      # pull all the tags
      echo "Pulling tags for $CLONEDIR..."
      git pull origin --tags && break
    done
    while true; do
      # push all the updates
      echo "Merging updates for $CLONEDIR..."
      git pull origin master && break
    done
    while true; do
      # push all the branches
      echo "Pushing updates for $CLONEDIR..."
      git push github --all && break
    done
    while true; do
      # push all the tags
      echo "Pushing tags for $CLONEDIR..."
      git push github --tags && break
    done
    echo "Returning home"
    cd "$HERE" || exit 1
  fi
done