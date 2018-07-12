#!/usr/bin/env bash

set -x

mkdir dist
rm dist/handlers.zip

shadow-cljs release :pages

zip -rq dist/handlers.zip node_modules
zip -j dist/handlers.zip out/release/handlers.js

bucketname="$1-nodejs-pages"

aws s3api put-object --bucket $bucketname --key pages/handlers.zip --body dist/handlers.zip --acl private

aws lambda update-function-code --function-name $1-page-home --region $2 --s3-bucket $bucketname --s3-key pages/handlers.zip
aws lambda update-function-code --function-name $1-page-app  --region $2 --s3-bucket $bucketname --s3-key pages/handlers.zip