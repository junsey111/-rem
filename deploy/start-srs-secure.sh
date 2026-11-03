#!/bin/sh
set -eu
case "${MEDIA_AUTH_KEY:-}" in
  ''|*[!a-zA-Z0-9_-]*) echo 'Set MEDIA_AUTH_KEY to at least 32 URL-safe characters' >&2; exit 1 ;;
esac
[ "${#MEDIA_AUTH_KEY}" -ge 32 ] || exit 1
sed "s/MEDIA_HOOK_KEY/$MEDIA_AUTH_KEY/g" /etc/srs/secure.conf > /tmp/srs-secure.conf
exec ./objs/srs -c /tmp/srs-secure.conf
