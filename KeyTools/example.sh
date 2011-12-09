set -e

doit() {
    java -cp /home/i2p/app/lib/i2p.jar:$PWD/lib/junit_4/junit-4.5.jar:$PWD/dist/KeyTools.jar i2p.keytools.Main "$@"
}

mkdir -p test

if [ "$thehardway" ]; then
    echo This takes about 2min each run, so do not bother just for an example.
    exit 3;
    key=$(doit create | grep '^Created new key ' | sed -e's/^Created new key //')
    # Created new key I6guiNQXM1Rc-KfcCLqJovp-iHusE-MMXIUxnG3jcCE
    # echo key=$key
else
    mkdir ~/.keys >> /dev/null && true
    key=I6guiNQXM1Rc-KfcCLqJovp-iHusE-MMXIUxnG3jcCE
    cp example.priv ~/.keys/$key.priv
fi

export key
if false; then
echo Public key $key:
doit export | base64
echo
echo -n Signing...
echo test | doit sign > test/signature
echo -n Verify the signature...
echo test | doit verify test/signature
echo -n BlockSign and BlockVerify...
echo blockSign makes a jar file | doit blockSign | doit blockVerify
echo -n Encrypting and decrypting:
echo this was encrypted | doit encrypt > test/encrypted
doit decrypt < test/encrypted 
fi
echo -n Pushing $key ...
doit push
