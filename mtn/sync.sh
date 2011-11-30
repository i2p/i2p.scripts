#!/bin/sh
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
##########################################################
# Stop multiple instances from running simultaneously    #
# Nothing in this section should require editing         #
##########################################################
if [ -w /var/lock ]; then  # the default lock directory in Linux
        LOCKDIR="/var/lock/mtn-sync.lck"
else
        LOCKDIR="/tmp/mtn-sync.lck"   # but maybe not elsewhere...
fi
PIDFILE="${LOCKDIR}/PID"
ENO_SUCCESS=0;
ENO_GENERAL=1;
ENO_LOCKFAIL=2;
ENO_RECVSIG=3;

#trap 'ECODE=$?; echo "[`basename $0`] Exit: ${ECODE}" >&2' 0

if mkdir "${LOCKDIR}"  > /dev/null 2>&1 ; then
       trap 'ECODE=$?;
       rm -rf "${LOCKDIR}"' 0
       touch $PIDFILE
       echo $$ > "${PIDFILE}"
       trap 'echo "ERROR: Killed by a signal $ECODE $ENO_RECVSIG" >&2
            exit ${ENO_RECVSIG}' 1 2 3 15
else
       # lock failed, check if it's stale
       OTHERPID="$(cat "${PIDFILE}")"

       if [ $? != 0 ]; then
               echo "ERROR: Another instance of `basename $0` is active with PID ${OTHERPID}" >&2
               exit ${ENO_LOCKFAIL}
       fi

       if ! kill -0 ${OTHERPID} >/dev/null 2>&1; then
               #stale lock, removing it and restarting
               [ $opt_verbose ] && echo "INFO: Removing stale PID ${OTHERPID}" >&2
               rm -rf ${LOCKDIR}
               [ $opt_verbose ] && echo "INFO: [`basename $0`] restarting" >&2
               exec "$0" "$@"
       else
               #lock is valid and OTHERPID is active
               echo "ERROR: Another instance of `basename $0` is active with PID ${OTHERPID}" >&2
               exit ${ENO_LOCKFAIL}
       fi
fi
##########################################################
# Configuration may be required in this section          #
##########################################################
#
# try something a few times
#
MAX=5
retry () 
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
# Configure as necessary                                 #
# Have to be careful to quote the branch $BR correctly   #
##########################################################
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
