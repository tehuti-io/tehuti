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
        echo "tag $SOURCE_TAG looks like a semver so proceeding with jfrog publish"
        git status
        git describe --tags
        ./gradlew publishAllPublicationsToLinkedInJfrogRepository
    else
        echo "tag $SOURCE_TAG is NOT a valid semantic version (x.y.z) so not publishing to jfrog"
    fi
fi
