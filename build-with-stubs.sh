#!/bin/bash

# Build script that handles stub dependencies correctly
# This script builds projects in the correct order to avoid circular dependencies

set -e  # Exit on any error

echo "🔨 Building projects with stub dependency handling..."

# Step 1: Build core libraries (no stub dependencies)
echo "📦 Step 1: Building core libraries..."
./gradlew :jar-common:build :jar-auth:build

# Step 2: Build services and publish stubs (in correct order)
echo "📦 Step 2: Building services and publishing stubs..."
./gradlew :svc-data:build
./gradlew :svc-data:pubStubs
./gradlew :svc-position:build
./gradlew :svc-position:pubStubs
./gradlew :svc-event:build

# Step 3: Build client libraries (now stubs are available)
echo "📦 Step 3: Building client libraries..."
./gradlew :jar-client:build :jar-shell:build

echo "✅ All projects built successfully with stub dependencies handled!"
