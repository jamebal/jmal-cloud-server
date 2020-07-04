#!/bin/bash

etc/nginx/sbin/nginx -g daemon off;

/usr/bin/mongod

exec "$@"
