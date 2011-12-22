#!/bin/sh
# trustedupdate.sh
#
# Another marginally useful wrapper script by KillYourTV
# Released into public domain
#
# December 2011

#I2P=
PRIVATEKEY=private-key

print_help () {
cat << EOF

Usage: $0 keygen        Generates keys. Keys will be saved to `pwd`/public-key
                                     and `pwd`/$PRIVATEKEY
       $0 showversion   signedFile
       $0 sign          inputFile outputFile version
       $0 verifysig     signedFile
       $0 verifyupdate  signedFile
       $0 verifyversion signedFile
       $0 verifyall     signedFile
EOF
}

if [ ! -n "$I2P" ]; then
        echo "ERROR: Environment variable 'I2P' not set." >&2
        exit 1
elif [ ! -e "$I2P/lib/i2p.jar" ]; then
        echo "ERROR: Cannot find i2p.jar in $I2P/lib" >&2
        exit 1
fi

check_key () {
        if [ ! -n "$PRIVATEKEY" ]; then
                echo "ERROR: PRIVATEKEY not set." >&2
                exit 1
        fi
}

trusted_update ()
{
        java -cp $I2P/lib/i2p.jar net.i2p.crypto.TrustedUpdate "$@"
}


while true ; do
        case $1 in
                keygen)
                        check_key
                        trusted_update keygen public-key "$PRIVATEKEY"
                        echo "You may want to move `pwd`/$PRIVATEKEY to a safe place, then edit $0"
                        echo "to update the PRIVATEKEY variable to point to the new location."
                        exit 0
                        ;;
                showversion)
                        trusted_update showversion "$2"
                        exit 0
                        ;;
                sign)
                        check_key
                        trustedupdate sign "$2" "$3" "$PRIVATEKEY" "$4"
                        exit 0
                        ;;
                verifysig)
                        trusted_update verifysig "$2"
                        exit 0
                        ;;
                verifyversion)
                        trusted_update verifyversion "$2"
                        exit 0
                        ;;
                verifyall)
                        trusted_update showversion "$2"
                        trusted_update verifyversion "$2"
                        trusted_update verifysig "$2"
                        trusted_update verifyupdate "$2"
                        exit 0
                        ;;
                *)
                        print_help
                        exit
                        ;;
        esac
done

# vim:fenc=utf-8:ai:si:ts=4:sw=4:et:nu:fdm=indent:fdn=1:
