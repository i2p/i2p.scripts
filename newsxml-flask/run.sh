#!/usr/bin/env bash
#
# run newsxml server
#
cd $(dirname $0)
PYTHONPATH=/usr/local/lib/python2.7/dist-packages python app.py &> newsxml.log &
