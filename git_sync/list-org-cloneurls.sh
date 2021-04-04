#! /usr/bin/env sh

. ./config

curl -s https://api.github.com/orgs/$ORG/repos | grep ssh_url | sed 's|"ssh_url":||g' | tr -d '", \t' | grep -v 'i2p-browser'
