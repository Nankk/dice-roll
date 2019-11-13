#!/bin/bash

cd ..
npx shadow-cljs release main
if test $? -ne 0; then
    echo Compilation failed.
    exit 1;
fi
cd resources
git add -A
git commit -m ":+1: Changed some code... this part is up to you."
if test $? -ne 0; then
    echo Commit failed.
    exit 1;
fi
git push heroku master
if test $? -ne 0; then
    echo Pushing changes failed.
    exit 1;
fi

