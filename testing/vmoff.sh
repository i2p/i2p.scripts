#
# Turn off vmCommSystem, i.e. enable normal mode
# Run before starting router.
# zzz 12/2011
#
grep -v i2p.vmCommSystem ~/.i2p/router.config > /tmp/foo$$
echo 'i2p.vmCommSystem=false' >> /tmp/foo$$
mv /tmp/foo$$ ~/.i2p/router.config
