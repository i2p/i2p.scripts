#! /usr/bin/env sh

. ./config

URLLIST=`./list-org-cloneurls.sh`
HERE=$(pwd)
for URL in $URLLIST; do
  BASEURL=$(echo $URL | sed "s|github.com:$ORG|i2pgit.org:i2p-hackers|g" | sed 's|i2p\.plugins\.i2psnark-rpc|i2psnark-rpc|g')
  CLONEDIR=$(echo $URL | sed "s|git@github.com:$ORG/||g" | sed 's|.git||g')
  if [ -d $CLONEDIR ]; then
    cd $CLONEDIR && git pull origin --tags && \
      git pull origin master && git push github --all
    cd $HERE
  fi
done