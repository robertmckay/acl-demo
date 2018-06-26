#!/usr/bin/env bash

function killJavaProc() {
    count=$( jps | grep $1 | awk '{ print $1 }' | wc -l | awk '{ print $1 }' )

    if (( count > 0 )); then
        echo -e "Stopping $count $1 nodes."
        jps | grep $1 | awk '{ print $1 }'
        jps | grep $1 | awk '{ print $1 }' | xargs kill -9
        echo -e "Done.\n"
    fi
}


killJavaProc 'Corda'
killJavaProc 'jar'
killJavaProc 'WebServer'

