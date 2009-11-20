#
# mtn sync script
# zzz Feb 1 2008
#
#  1) pull to local-db from local-server
#  2) sync local-db with one or more remote-servers
#  3) push to local-server from local-db
#
# If you are running a server you must have a second local-only database
# (mtn -d i2p.mtn db init) because 'mtn serve' locks its database
#
# If you are not running a server, you should skip steps 1 and 3 -
# comment them out below!
#
# Be sure and set up the configuration correctly below.
#
#
##########################################################
#
# try something a few times
#
MAX=5
function retry
{
	if [ $# -eq 0 ]
	then
		echo 'usage: $0 command args...'
		exit 1
	fi
#
	i=1
	while ! "$@"
	do
		echo "$0: try $i of $MAX failed for command $@"
		if [ $i -ge $MAX ]
		then
			break
		fi
		let i=$i+1
		sleep 15
	done
}

##########################################################
#
# Configure as necessary
# Have to be careful to quote the branch $BR correctly
#
TKEY=my-transport-key@mail.i2p
BR='i2p.*'
DB=i2p.mtn
LOG=sync.log
#
# You must set up a client tunnel for each remote server you want to sync with
# 8998 - welterde
# 8997 - complication
# 8999 - echelon
#
REMOTEPORTS="8998"
#
# 1) pull from local server
#
nice mtn --db=$DB pull localhost:4691 "$BR" -k$TKEY >> $LOG 2>&1
#
# 2) sync with remote servers
#
for i in $REMOTEPORTS
do
	retry nice mtn --db=$DB sync localhost:$i "$BR" -k$TKEY >> $LOG 2>&1
done
#
# 3) push to local server
#
nice mtn --db=$DB push localhost:4691 "$BR" -k$TKEY >> $LOG 2>&1
