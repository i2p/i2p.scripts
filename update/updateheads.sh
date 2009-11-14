#
# check to see if all update hosts have the latest
#
for i in `cat updatesources.txt``
do
	echo $i
	eephead $i
	echo '==========================='
done
