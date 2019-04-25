i2p.newsxml server. put su3 files into ./static and serve them

license: public domain

=====

dependancies:

python 2.7 or 3.4+

flask (required):

  # pip install flask

graphs: (optional)
  
  # pip install pygal 

persistant statistics storage: (optional)

  # apt install redis-server python-redis
    

=====

setup:

it binds an http server to 127.0.0.1:9696 by default

run so it is externally accessable via http://your.server.tld/news/ and uses the internal port 1234

  $ ./run.sh http://your.server.tld/news/ 1234

you need to reverse proxy this or use another i2p destination for news

if you do another destination run:

  $ ./run.sh http://your.b32.i2p/ 1234

then point an http SERVER tunnel (not http bidir) to 127.0.0.1 port 1234

    
=====

questions:

ask psi on irc2p
