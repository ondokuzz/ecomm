#!/bin/sh
# Registers every schema under SCHEMAS_DIR (default: ./schemas) in the Apicurio registry at
# REGISTRY_URL (its v3 API, e.g. http://localhost:8088/apis/registry/v3), as the artifact
# <topic>-value in the default group: the one a producer's serializer looks up for <topic>.
#
# Each artifact gets a BACKWARD compatibility rule. A schema that is already the latest version is
# left alone; a changed one becomes a new version only if it is backward compatible, so a field made
# required or removed is refused, and the script exits non-zero. Needs only sh, sed, tr and curl.
set -eu

registry=${REGISTRY_URL:?Set REGISTRY_URL to the registry v3 API, e.g. http://localhost:8088/apis/registry/v3}
dir=${SCHEMAS_DIR:-$(dirname "$0")/schemas}
artifacts="$registry/groups/default/artifacts"
response=$(mktemp)
trap 'rm -f "$response"' EXIT
status=0

# POST $2 to $1 and print the HTTP status; the body lands in $response.
post() {
  curl -sS -o "$response" -w '%{http_code}' -X POST -H 'Content-Type: application/json' --data "$2" "$1"
}

require_backward_compatibility() {
  code=$(post "$artifacts/$1/rules" '{"ruleType":"COMPATIBILITY","config":"BACKWARD"}')
  case $code in
    204 | 409) ;; # added, or already there
    *) echo "$1: could not add the compatibility rule ($code): $(cat "$response")" >&2; exit 1 ;;
  esac
}

for file in "$dir"/*.json; do
  topic=$(basename "$file" .json)
  artifact="$topic-value"
  # The schema as a JSON string: newlines dropped, backslashes and quotes escaped.
  content=$(tr -d '\r\n' <"$file" | sed 's/\\/\\\\/g; s/"/\\"/g')
  body="{\"artifactId\":\"$artifact\",\"artifactType\":\"JSON\",\"firstVersion\":{\"content\":{\"content\":\"$content\",\"contentType\":\"application/json\"}}}"

  # An existing artifact gets its rule first, so the new version is checked against it.
  existed=$(curl -sS -o /dev/null -w '%{http_code}' "$artifacts/$artifact")
  [ "$existed" = 200 ] && require_backward_compatibility "$artifact"

  code=$(post "$artifacts?ifExists=FIND_OR_CREATE_VERSION" "$body")
  if [ "$code" = 200 ]; then
    version=$(sed -n 's/.*"version":{"version":"\([^"]*\)".*/\1/p' "$response")
    echo "$topic: version $version"
    [ "$existed" = 200 ] || require_backward_compatibility "$artifact"
  else
    echo "$topic: refused ($code): $(cat "$response")" >&2
    status=1
  fi
done

exit $status
