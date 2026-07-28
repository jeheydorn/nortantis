#!/bin/bash

# Resets all macOS privacy permissions (Desktop/Documents/Downloads access, etc.) recorded for Nortantis, so the app is
# prompted again next time it needs them, as if freshly installed. Useful for testing permission-denied behavior.

tccutil reset All nortantis.swing
