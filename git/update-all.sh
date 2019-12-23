#!/bin/bash

set -euxo pipefail

for PROJECT in i2p.itoopie i2p.plugins.zzzot i2p.graphics i2p.plugins.i2pcontrol i2p.scripts i2p.android.base i2p.syndie i2p.i2p i2p.www i2p.plugins.orchid; do
    ./update-git.sh "$PROJECT" || echo "Export of $PROJECT failed"
done
