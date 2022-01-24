#!/bin/bash
DEVDIR=$HOME/dev
ZIPDIR=$HOME/builds

if [ -d $DEVDIR/$1 ]
then
    cd $DEVDIR/$1

    if [ $# -ge 2 ] && [ $2 == "-f" ]
    then
        echo
        echo "Cleaning up $1 build directories..."
        echo
        ant distclean
    fi

    echo
    echo "Building update file for $1..."
    echo
    ant updater

    echo
    echo "Copying update file for $1 to eepsite..."
    if [ ! -d $ZIPDIR/$1 ]
    then
        mkdir -p $ZIPDIR/$1
    else
        if [ -f $ZIPDIR/$1/i2pupdate.zip ]
        then
            rm $ZIPDIR/$1/i2pupdate.zip
        fi
    fi
    mv $DEVDIR/$1/i2pupdate.zip $ZIPDIR/$1

    echo
    echo "Done!"
else
    echo "$1 is not a valid branch!"
fi
