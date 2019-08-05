#!/bin/bash

function printColor() {
    printInColor "1;35" "$1" "1;32" "$2"
}

function printUsage() {
    usageMsg=$1
    printColor "\nUsage example:" "$usageMsg"
}

function printInColor() {
    color1=$1
    message1=$2
    color2=$3
    message2=$4
    echo -e "\033[${color1}m${message1}\033[m\033[${color2}m$message2\033[m"
}