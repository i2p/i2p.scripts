#
# Turn off routerconsole.advanced
# Run before starting router.
# zzz 12/2011
#
grep -v routerconsole.advanced ~/.i2p/router.config > /tmp/foo$$
echo 'routerconsole.advanced=false' >> /tmp/foo$$
mv /tmp/foo$$ ~/.i2p/router.config
echo 'Advanced console disabled'
