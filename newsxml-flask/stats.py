#
# -*- coding: utf-8 -*-
#
import datetime
import json
import logging
import operator
import os
import time

# try importing redis redis
try:
    import redis
except ImportError:
    print("redis not available, fall back to volatile stats backend")
    redis = None
    
# try importing pygal
try:
    import pygal
except ImportError:
    print("pygal not available, fall back to text based stats")
    pygal = None
pygal =None
    
__doc__ = """
statistics backend optionally using redis
"""


class RedisDB:
    """
    redis based backend for storing stats
    """
    def __init__(self):
        self._redis = redis.Redis()
        self.exists = self._redis.exists
        self.get = self._redis.get
        self.set = self._redis.set

class DictDB:
    """
    volatile dictionary based database backend for storing stats in memory
    """
    def __init__(self):
        self._d = dict()

    def get(self, k):
        if self.exists(k):
            return self._d[k]
        
    def set(self, k, v):
        self._d[k] = v

    def exists(self, k):
        return k in self._d

    
class Grapher:
    """
    generic grapher that does nothing
    """

    def collect(self, data_sorted, multiplier, calc_netsize):
        """
        do the magic calculations
        yields (x, netsize_y, rph_y)
        """
        total = 0
        hours = 0
        req_s = []
        netsize_s = []
        window = []
        for hour, val in data_sorted:
            years = hour / ( 365 * 24 )
            days = ( hour - years * 365 * 24 ) / 24
            hours = hour - ( ( years * 365 * 24 ) + ( days * 24 ) )
            hour = datetime.datetime.strptime('%0.4d_%0.3d_%0.2d' % (years, days, hours), '%Y_%j_%H')
            if val > 0:
                total += val
                hours += 1
                per_hour = float(total) / hours
                window.append(val)
                while len(window) > window_len:
                    window.pop(0)
                mean = sum(window) / len(window)
                netsize = int(calc_netsize(mean, multiplier))
                yield (hour, netsize, val)
                
    def generate(self, data_sorted, multiplier, calc_netsize):
        """
        :param data_sorted: sorted list of (hour, hitcount) tuple
        :param multiplier: multiplier to use on graph Y axis
        :param calc_netsize: function that calculates the network size given a mean value and multiplier
        :return (netsize, requests) graph tuple:
        """

class SVGText:
    """
    svg hold text
    """
    def __init__(self, data='undefined'):
        self.data = data
    
    def render(self):
        return """<?xml version="1.0" standalone="no"?>
<svg viewBox="0 0 80 40" xmlns="http://www.w3.org/2000/svg">
    <desc>fallback svg</desc>
    <rect x="0" y="0" width="80" height="40" stroke="red" fill="None">
    </rect>
    <text x="30" y="20">{}</text>
</svg>
        """.format(self.data)
    
class TextGrapher(Grapher):
    """
    generates svg manually that look like ass
    """

    def generate(self, data_sorted, multiplier, calc_netsize):
        nsize = 0
        rph = 0
        t = 0
        for hour, netsize, reqs in self.collect(data_sorted, multiplier, calc_netsize):
            t += 1
            nsize += netsize
            rpy += reqs
        if t:
            nsize /= t
            rph /= t
        return SVGText("MEAN NETSIZE: {} routers".format(nsize)), SVGText("MEAN REQUETS: {} req/hour".format(rph))
        
class PygalGrapher(Grapher):
    """
    generates svg graphs using pygal
    """
    
    def generate(self, data_sorted, multiplier, calc_netsize):

        _netsize_graph = pygal.DateY(show_dots=False,x_label_rotation=20)
        _requests_graph = pygal.DateY(show_dots=False,x_label_rotation=20)
        
        _netsize_graph.title = 'Est. Network Size (multiplier: %d)' % multiplier
        _requests_graph.title = 'Requests Per Hour'

        netsize_s, req_s = list(), list()
        for hour, netsize, reqs in self.collect(data_sorted, multiplier, calc_netsize):
            netsize_s.append((hour, netsize))
            req_s.append((hour, reqs))
            
        _netsize_graph.add('Routers', netsize_s)
        _requests_graph.add('news.xml Requests', req_s)
        return _netsize_graph, _requests_graph

        
class StatsEngine:
    """
    Stats engine for news.xml
    """

    _log = logging.getLogger('StatsEngine')

    def __init__(self):
        self._cfg_fname = 'settings.json'
        if redis:
            self._db = RedisDB()
            try:
                self._db.exists('nothing')
            except:
                self._log.warn("failed to connect to redis, falling back to volatile stats backend")
                self._db = DictDB()
        else:
            self._db = DictDB()
        if pygal:
            self._graphs = PygalGrapher()
        else:
            self._graphs = TextGrapher()
        
        self._last_hour = self.get_hour()
        
    def _config_str(self, name):
        with open(self._cfg_fname) as f:
            return str(json.load(f)[name])
        
    def _config_int(self, name):
        with open(self._cfg_fname) as f:
            return int(json.load(f)[name])

    def multiplier(self):
        return self._config_int('mult')	

    def tslice(self):
        return self._config_int('slice')

    def window_len(self):
        return self._config_int('winlen')

    @staticmethod
    def get_hour():
        """
        get the current our as an int
        """
        dt = datetime.datetime.utcnow()
        return dt.hour + (int(dt.strftime('%j')) * 24 ) + ( dt.year * 24 * 365 )

    def calc_netsize(self, per_hour, mult):
        return float(per_hour) * 24 / 1.5 * mult

    @staticmethod
    def _hour_key(hour):
        return 'newsxml.hit.{}'.format(hour)
        
    def hit(self, lang=None):
        """
        record a request
        """
        hour = self.get_hour()
        keyname = self._hour_key(hour)
        if not self._db.exists(keyname):
            self._db.set(keyname, '0')
        val = self._db.get(keyname)
        self._db.set(keyname, str(int(val) + 1))
        
    def _load_data(self, hours):
        """
        load hit data 
        """
        hour = self.get_hour() 
        data = list()
        while hours > 0:
            keyname = self._hour_key(hour)
            val = self._db.get(keyname)
            if val:
                data.append((hour, int(val)))
            hour -= 1
            hours -= 1
        return data
            
    def regen_graphs(self, tslice, window_len, mult):
        data = self._load_data(tslice)
        data_sorted = sorted(data, key=operator.itemgetter(0))
        if len(data_sorted) > tslice:
            data_sorted = data_sorted[-tslice:]
        self._netsize_graph, self._requests_graph = self._graphs.generate(data_sorted, self.multiplier(), self.calc_netsize)
        


    def netsize(self, tslice, window, mult):
        #if not hasattr(self,'_netsize_graph'):
        self.regen_graphs(tslice, window, mult)
        return self._netsize_graph.render()

    def requests(self, tslice, window, mult):
        #if not hasattr(self,'_requests_graph'):
        self.regen_graphs(tslice, window, mult)
        return self._requests_graph.render()


engine = StatsEngine()
