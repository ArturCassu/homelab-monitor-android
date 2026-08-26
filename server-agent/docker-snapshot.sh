#!/bin/sh
set -eu

runtime_dir=/run/homelab-metrics
target="$runtime_dir/docker.tsv"
temporary="$runtime_dir/docker.tsv.tmp.$$"

umask 077
/usr/bin/docker ps --all --format '{{.Names}}\t{{.State}}\t{{.Status}}\t{{.Image}}' > "$temporary"
chown homelab-metrics:homelab-metrics "$temporary"
chmod 0640 "$temporary"
mv -f "$temporary" "$target"
