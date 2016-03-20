#
# Fixup the irc meeting logs
# zzz 03-2016
#
# This is for a xchat log with timestamps.
# The last few sed lines are for changing time to UTC,
# adjust or remove as necessary.
# See branch i2p.www i2p2www/meetings/parse-log-kytv.sh for alternative.
#
# usage: meetinglog.sh file > xxx.log
#
cut -c 8- $@ | \
  grep -v ' has joined #i2p-dev' | \
  grep -v ' has left #i2p-dev' | \
  grep -v ' has quit ' | \
  grep -v ' is now known as ' | \
  grep -v '	ChanServ gives voice to ' | \
  sed 's/\t/ /g' | \
  sed 's/^15:/19:/' | \
  sed 's/^16:/20:/' | \
  sed 's/^17:/21:/'
