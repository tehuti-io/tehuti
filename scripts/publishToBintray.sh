#!/usr/bin/env bash

result=${PWD##*/}
if [[ "$result" = "scripts" ]]
then
    echo "script must be run from root project folder, not $PWD"
    exit 1
else
    echo "we are in $PWD and tag is $SOURCE_TAG"

    if [[ $SOURCE_TAG =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
    then
        echo "tag $SOURCE_TAG looks like a semver so proceeding with bintray publish"
        git status
        git describe --tags
        #enable below to test the bintrayUpload process, it wouldn't do the actual uploading
        #./gradlew bintrayUpload -Pbintray.dryRun --info
        ./gradlew bintrayUpload --info
    else
        echo "tag $SOURCE_TAG is NOT a valid semantic version (x.y.z) so not publishing to bintray"
    fi
fi
