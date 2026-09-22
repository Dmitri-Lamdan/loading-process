#!/bin/bash

# --- CONFIGURATION ---
IMAGE_NAME=$1
TARGET_USER="lamdan"
TARGET_HOST="linux-pc.local"
TEMP_ARCHIVE="image-transfer.tar.gz"

if [ -z "$IMAGE_NAME" ]; then
    echo "Usage: $0 <image-name>"
    exit 1
fi

echo "Step 1: Exporting image '$IMAGE_NAME'..."
podman save "$IMAGE_NAME" | gzip > "$TEMP_ARCHIVE"

echo "Step 2: Transferring image to $TARGET_HOST..."
scp "$TEMP_ARCHIVE" "$TARGET_USER@$TARGET_HOST:/tmp/"

sed "s|{{IMAGE_NAME}}|$IMAGE_NAME|g" deployment.yaml > /tmp/ready_deployment.yaml

scp /tmp/$TEMP_ARCHIVE /tmp/ready_deployment.yaml "$TARGET_USER@$TARGET_HOST:/tmp/"

echo "Step 3: Loading into Minikube and deploying..."
ssh "$TARGET_USER@$TARGET_HOST" << EOF
    echo "Loading image into Minikube..."
    minikube image load /tmp/$TEMP_ARCHIVE --overwrite

    echo "Verifying image name..."
    # Improved grep to find the exact match in the short list
    ACTUAL_NAME=\$(minikube image ls --format short | grep -E "${IMAGE_NAME%:*}" | head -n 1)

    if [ -z "\$ACTUAL_NAME" ]; then
        echo "Error: Image not found in Minikube after loading."
        exit 1
    fi

    echo "Applying Manifest to Kubernetes..."
    kubectl apply -f /tmp/ready_deployment.yaml

    echo "Cleaning up remote temp files..."
    rm -f /tmp/$TEMP_ARCHIVE /tmp/ready_deployment.yaml
EOF

echo "Cleaning up local temp file..."
rm -f "$TEMP_ARCHIVE"

echo "Done! Image transferred and deployed."