#
# courtesy of Kilroy
# modded by zzz
#
BASE=$HOME/public_html
cd $BASE
mtn -d i2p.mtn pull mtn.i2p2.de i2p.www
cd $BASE/i2p.www/
mtn up
