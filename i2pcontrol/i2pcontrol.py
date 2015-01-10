#!/usr/bin/env python

import argparse
import json
import urllib2
import httplib
import socket
import ssl
import sys
import yaml
from urllib2 import HTTPError, URLError
from string import whitespace

# Info about requestable data can be found at http://i2p2.de/i2pcontrol.html & http://www.i2p2.de/ratestats.html

address = "127.0.0.1" 	# Default I2PControl Address
port = 7650 		# Default I2PControl Port
apiPassword = "itoopie" # Default I2PControl password


## Do not edit below
apiVersion = 1 		# Default API Version
msgId = 1
token = None

def checkToken():
	global token
	if (token == None):
		token = getToken()
		if (token == None):
			print("Unable to login. Quitting..")
			sys.exit()

def getToken():
	loginStr = "{\"id\":" + str(msgId) + ", \"method\":\"Authenticate\",\"params\":{\"API\":" + str(apiVersion) + ", \"Password\":\"" +  apiPassword + "\"}, \"jsonrpc\":\"2.0\"}"

	try:	
		jsonResp = sendMsg(loginStr)
		return jsonResp.get("result").get("Token")

	except HTTPError, e:
		print("HTTPError: %s" % e.reason)
	except URLError, e:
		print("URLError: %s" % e.reason)

def getRate(rateName, ratePeriod):
	checkToken()
	msgStr = "{\"id\":" + str(msgId) + ", \"method\":\"GetRate\",\"params\":{\"Stat\":\"" + rateName + "\", \"Period\":" +  str(ratePeriod) + ", \"Token\":\"" + token +"\" }, \"jsonrpc\":\"2.0\"}"
	jsonResp = sendMsg(msgStr)
	return jsonResp.get("result").get("Result")
	
def getRouterInfo(infoName):
	checkToken()
	## The parameter names in 'params' defines which answers are requested
	msgStr = "{\"id\":" + str(msgId) + ", \"method\":\"RouterInfo\",\"params\":{\""+infoName+"\":\"\", \"Token\":\"" + token +"\" }, \"jsonrpc\":\"2.0\"}"
	jsonResp = sendMsg(msgStr)
	return jsonResp.get("result").get(infoName)

def sendMsg(jsonStr):
		global msgId
		https_handler = UnauthenticatedHTTPSHandler()
		url_opener = urllib2.build_opener(https_handler)
		handle = url_opener.open("https://"+address+":"+ str(port) + "/jsonrpc", jsonStr)
		response = handle.read()
		handle.close()
		msgId = msgId + 1;
		
		jsonResp = json.loads(response)
		if (jsonResp.has_key("error")):
			print ("Remote server: I2PControl Error: " + str(jsonResp.get("error").get("code")) + ", " + jsonResp.get("error").get("message"))
			sys.exit()
		return jsonResp
	
###
# Overrides the version in httplib so that we can ignore server certificate authenticity
# and use SSLv3
###
class UnauthenticatedHTTPSConnection(httplib.HTTPSConnection):
    def connect(self):
        # 
        sock = socket.create_connection((self.host, self.port), self.timeout)
        if self._tunnel_host:
            self.sock = sock
            self._tunnel()
        self.sock = ssl.wrap_socket(sock,
                                    cert_reqs=ssl.CERT_NONE,
									ssl_version=ssl.PROTOCOL_SSLv3)

###
# HTTPS handler which uses SSLv3 and ignores server cert authenticity
###
class UnauthenticatedHTTPSHandler(urllib2.HTTPSHandler):
    def __init__(self, connection_class = UnauthenticatedHTTPSConnection):
        self.specialized_conn_class = connection_class
        urllib2.HTTPSHandler.__init__(self)
    def https_open(self, req):
        return self.do_open(self.specialized_conn_class, req)

def zabbix_config(fileName, outfile):
	yamlDict = dict()
	for line in open(fileName):
		li=line.strip()
		if li.startswith("UserParameter"):
			i2pCtrlOpt = li.strip("UserParameter=").split(",") 
			i2pCtrlOpt[1] = i2pCtrlOpt[1].split()
			i2pCtrlOpt[1].pop(0) # Remove path of this script (i2pcontrol)
			i2pCtrlParams = i2pCtrlOpt[1]
			#print i2pCtrlOpt #Delete me!
			result = ""
			if (i2pCtrlParams[0] == "-i" or i2pCtrlParams[0] == "--router-info"):
				result = getRouterInfo(i2pCtrlParams[1])
			elif (i2pCtrlParams[0] == "-s" or i2pCtrlParams[0] == "--rate-stat"):
				result = getRate(i2pCtrlParams[1], i2pCtrlParams[2])
			else:
				result = "Bad query syntax."
			yamlDict[i2pCtrlParams[1]] = result
	#print yaml.dump(yamlDict)
	yaml.dump(yamlDict, open(outfile,'w'))
	
def from_file(infile, parameter):
	try:
		yamlDict = yaml.load(open(infile,'r'))
		print yamlDict[parameter]
	except IOError, e:	
		print "File \""+ infile +"\" couldn't be read."	
def main():
	parser = argparse.ArgumentParser(description='Fetch I2P info via the I2PControl API.')
	parser.add_argument("-i",
		"--router-info", 
		nargs=1,
		metavar="router-info-id",
		dest="router_info", 
		action="store", 
		help="Request info such as I2P version and uptime. Returned info can be of any type. Full list of options at http://www.i2p2.de/i2pcontrol.html. Usage: \"-i i2p.router.version\"")

	parser.add_argument("-s",
		"--rate-stat",
		nargs=2,
		metavar=("rateStatName", "period"),
		dest="rate_stat",		
		action="store",
		help="Request info such as bandwidth, number active peers, clock skew, etc.. The period is measured in ms and must be longer than 60s. Full list at http://www.i2p2.de/ratestats.html. Usage: \"-s bw.receiveBps 3600000\"")

	parser.add_argument("-z",
		"--zabbix",
		nargs=2,
		metavar=("\"path to zabbix_agent.conf\"", "\"path to output file\""),
		dest="zabbix",		
		action="store",
		help="Parse options to request, by reading a zabbix config file for \"UserParameter\"s relating to I2P. Usage: \"-z /etc/zabbix/zabbix_agent.conf\"")

	parser.add_argument("-f",
		"--from-file",
		nargs=1,
		metavar=("\"path to input file\""),
		dest="from_file",		
		action="store",
		help="Parse options to request, by reading a zabbix config file for \"UserParameter\"s relating to I2P. Usage: \"-z /etc/zabbix/zabbix_agent.conf\"")
	
	if (len(sys.argv) == 1):
		parser.parse_args(["-h"])
	options = parser.parse_args()

	if ((options.rate_stat != None) and (options.router_info != None)):
		print("Error: Choose _one_ option. \n\n")
		parser.parse_args(["-h"])
	
	if ((options.zabbix != None) and ((options.rate_stat != None) or (options.router_info != None) or (options.from_file != None))):
		print("Error: Don't combine option --zabbix with other options.\n")
		parser.parse_args(["-h"])

	# From-file can only be used when either router-info or rate-stat is enabled.
	if ((options.from_file != None) and (options.rate_stat == None) and (options.router_info == None)):
		print("Error: --from-file must be used with either --router-info or --rate-stat.\n")
		parser.parse_args(["-h"])
		
	if (options.from_file != None):
		if (options.router_info != None):
			from_file(options.from_file[0], options.router_info[0])
		if (options.rate_stat != None):
			from_file(options.from_file[0], options.rate_stat[0])
		sys.exit()

	if (options.rate_stat != None):
		try:
			period = int(options.rate_stat[1])
			if (period < 60000):
				raise ValueError
			print getRate(options.rate_stat[0], period)
		except ValueError, e:
			print("Error: \""+options.rate_stat[1]+"\" is not an integer > 60000 \n\n")
			parser.parse_args(["-h"])
		sys.exit()

	if (options.router_info != None):
		print getRouterInfo(options.router_info[0])
		sys.exit()
	
	if (options.zabbix != None):
		zabbix_config(options.zabbix[0], options.zabbix[1])
		sys.exit()



if __name__ == "__main__":
    main()