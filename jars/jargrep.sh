#
#  grep for classes in jars/wars on the command line
#  usage: jargrep.sh classname foo.jar foo2.war ...
#
#  zzz 2020-05 CCO
#

SRCH=$1
shift
for i in $@
do
	echo $i
	ln -s $PWD/$i /tmp/foo$$.zip
        unzip -l /tmp/foo$$.zip | grep -i $SRCH
	rm -f /tmp/foo$$.zip
done
