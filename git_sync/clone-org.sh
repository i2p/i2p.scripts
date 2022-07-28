#! /usr/bin/env sh

. ./config

URLLIST=`./list-org-cloneurls.sh`
HERE=$(pwd)
for URL in $URLLIST; do
  echo $URL
  BASEURL=$(echo $URL | sed "s|github.com:$ORG|i2pgit.org:i2p-hackers|g" | sed 's|i2p\.plugins\.i2psnark-rpc|i2psnark-rpc|g')
  CLONEDIR=$(echo $URL | sed "s|git@github.com:$ORG/||g" | sed 's|.git||g')
  if [ ! -d $CLONEDIR ]; then
    git clone $BASEURL $CLONEDIR
    cd $CLONEDIR && git remote add github $URL
    cd $HERE
  fi
done