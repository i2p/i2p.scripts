#
# copy a new tanuki wrapper release from the delta pack over to i2p.i2p mtn
#
# zzz 2016-05 public domain
#

if [ "$#" -ne "2" ]
then
	echo "usage: $0 /path/to/wrapper-delta-pack-3.5.xx /path/to/i2p.i2p"
	exit 1
fi

if [ ! -f ${1}/README_en.txt ]
then
	echo "Delta pack dir $1 does not exist"
	exit 1
fi
if [ ! -f ${2}/history.txt ]
then
	echo "I2P source dir $2 does not exist"
	exit 1
fi
FROM=$1
TO=${2}/installer/lib/wrapper

set -e

cp $FROM/bin/wrapper-freebsd-x86-32 $TO/freebsd/i2psvc
cp $FROM/lib/libwrapper-freebsd-x86-32.so $TO/freebsd/libwrapper.so

cp $FROM/bin/wrapper-freebsd-x86-64 $TO/freebsd64/i2psvc
cp $FROM/lib/libwrapper-freebsd-x86-64.so $TO/freebsd64/libwrapper.so

cp $FROM/bin/wrapper-linux-armel-32 $TO/linux-armv5/i2psvc
cp $FROM/lib/libwrapper-linux-armel-32.so $TO/linux-armv5/libwrapper.so

#cp $FROM/bin/wrapper-linux-armhf-64 $TO/linux-armv6/i2psvc
#cp $FROM/lib/libwrapper-linux-armhf-64.so $TO/linux-armv6/libwrapper.so
echo 'Warning - not copying armhf/armv6 files, you must compile them yourself'
echo "See $TO/linux-armv6/README.txt"

cp $FROM/bin/wrapper-linux-ppc-32 $TO/linux-ppc/i2psvc
cp $FROM/lib/libwrapper-linux-ppc-32.so $TO/linux-ppc/libwrapper.so

cp $FROM/bin/wrapper-linux-x86-32 $TO/linux/i2psvc
cp $FROM/lib/libwrapper-linux-x86-32.so $TO/linux/libwrapper.so

cp $FROM/bin/wrapper-linux-x86-64 $TO/linux64/i2psvc
cp $FROM/lib/libwrapper-linux-x86-64.so $TO/linux64/libwrapper.so

cp $FROM/bin/wrapper-macosx-universal-32 $TO/macosx/i2psvc-macosx-universal-32
cp $FROM/lib/libwrapper-macosx-universal-32.jnilib $TO/macosx/

cp $FROM/bin/wrapper-macosx-universal-64 $TO/macosx/i2psvc-macosx-universal-64
cp $FROM/lib/libwrapper-macosx-universal-64.jnilib $TO/macosx/

cp $FROM/bin/wrapper-solaris-sparc-32 $TO/solaris/i2psvc
cp $FROM/lib/libwrapper-solaris-sparc-32.so $TO/solaris/libwrapper.so

# in delta pack, but kytv compiled to change the icon, so don't use them
#cp $FROM/bin/wrapper-windows-x86-32.exe $TO/win32/I2Psvc.exe
#cp $FROM/lib/wrapper-windows-x86-32.dll $TO/win32/wrapper.dll
echo 'Warning - not copying 32-bit Windows binaries, you must compile them yourself'
# not in delta pack, have to compile ourselves
#cp $FROM/bin/wrapper-windows-x86-64.exe $TO/win64/I2Psvc.exe
#cp $FROM/lib/wrapper-windows-x86-64.dll $TO/win64/wrapper.dll
echo 'Warning - Delta pack does not have 64-bit Windows binaries, you must compile them yourself'
echo "See $TO/win64/README-x64-win.txt"

cp $FROM/lib/wrapper.jar $TO/all/

chmod -x $TO/*/i2psvc $TO/*/I2Psvc.exe $TO/*/*.so $TO/*/wrapper.dll $TO/macosx/i2psvc-* $TO/macosx/libwrapper-*
