# ion-appsync-example

## Wiring up Datomic Cloud Ions to AWS AppSync

## What is Datomic Cloud, and what are Ions?

## What is AWS AppSync
tl;dr: `AWS API Gateway` : `REST` :: `AWS AppSync` : `GraphQL`

## What are we going to do in this repo?
penpineappleapplepen!

## You Will Need:
- A running Datomic Cloud cluster
- That cluster's compute stack name (img here)
- A copy of `ion-starter` that has been `push`ed and `deploy`ed to your Cloud instance
- This repo

## What are we going to build today?
- AWS Cognito application
- AWS Cognito UserPool to authenticate access
- AWS AppSync GraphQL service
- Data sources that expose the `ion-starter` lambdas to AppSync
- Resolvers that let us query items by type and add an item
- Subscription that lets us track items by type over time
- Simple CLJS web client for the GraphQL API

## How to do it
