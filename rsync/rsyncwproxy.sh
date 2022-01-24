PORT=7665
# Note, you need the openbsd-netcat to do this:
export RSYNC_CONNECT_PROG="nc -X 5 -x 127.0.0.1:$PORT %H 873"

exec rsync $@

# example:
# sh rsyncwproxy.sh bswwika32z7eym55zziv3nath4kqvtzkrtcjtihm6plcxjg6qsnq.b32.i2p::code/