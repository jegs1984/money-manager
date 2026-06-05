#!/bin/bash
set -e

echo "Fixing packaging conflict..."
pip uninstall -y streamlit packaging wheel setuptools
pip install --upgrade setuptools wheel
pip install 'packaging<26,>=20'
pip install streamlit==1.48.0

echo "Verifying..."
pip check
echo "✓ Fixed!"
