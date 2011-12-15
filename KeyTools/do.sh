set -e

java -cp ../../i2p.i2p/build/i2p.jar:$PWD/lib/junit_4/junit-4.5.jar:$PWD/dist/KeyTools.jar i2p.keytools.Main "$@"
