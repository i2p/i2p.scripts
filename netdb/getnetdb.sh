#
# Just get a copy of netdb and save it
#
unset http_proxy
rm -f netdb.jsp
wget -q -O netdb.jsp http://amd.n:7657/netdb.jsp?f=1
