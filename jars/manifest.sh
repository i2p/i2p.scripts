#
#  dump out the manifest for all jars/wars on the command line
#
#  zzz 2020-05 CCO
#

for i in $@
do
	echo '==============='
	echo $i ':'
	unzip -c $i META-INF/MANIFEST.MF
done
