#!/usr/bin/env bash
set -euo pipefail

if [[ ${REGISTRY:-} == "" ]]; then
  echo "Please export REGISTRY=\"<account>.dkr.ecr.<region>.amazonaws.com/<prefix>\""
  exit 1
fi

shopt -s nullglob
FILES=(docker-images/*.tar.gz docker-images/*.tar)
if [ ${#FILES[@]} -eq 0 ]; then
  echo "No .tar or .tar.gz files found in docker-images directory. Exiting."
  exit 1
fi

for img in "${FILES[@]}"; do
  echo "Processing $img..."
  
  # If it's a .tar.gz, extract it first
  if [[ "$img" == *.tar.gz ]]; then
    echo "Extracting $img..."
    gunzip -c "$img" > "${img%.gz}"
    img="${img%.gz}"
  fi
  
  LOADED=$(docker load -i "$img" | grep "Loaded image:" | cut -d' ' -f3)
  if [ -z "$LOADED" ]; then
    echo "Failed to load image from $img. Skipping."
    continue
  fi
  
  # Remove .tar or .tar.gz extension for naming
  BASENAME=$(basename "$img")
  BASENAME="${BASENAME%.tar.gz}"
  BASENAME="${BASENAME%.tar}"
  NEW_NAME="$REGISTRY/$BASENAME"
  
  echo "Tagging as: $NEW_NAME"
  docker tag "$LOADED" "$NEW_NAME"
  echo "Pushing to ECR..."
  docker push "$NEW_NAME"
done


