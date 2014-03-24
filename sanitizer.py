#!/usr/bin/env python

# pasted by zab to http://pastethis.i2p/show/1532/

import sys

if len(sys.argv) < 4 :
   print >> sys.stderr, "usage %s <logfile fragment> <sanitized output> <stats>" % sys.argv[0]
   sys.exit(1)

ips = {}

f = open(sys.argv[1])
o = open(sys.argv[2],'w')

for line in f :
   line = line[:-1]
   localIps = {}
   for token in line.split(" ") :
       if token.count(".") != 3 or token.count(":") != 1 :
          continue
       tokenS = token.split(":")
       if len(tokenS) != 2 :
          continue

       if not tokenS[1].isdigit() :
          continue

       tokenS = tokenS[0].split(".")
       if len(tokenS) != 4 :
          continue

       allDigits = True
       for s in tokenS :
          allDigits &= s.isdigit()
       if not allDigits :
          continue

       if ips.has_key(token) :
          replacement = ips[token]
       else :
          replacement = "ip_%d" % len(ips)
          ips[token] = replacement
       localIps[token] = replacement

   for token,replacement in localIps.iteritems() :
         line = line.replace(token,replacement)
   print >> o, line
o.close()

stats = open(sys.argv[3],'w')
print >> stats, ("total unique ips %d" % len(ips))
print >> stats, "==========="
for ip,replacement in ips.iteritems() :
    print >> stats, "%s -> %s" % (ip, replacement)
stats.close()
