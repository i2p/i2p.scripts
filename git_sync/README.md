Git sync scripts
================

IF you are an SSH user authorized on both [I2P Git](https://i2pgit.org/i2p-hackers/)
and on [Github](https://github.com/i2p), then you are able to run this script. Right
now, that's basically only me. If you want to also run this script, please announce
on zzz.i2p or reach out to `eyedeekay`(sometimes also `idk`) on Irc2P.

Add your keys to the authentication agent before starting. Everything runs from this
directory, without modification.

In order to perform the initial clone of the I2P source code, run:

```sh
./clone-org.sh
```

In order to perform a one-time sync all the source code in those repositories between
gitlab and github, run

```sh
./sync-org.sh
```

There are also scripts to run continuously. If you want to run it continuously in the
foreground, use `run.sh`:

```sh
./run.sh
```

To run it in the background, use `background.sh`:

```sh
./background.sh
```
