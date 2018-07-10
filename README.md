# ion-appsync-example

## Wiring up Datomic Cloud Ions to AWS AppSync
If reading that header fills you with joy and anticipation, you can probably skip down to `You Will Need:` and get building. If your reaction is closer to  
_What's a Datomic? Why would you ionize a cloud? Who is AppSync?_  
Fear not! Keep reading right here, and by the time we get to the `You Will Need:` part, you should understand why you might want to build one of what we have on offer in this repository.

## What is Datomic Cloud, and what are Ions?

## What is AWS AppSync?
### tl;dr: `AWS API Gateway` : `REST` :: `AWS AppSync` : `GraphQL`
After almost a decade of eating the world, REST has recently begun to see competiton in the form of [GraphQL](https://www.graphql.org). Developed at Facebook, GraphQL is a model for querying and mutating data from an API endpoint that is very different from REST in several ways that I won't go into great detail about in this README. I will simply say that many people and organizations have found it to be a good fit for their problem space. Standing up a GraphQL server in AWS has historically been the province of running servers, either Docker containers in Elastic Container Service or plain EC2 machines, which are then hooked up to load balancers, monitored, patched, and so forth.  
That changed last April 13 at [React Amsterdam] (https://youtu.be/P_mGa91wZ4o "YouTube recording of AWS AppSync talk"), when AWS announced the GA of AWS AppSync, which is a fully managed service for GraphQL APIs in much the same way that AWS API Gateway is a fully-managed service for REST. Since the announcement, interest in and attention to AppSync in the AWS and especially the #Serverless community has been, well... ![sohotrightnow "Very high"](./doc/images/appsyncsohot.png)

## What are we going to do in this repo?
Take two amazing tools, Datomic Cloud Ions and AWS Appsync, and _smash them together_ into one _really really great_ tool!
![ppap "Pen pineapple apple pen!"](./doc/images/ppap.gif)`

## You Will Need:
- A local Clojure dev environment that can run Clojure 1.9 and the new command-line tools/`tools.deps`
  Documentation for install and setup of Clojure is available [here](https://clojure.org/guides/getting_started)
- An AWS account with a running Datomic Cloud cluster (free tier account Solo topology Datomic Cloud is 100% fine for this)
  Get up and running with Datomic Cloud using the instructions [here](https://docs.datomic.com/cloud/setting-up.html)
- That cluster's compute stack name (img here)
- A checkout or fork of [the Datomic `ion-starter` example](https://github.com/datomic/ion-starter) that has been `:push`ed and `:deploy`ed to your Cloud instance
- A clone or fork of this repo

## What are we going to build today?
- AWS Cognito application
- AWS Cognito UserPool to authenticate access
- AWS AppSync GraphQL service
- AppSync Data sources that expose the `ion-starter` lambdas to AppSync
- AppSync Resolvers that let us query items by type and add an item
- An AppSync Subscription that lets us track items by type over time
- Simple CLJS web client for the GraphQL API

## How to do it

### Inital setup
Start by spinning up an 

Note that `items-by-type` on its own doesn't return _JSON_ but _edn_; in order to use the underlying `items-by-type*` fn in the larger AWS ecosystem, we are going to need a version that emits JSON. You can find that added to `core.clj` and `config.edn` as `items-by-type-json`:
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