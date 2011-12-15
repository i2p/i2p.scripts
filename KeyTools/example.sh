set -e

mkdir -p test

if [ "$thehardway" ]; then
    echo This takes about 2min each run, so do not bother just for an example.
    exit 3;
    key=$(sh do.sh create | grep '^Created new key ' | sed -e's/^Created new key //')
    # Created new key I6guiNQXM1Rc-KfcCLqJovp-iHusE-MMXIUxnG3jcCE
    # echo key=$key
else
    mkdir ~/.keys >> /dev/null && true
    key=I6guiNQXM1Rc-KfcCLqJovp-iHusE-MMXIUxnG3jcCE
    cp example.priv ~/.keys/$key.priv
fi

export key
echo Public key $key:
sh do.sh export | base64
echo
echo -n Signing...
echo test | sh do.sh sign > test/signature
echo -n Verify the signature...
echo test | sh do.sh verify test/signature
echo -n BlockSign and BlockVerify...
echo blockSign makes a jar file | sh do.sh blockSign | sh do.sh blockVerify
echo -n Encrypting and decrypting:
echo this was encrypted | sh do.sh encrypt > test/encrypted
sh do.sh decrypt < test/encrypted 
echo -n Pushing $key ...
sh do.sh push
