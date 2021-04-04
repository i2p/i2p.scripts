#! /usr/bin/env sh

./clone-org.sh

while true; do
  ./sync-org.sh
  sleep 10m
done