#!/bin/bash

GITHUB_HOST="github.com"
KNOWN_HOSTS_FILE="$HOME/.ssh/known_hosts"

mkdir -p "$HOME/.ssh"
touch "$KNOWN_HOSTS_FILE"

if grep -q "$GITHUB_HOST" "$KNOWN_HOSTS_FILE"; then
    echo "$GITHUB_HOST is already in the list of known hosts."
else
    ssh-keyscan -H "$GITHUB_HOST" >> "$KNOWN_HOSTS_FILE"
    echo "$GITHUB_HOST added to the list of known hosts."
fi
