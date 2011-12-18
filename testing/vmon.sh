#
# Turn on vmCommSystem, i.e. enable test mode with no network access
# Run before starting router.
# zzz 12/2011
#
grep -v i2p.vmCommSystem ~/.i2p/router.config > /tmp/foo$$
echo 'i2p.vmCommSystem=true' >> /tmp/foo$$
mv /tmp/foo$$ ~/.i2p/router.config
