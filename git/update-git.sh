#!/bin/sh

###
### Export from monotone to git
###
### Monotone doesn't handle suspend certs correctly when doing
### a git_export, so they must be killed locally before exporting.
###
### By duck and zzz May 2011
###

MTN=mtn
echo "Pulling latest from mtn"
$MTN --db i2p.mtn pull -k YOURNAME-transport@mail.i2p
echo

echo "Heads:"
$MTN --db i2p.mtn head --no-standard-rcfiles --ignore-suspend-certs -b i2p.i2p
echo
echo "Press a key to continue"
read dummy

echo "Killing bad revs"
$MTN --db i2p.mtn local kill_rev 18c652e0722c4e4408b28036306e5fb600f63472
$MTN --db i2p.mtn local kill_rev 7d2f18d277a34eb2772fa9380449c7fdb4dcafcf
echo

echo "Exporting to git format"
$MTN --db i2p.mtn git_export > i2p.git_export
echo

cd i2p.git
echo "Importing into git"
git fast-import < ../i2p.git_export
echo
echo "Press a key to continue"
read dummy

echo "Pushing to github"
git checkout i2p.i2p
git push origin i2p.i2p

cd ..

