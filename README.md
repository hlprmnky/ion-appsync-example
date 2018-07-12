# ion-appsync-example

## Wiring up Datomic Cloud Ions to AWS AppSync
If reading that header fills you with joy and anticipation, you can probably skip down to `You Will Need:` and get building. If your reaction is closer to  
_What's a Datomic? Why would you ionize a cloud? Who is AppSync?_  
Fear not! Keep reading right here, and by the time we get to the `You Will Need:` part, you should understand why you might want to build one of what we have on offer in this repository.

## What is AWS AppSync?
### tl;dr: `AWS API Gateway` : `REST` :: `AWS AppSync` : `GraphQL`
After almost a decade of eating the world, REST has recently begun to see competiton in the form of [GraphQL](https://www.graphql.org). Developed at Facebook, GraphQL is a model for querying and mutating data from an API endpoint that is very different from REST in several ways that I won't go into great detail about in this README. I will simply say that many people and organizations have found it to be a good fit for their problem space. Standing up a GraphQL server in AWS has historically been the province of running servers, either Docker containers in Elastic Container Service or plain EC2 machines, which are then hooked up to load balancers, monitored, patched, and so forth.  
That changed last April 13 at [React Amsterdam] (https://youtu.be/P_mGa91wZ4o "YouTube recording of AWS AppSync talk"), when AWS announced the GA of AWS AppSync, which is a fully managed service for GraphQL APIs in much the same way that AWS API Gateway is a fully-managed service for REST. Since the announcement, interest in and attention to AppSync in the AWS and especially the #Serverless community has been, well... ![sohotrightnow "Very high"](./doc/images/sohotrightnow.gif)

## What is Datomic Cloud, and what are Ions?

## What are we going to do in this repo?
Take two amazing tools, Datomic Cloud Ions and AWS Appsync, and _smash them together_ into one _really really great_ tool!
![ppap "Pen pineapple apple pen!"](./doc/images/ppap.gif)`

## You Will Need:
- A local Clojure dev environment that can run Clojure 1.9 and the new command-line tools/`tools.deps`
  Documentation for install and setup of Clojure is available [here](https://clojure.org/guides/getting_started)
- An AWS account with a running Datomic Cloud cluster (free tier account Solo topology Datomic Cloud is 100% fine for this)
  Get up and running with Datomic Cloud using the instructions [here](https://docs.datomic.com/cloud/setting-up.html)
- That cluster's compute stack name ![compute_stack "Datomic Compute Stack in AWS Cloudformation"](./doc/images/datomic_cloud_stack.png)
- To run the terminal commands for interacting with AWS CloudFormation in this `README`, the [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-welcome.html)
It is also possible to use the [AWS Web console](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console.html) to stand up CloudFormation resources if you prefer.
- A checkout or fork of [the Datomic `ion-starter` example](https://github.com/datomic/ion-starter) that has been `:push`ed and `:deploy`ed to your Cloud instance as outlined in that repo's `README.md`
- A checkout or fork of this repository
- (optional) An install of [GraphiQL](https://graphiql.org/) or another GraphQL client

## What are we going to build today?
This repository contains AWS CloudFormation templates and Clojure source code to enrich a Datomic Cloud environment which already has loaded in the `ion-starter` ion fns with:


- an AWS AppSync GraphQL service with a schema that models the `ion-starter` dataset
- AppSync Data sources that expose the `datomic.ion.starter/items-by-type` and `datomic.ion.starter/add-item` lambdas to AppSync
- AppSync Resolvers that let us query `items-by-type` and mutate `add-item`
- An AppSync Subscription that lets us track `items-by-type` over time
- (optionally) an AWS Cognito UserPool to authenticate access
- (optionally) an AWS Cognito application
- (optionallly) a simple CLJS web client for the GraphQL API

## How to do it

### Inital setup
- Start by spinning up a Datomic Cloud cluster and noting the name of the _compute stack_ - this value will be a parameter to one of our CloudFormation templates.
- Deploy the `ion-starter` schema, dataset, and ions, following the instructions in the `ion-starter` repo

### Deploy this repository's new ions
In order for the AppSync stack deploy to succeed, the AWS Lambda functions for the `items-by-type-json` and `items-by-type-gql` ions need to exist in your AWS account. Fortunately, that's as simple as running a standard Ions deploy phase from this repo:
```bash
clj -A:dev -m datomic.ion.dev '{:op :push}' # note 'rev' value from return statement
clj -A:dev -m datomic.ion.dev '{:op :deploy :rev "<rev value from push>" :group "<your compute stack name>"}'
```
Once this deployment is successful, it's time to stand up the AWS AppSync pieces!

### Stand up the AppSync integration
#### Option 1: Create an unauthenticated GraphQL API
This is the simplest way to stand up the AppSync infrastructure with a simple API-key based auth setup. If you don't have an existing method for authorizing users to hit an API you'd rather use, or aren't interested in combining AppSync with a more-robust user authentication scheme, use this method. *Note* that this will _not_ work with the SPA or (planned future) React Native apps in this repository, it is only appropriate for "taking the API for a spin" with GraphiQL or another GraphQL client. For an example of a fully-functional full-stack Ion-backed app, see Option 2 below.

- Run `scripts/appsync-simple.sh`, providing the name of the _compute stack_ as a parameter:
```bash
./scripts/appsync-simple.sh --stack-name <your stack name>
```
After the stack is created, you can find the API key ![here]()
or with the following command:
```shell
aws appsync ...
```

#### Option 2: Production-ready API with AWS Cognito User Pools
This method creates a real, production-ready application with authentication and user management provided by AWS Cognito. It is included in this repository because You can provide your own AWS Cognito User Pool and client to the AppSync API using this method.
- (optional) Launch a CloudFormation stack from the `templates/cognito.yml` template file to create the AWS Cognito infrastructure to control user authentication to the AppSync API:
```bash
aws cloudformation create-stack ...
```
- Launch a second CloudFormation stack from the `templates/appsync.yml` template file to create the AWS AppSync resources: 
```bash
aws cloudformation create-stack ...
```

### Take a look at your new AppSync API

#### TODO: process for login via GraphiQL or the AppSync console
Note that `datomic.ion.starter/items-by-type` on its own doesn't return _JSON_ but _edn_; in order to use the underlying `items-by-type*` fn in the larger AWS ecosystem, we are going to need a version that emits JSON. You can find that added to `core.clj` and `config.edn` as `items-by-type-json`:
```clojure
(defn items-by-type-json
  "items-by-type starter ion modified to emit JSON for consumption by the AWS service ecosystem"
  [{:keys [input]}]
  (let [type (keyword (get (json/read-str input) "type"))
        conn (d/connect (get-client) {:db-name "datomic-docs-tutorial"})]
    ;; NOTE that conn can - and should be - parameterized in production builds.
    ;; See the ion-starter repo and get-connection for a better but more verbose approach
    (->> (items-by-type* (d/db conn) type)
          json/write-str)))
```

*Note* that the method for creating a connection in this and `items-by-type-gql` below is not an optimal approach for a production setting. Please review `starter.clj` in the [ion-starter repo](https://github.com/datomic/ion-starter) for a more "production-y" method, which is not duplicated here in order to avoid pulling in all the machinery for transacting the sample dataset and schema.

Perform another `:push` and `:deploy` to your Cloud cluster and you have an ion which can back an AppSync resolver. Modify your `graphql-api.yml` template so that `AppSyncLambdaIonPolicy` contains a reference to the new resource: 
```YAML
- !Sub arn:aws:lambda:${AWS::Region}:${AWS::AccountId}:function:${ComputeStackName}-items-by-type-json
```
Then 
