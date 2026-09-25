#!/bin/sh
set -eu

action=$1
ip_file=$2
case "$action" in
    pair) action_label=Pairing ;;
    connect) action_label=Connection ;;
    *) printf 'Unsupported adb action: %s\n' "$action" >&2; exit 1 ;;
esac
saved_ip=
if [ -f "$ip_file" ]; then
    IFS= read -r saved_ip < "$ip_file" || true
fi

if [ -n "$saved_ip" ]; then
    printf 'IP address [%s] (Enter to use saved): ' "$saved_ip"
else
    printf 'IP address (Enter to cancel): '
fi
IFS= read -r device_ip || exit 0
device_ip=${device_ip:-$saved_ip}
if [ -z "$device_ip" ]; then
    printf '%s cancelled.\n' "$action_label"
    exit 0
fi
printf '%s\n' "$device_ip" > "$ip_file"

printf '%s port for %s (Enter to cancel): ' "$action_label" "$device_ip"
IFS= read -r device_port || exit 0
if [ -z "$device_port" ]; then
    printf '%s cancelled.\n' "$action_label"
    exit 0
fi
case "$device_port" in
    *[!0-9]*)
        printf 'Invalid port: %s\n' "$device_port" >&2
        exit 1
        ;;
esac
if [ "$device_port" -lt 1 ] || [ "$device_port" -gt 65535 ]; then
    printf 'Port must be between 1 and 65535: %s\n' "$device_port" >&2
    exit 1
fi

adb "$action" "$device_ip:$device_port"
