#!/usr/bin/env bash

for party in $@; do
    curl -X POST "http://localhost:10021/api/acl-demo/addNode?target=${party}" -H "accept: text/plain"
done
