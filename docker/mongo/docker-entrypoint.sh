#!/bin/bash

etc/nginx/sbin/nginx -g daemon off;

/usr/bin/mongod --bind_ip=0.0.0.0

exec "$@"
