#!/usr/bin/env bash

if [ $# -ne 4 ]
  then
    echo "install.sh <stack name> <api name> <aws region> <compute stack name> "
  else
    set -x

    aws --region $3 cloudformation create-stack --stack-name $1 --template-body file://../templates/appsync-simple.yml\
        --capabilities CAPABILITY_NAMED_IAM\
        --parameters\
        ParameterKey=APIName,ParameterValue=$2 \
        ParameterKey=ComputeStackName,ParameterValue=$4
fi
