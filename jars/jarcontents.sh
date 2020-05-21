#
#  list classes in jars/wars on the command line
#
#  zzz 2020-05 CCO
#

# to get case-sensitive so META-INF is on top
export LC_ALL=C
for i in "$@"
do
	jar tf "$i" | sort
done
