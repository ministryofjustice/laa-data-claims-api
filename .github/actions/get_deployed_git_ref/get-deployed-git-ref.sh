#!/usr/bin/env bash

set -euo pipefail

: "${KUBE_NAMESPACE:?KUBE_NAMESPACE is required}"
: "${DEPLOYMENT:?DEPLOYMENT is required}"
: "${IMAGE_PREFIX:?IMAGE_PREFIX is required}"
: "${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"

IMAGE=$(kubectl get deployment "$DEPLOYMENT" \
  --namespace "$KUBE_NAMESPACE" \
  -o jsonpath='{.spec.template.spec.containers[0].image}')

if [ -z "$IMAGE" ]; then
  echo "::error::Unable to determine deployed image for deployment '$DEPLOYMENT'"
  exit 1
fi

IMAGE_NAME="${IMAGE##*/}"
if [[ "$IMAGE_NAME" != *:* ]]; then
  echo "::error::Deployed image does not contain a tag: $IMAGE"
  exit 1
fi
IMAGE_TAG="${IMAGE_NAME##*:}"

echo "image=$IMAGE" >> "$GITHUB_OUTPUT"
echo "image-tag=$IMAGE_TAG" >> "$GITHUB_OUTPUT"

GIT_TAG_PREFIX="${GIT_TAG_PREFIX:-v}"
if [[ "$IMAGE_TAG" != "${IMAGE_PREFIX}-"* ]]; then
  echo "::error::Image tag '$IMAGE_TAG' does not start with expected prefix '${IMAGE_PREFIX}-'"
  exit 1
fi
VERSION_PART="${IMAGE_TAG#${IMAGE_PREFIX}-}"
SNAPSHOT_RE='^[0-9]+\.[0-9]+\.[0-9]+-([0-9a-f]{7,40})-SNAPSHOT$'
RELEASE_RE='^[0-9]+\.[0-9]+\.[0-9]+$'

if [[ "$VERSION_PART" =~ $SNAPSHOT_RE ]]; then
  GIT_REF="${BASH_REMATCH[1]}"
  MATCH_TYPE="snapshot-sha"
elif [[ "$VERSION_PART" =~ $RELEASE_RE ]]; then
  GIT_REF="${GIT_TAG_PREFIX}${VERSION_PART}"
  MATCH_TYPE="release-tag"
else
  echo "::error::Unable to determine git ref from image tag '$IMAGE_TAG' (version part: '$VERSION_PART')"
  exit 1
fi

if ! git rev-parse --verify "${GIT_REF}^{commit}" >/dev/null 2>&1; then
  echo "::error::Resolved git ref '$GIT_REF' does not exist in this repository. Make sure tags/history have been fetched (fetch-depth: 0, fetch-tags: true)."
  exit 1
fi

echo "Currently deployed: $IMAGE"
echo "Resolved git ref: $GIT_REF ($MATCH_TYPE)"
echo "git-ref=$GIT_REF" >> "$GITHUB_OUTPUT"


