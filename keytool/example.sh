set -x

doit() {
    java -cp /home/i2p/app/lib/i2p.jar:$PWD/dist/KeyTools.jar i2p.keytools.Main "$@"
}

mkdir -p test


doit export test/privKey > /dev/null
doit export test/privKey > test/pubKey
echo test | doit sign test/privKey > test/signature
echo test | doit verify test/pubKey test/signature
echo this was encrypted | doit encrypt test/pubKey > test/encrypted
doit decrypt test/privKey < test/encrypted | less