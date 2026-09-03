#!/bin/sh
# Do not enable shell tracing: credentials must never enter startup logs/argv.
set -efu

fail() {
    echo 'Required runtime credentials are missing or invalid' >&2
    exit 64
}

[ -n "${FINGUARD_REQUIRED_SECRETS:-}" ] || fail
for name in $FINGUARD_REQUIRED_SECRETS; do
    case "$name" in
        [0-9]*|*[!A-Z0-9_]*|'') fail ;;
    esac
    [ -r "/run/secrets/$name" ] || fail
    value="$(cat "/run/secrets/$name")"
    case "$value" in
        *[![:space:]]*) ;;
        *) fail ;;
    esac
    export "$name=$value"
done
unset value name

# Avoid Spring validation reporting two equal secret values in a startup error.
if [ -n "${FINGUARD_API_VIEWERCREDENTIAL:-}" ] &&
    [ "${FINGUARD_API_VIEWERCREDENTIAL}" = "${FINGUARD_API_OPERATORCREDENTIAL:-}" ]; then
    fail
fi

exec "$@"
