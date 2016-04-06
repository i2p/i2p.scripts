martinbot

irc monotone bridge bot using ii

dependancies:

    sudo apt-get install ii

setup:

ii acts as an irc bot that announces monotone events to irc

every time monotone pulls it will inform ii via lua hook script "irchook.lua"

crontab entries for non-root user to run monotone:

    00 * * * * mtn pull -d /path/to/mtn.db 'mtn://127.0.0.1:8998?i2p.*' --rcfile=/path/to/irchook.lua
    @reboot /usr/bin/ii -s localhost -p 6668 -n botname

