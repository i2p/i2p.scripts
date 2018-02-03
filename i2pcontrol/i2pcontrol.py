#!/usr/bin/env python

#
# If it fails "No module named yaml"
# then sudo apt install python-yaml
#

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

# Info about requestable data can be found at https://geti2p.net/i2pcontrol.html & https://geti2p.net/ratestats.html

address = "127.0.0.1" 	# Default I2PControl Address
port = 7650 		# Default I2PControl Port
usessl = 1		# Change to 0 for HTTP
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
	
def getControlInfo(infoName):
	checkToken()
	## The parameter names in 'params' defines which answers are requested
	if ("=" in infoName):
		toks = infoName.split("=", 2);
		infoName = toks[0];
		infoValue = toks[1];
		msgStr = "{\"id\":" + str(msgId) + ", \"method\":\"I2PControl\",\"params\":{\""+infoName+"\":\""+infoValue+"\", \"Token\":\"" + token +"\" }, \"jsonrpc\":\"2.0\"}"
	else:
		return "Parameter value required for " + infoName
	jsonResp = sendMsg(msgStr)
	return "I2PControl setting " + infoName + " set to " + infoValue
	
def getRouterManagerInfo(infoName):
	checkToken()
	## The parameter names in 'params' defines which answers are requested
	msgStr = "{\"id\":" + str(msgId) + ", \"method\":\"RouterManager\",\"params\":{\""+infoName+"\":\"\", \"Token\":\"" + token +"\" }, \"jsonrpc\":\"2.0\"}"
	jsonResp = sendMsg(msgStr)
	if (infoName == "FindUpdates" or infoName == "Update"):
		return jsonResp.get("result").get(infoName)
	else:
		return "Sent Router Manager command: " + infoName
	
def getNetworkInfo(infoName):
	checkToken()
	isset = 0;
	## The parameter names in 'params' defines which answers are requested
	if ("=" in infoName):
		toks = infoName.split("=", 2);
		isset = 1;
		infoName = toks[0];
		infoValue = toks[1];
		msgStr = "{\"id\":" + str(msgId) + ", \"method\":\"NetworkSetting\",\"params\":{\""+infoName+"\":\""+infoValue+"\", \"Token\":\"" + token +"\" }, \"jsonrpc\":\"2.0\"}"
	else:
		msgStr = "{\"id\":" + str(msgId) + ", \"method\":\"NetworkSetting\",\"params\":{\""+infoName+"\":null, \"Token\":\"" + token +"\" }, \"jsonrpc\":\"2.0\"}"
	jsonResp = sendMsg(msgStr)
	if (isset == 1):
		return "Network setting " + infoName + " set to " + infoValue
	else:
		return jsonResp.get("result").get(infoName)

def getAdvancedInfo(infoName):
	checkToken()
	isset = 0;
	## The parameter names in 'params' defines which answers are requested
	if (infoName == "GetAll"):
		msgStr = "{\"id\":" + str(msgId) + ", \"method\":\"AdvancedSettings\",\"params\":{\""+infoName+"\":\"\", \"Token\":\"" + token +"\" }, \"jsonrpc\":\"2.0\"}"
	elif (infoName == "SetAll"):
		return "SetAll unsupported"
	elif ("=" in infoName):
		toks = infoName.split("=", 2);
		isset = 1;
		infoName = toks[0];
		infoValue = toks[1];
		msgStr = "{\"id\":" + str(msgId) + ", \"method\":\"AdvancedSettings\",\"params\":{\"set\":{\""+infoName+"\":\""+infoValue+"\"}, \"Token\":\"" + token +"\" }, \"jsonrpc\":\"2.0\"}"
	else:
		msgStr = "{\"id\":" + str(msgId) + ", \"method\":\"AdvancedSettings\",\"params\":{\"get\":\""+infoName+"\", \"Token\":\"" + token +"\" }, \"jsonrpc\":\"2.0\"}"
	jsonResp = sendMsg(msgStr)
	if (infoName == "GetAll"):
		return jsonResp.get("result").get(infoName)
	elif (isset == 1):
		return "Advanced configuration " + infoName + " set to " + infoValue
	else:
		return jsonResp.get("result").get("get").get(infoName)

def sendMsg(jsonStr):
		global msgId
		https_handler = UnauthenticatedHTTPSHandler()
		url_opener = urllib2.build_opener(https_handler)
		if (usessl != 0):
			handle = url_opener.open("https://"+address+":"+ str(port) + "/jsonrpc/", jsonStr)
		else:
			handle = url_opener.open("http://"+address+":"+ str(port) + "/jsonrpc/", jsonStr)
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
###
class UnauthenticatedHTTPSConnection(httplib.HTTPSConnection):
    def connect(self):
        # 
        sock = socket.create_connection((self.host, self.port), self.timeout)
        if self._tunnel_host:
            self.sock = sock
            self._tunnel()
        self.sock = ssl.wrap_socket(sock,
                                    cert_reqs=ssl.CERT_NONE)

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
	global address
	global port
	global usessl
	parser = argparse.ArgumentParser(description='Fetch I2P info via the I2PControl API.')
	parser.add_argument("-l",
		"--host", 
		nargs=1,
		metavar="host",
		dest="address", 
		action="store", 
		help="Listen host address of the i2pcontrol server")

	parser.add_argument("-p",
		"--port", 
		nargs=1,
		metavar="port",
		dest="port", 
		action="store", 
		help="Port of the i2pcontrol server")

	parser.add_argument("-x",
		"--no-ssl", 
		dest="http", 
		action="store_true", 
		help="Use HTTP instead of HTTPS")

	parser.add_argument("-i",
		"--router-info", 
		nargs=1,
		metavar="info",
		dest="router_info", 
		action="store", 
		help="Request info such as I2P version and uptime. Returned info can be of any type. Full list of options at https://geti2p.net/i2pcontrol.html. Usage: \"-i i2p.router.version\"")

	parser.add_argument("-c",
		"--i2pcontrol-info", 
		nargs=1,
		metavar="key[=value]",
		dest="i2pcontrol_info", 
		action="store", 
		help="Change settings such as password. Usage: \"-c i2pcontrol.password=foo\"")

	parser.add_argument("-r",
		"--routermanager-info", 
		nargs=1,
		metavar="command",
		dest="routermanager_info", 
		action="store", 
		help="Send a command to the router. Usage: \"-r FindUpdates|Reseed|Restart|RestartGraceful|Shutdown|ShutdownGraceful|Update\"")

	parser.add_argument("-n",
		"--network-info", 
		nargs=1,
		metavar="key[=value]",
		dest="network_info", 
		action="store", 
		help="Request info such as bandwidth. Usage: \"-n i2p.router.net.bw.in[=xxx]\"")

	parser.add_argument("-a",
		"--advanced-info", 
		nargs=1,
		metavar="key[=value]",
		dest="advanced_info", 
		action="store", 
		help="Request configuration info. Usage: \"-a GetAll|foo[=bar]\"")

	parser.add_argument("-s",
		"--rate-stat",
		nargs=2,
		metavar=("rateStatName", "period"),
		dest="rate_stat",		
		action="store",
		help="Request info such as bandwidth, number active peers, clock skew, etc.. The period is measured in ms and must be longer than 60s. Full list at https://geti2p.net/ratestats.html. Usage: \"-s bw.receiveBps 3600000\"")

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

	# todo we don't check all the options
	if ((options.rate_stat != None) and (options.router_info != None)):
		print("Error: Choose _one_ option. \n\n")
		parser.parse_args(["-h"])
	
	# todo we don't check all the options
	if ((options.zabbix != None) and ((options.rate_stat != None) or (options.router_info != None) or (options.from_file != None))):
		print("Error: Don't combine option --zabbix with other options.\n")
		parser.parse_args(["-h"])

	# From-file can only be used when either router-info or rate-stat is enabled.
	# todo we don't check all the options
	if ((options.from_file != None) and (options.rate_stat == None) and (options.router_info == None)):
		print("Error: --from-file must be used with either --router-info or --rate-stat.\n")
		parser.parse_args(["-h"])
		
	if (options.port != None):
		port = int(options.port[0]);
	if (options.address != None):
		address = options.address[0];
	if (options.http):
		usessl = 0;

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

	if (options.i2pcontrol_info != None):
		print getControlInfo(options.i2pcontrol_info[0])
		sys.exit()

	if (options.routermanager_info != None):
		print getRouterManagerInfo(options.routermanager_info[0])
		sys.exit()

	if (options.network_info != None):
		print getNetworkInfo(options.network_info[0])
		sys.exit()

	if (options.advanced_info != None):
		print getAdvancedInfo(options.advanced_info[0])
		sys.exit()
	
	if (options.zabbix != None):
		zabbix_config(options.zabbix[0], options.zabbix[1])
		sys.exit()



if __name__ == "__main__":
    main()
