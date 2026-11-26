#!/usr/bin/env bash
set -euo pipefail
path="${1:-.}"
disk=$(df -Pk "$path" | awk 'NR==2{print $4}')
ram=$(awk '/MemTotal/{print $2}' /proc/meminfo)
printf 'CPU: %s (%s threads)\nRAM MiB: %s\nFree disk MiB: %s\n' "$(uname -m)" "$(nproc)" "$((ram/1024))" "$((disk/1024))"
if [[ "$(uname -m)" == x86_64 ]] && (( disk >= 150*1024*1024 && ram >= 8*1024*1024-262144 )); then
  echo 'READY=yes'
else
  echo 'READY=no'
  echo 'Native build requires x86_64 Linux, at least 150 GiB available for our checkout/build and about 8 GiB RAM minimum.'
fi
