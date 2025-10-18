#!/usr/bin/env bash
set -euo pipefail

if [[ ${REGISTRY:-} == "" ]]; then
  echo "Please export REGISTRY=\"<account>.dkr.ecr.<region>.amazonaws.com/<prefix>\""
  exit 1
fi

shopt -s nullglob
FILES=(docker-images/*.tar.gz)
if [ ${#FILES[@]} -eq 0 ]; then
  echo "No .tar.gz files found in docker-images directory. Exiting."
  exit 1
fi

for img in "${FILES[@]}"; do
  echo "Processing $img..."
  LOADED=$(docker load -i "$img" | grep "Loaded image:" | cut -d' ' -f3)
  if [ -z "$LOADED" ]; then
    echo "Failed to load image from $img. Skipping."
    continue
  fi
  NEW_NAME="$REGISTRY/$(basename "$img" .tar.gz)"
  docker tag "$LOADED" "$NEW_NAME"
  docker push "$NEW_NAME"
done


