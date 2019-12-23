# i2p-mtn-git

Export Scripts for I2P Monotone to Git and related utilities.
A maybe outdated version of this can be found in the i2p.scripts mtn branch [which is also exported](https://github.com/i2p/i2p.scripts/tree/master/git).
I run them regularly to update the [official git mirrors](https://github.com/i2p).

Monotone does not run on modern distributions anymore so I use an old Debian release.
Might change to Alpine based source build later.

Some history rewrites are necessary to reproduce somehow partially failed exports that already were published.

Instructions
-------------

- Build the docker container: `docker build -t debian:mtn .`
- Make adaptions to `./update-git.sh`
- run `./update-all.sh`

Contact
--------

Find the nick "nextloop" on Freenode and irc2p.

License
--------

Public Domain
