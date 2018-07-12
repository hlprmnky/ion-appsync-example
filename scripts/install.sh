#!/usr/bin/env bash

if [ $# -ne 6 ]
  then
    echo "install.sh <stack name> <aws region> <cognito pool id> <cognito domain> <cognito client id> <cognito client secret> "
  else
    set -x

    aws --region $2 cloudformation create-stack --stack-name $1 --template-body file://target/templates/cf/node-pages/create-stack.json\
        --capabilities CAPABILITY_NAMED_IAM\
        --parameters\
        ParameterKey=Application,ParameterValue=$1\
        ParameterKey=CognitoPoolId,ParameterValue=$3\
        ParameterKey=CognitoDomain,ParameterValue=$4\
        ParameterKey=CognitoClientId,ParameterValue=$5\
        ParameterKey=CognitoClientSecret,ParameterValue=$6\

fi