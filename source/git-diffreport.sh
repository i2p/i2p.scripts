#! /usr/bin/env sh

# diffstat the changes from the last release to the current workspace;
# similar to diffreport.sh

git diff $(git tag --sort=committerdate | tail -1) | diffstat -D . -f 4
