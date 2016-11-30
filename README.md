# friboo

![Maven Central](https://img.shields.io/maven-central/v/org.zalando.stups/friboo.svg)
[![Build Status](https://travis-ci.org/zalando/friboo.svg?branch=master)](https://travis-ci.org/zalando/friboo)
[![codecov](https://codecov.io/gh/zalando/friboo/branch/master/graph/badge.svg)](https://codecov.io/gh/zalando/friboo)

**Friboo** is a lightweight utility library for writing microservices in Clojure. It provides several components that you can use with Stuart Sierra's [Component lifecycle framework](https://github.com/stuartsierra/component).

Friboo encourages an "API First" approach based on the [Swagger specification](http://swagger.io/). As such, the REST API is defined as YAML.

## Leiningen dependency

    [org.zalando.stups/friboo 2.0.0]

## Why Friboo?

- Friboo allows you to first define your API in a portable, language-agnostic format, and then implement it (with the help of [swagger1st](https://github.com/sarnowski/swagger1st)).
- It contains ready-made components/building blocks for your applications: An HTTP server, DB access layer, metrics registry, Hystrix dashboard (in case you have compliance requirements to follow), and more. See [Components](#components).
- Pluggable support for all authentication mechanisms (basic, OAuth 2.0, API keys). 
- It contains the "glue code" for you, and there is already a recommended way of doing things.

## Development Status

In our production we use an extension library that is based on Friboo: [friboo-ext-zalando](https://github.com/zalando-incubator/friboo-ext-zalando).
See the list at the end of this page.
However, there is always room for improvement, so we're very much open to contributions. For more details, see our [contribution guidelines](CONTRIBUTING.md) and check the Issues Tracker for ways you can help.

## Getting Started

### Requirements

* [Leiningen](http://leiningen.org/)

### Starting a New Project

To start a new project based on Friboo, use the Leiningen template:

    $ lein new friboo com.example/friboo-is-awesome

This will generate a sample project containing some "foobar" logic that can serve as a starting point in your experiments.

A new directory with name `friboo-is-awesome` will be created in the current directory, containing the following files:

```
friboo-is-awesome
├── README.md
├── dev
│   └── user.clj
├── dev-config.edn
├── project.clj
├── resources
│   └── api
│       └── api.yaml
├── src
│   └── com
│       └── example
│           └── friboo_is_awesome
│               ├── api.clj
│               └── core.clj
└── test
    └── com
        └── example
            └── friboo_is_awesome
                ├── api_test.clj
                └── core_test.clj
```

* `README.md` contains some pregenerated development tips for the new project.
* `dev/user.clj` contains functions for [Reloaded Workflow](http://thinkrelevance.com/blog/2013/06/04/clojure-workflow-reloaded).
* `dev-config.edn` contains environment variables that will be used during reloaded workflow (instead of putting them into `profiles.clj`).
* `project.clj` contains the project definition with all dependencies and some additional plugins.
* `resources/api.yaml` contains the [Swagger API definition](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md) in .yaml format.
* `src` directory contains these files:
	* `core.clj` is the [system](https://github.com/stuartsierra/component#systems) definition.
	* `api.clj` contains API endpoint handlers.
* the `test` directory contains unit test examples using both `clojure.test` and [Midje](https://github.com/marick/Midje).

## How Friboo works

There are two core parts in any Friboo application:

- loading configuration by aggregating many sources
- starting the [system](https://github.com/stuartsierra/component#systems)

Both these parts are taken care of in `core.clj` in `run` function. The name "run" is not fixed, it can be anything.

Let's put configuration aside for now. A minimal `run` function might look like this:

```clojure
(require '[com.stuartsierra.component :as component]
         '[org.zalando.stups.friboo.system.http :as http]
         '[org.zalando.stups.friboo.system :as system])

(defn run []
  (let [system (component/map->SystemMap
                 {:http (http/make-http "api.yaml" {})})]
    (system/run {} system)))
```

Here we declare a system that has just one component created by `make-http` function. When started, this component will expose a RESTful API
where requests are routed according to the Swagger definition in `api.yaml`, which is taken from the classpath (usually `resources/api.yaml`).

Then we call `run` from `-main`:

```clojure
(defn -main [& args]
  (try
    (run)
    (catch Exception e
      (println "Could not start the system because of" (str e))
      (System/exit 1))))
```

`run` function does not block, it immediately returns the started system that can later be stopped (as reloaded workflow suggests). 

This already works, but it's not too flexible.

### Parsing configuration options

According to https://12factor.net/config, configuration should be provided via environment variables.
However, with REPL-driven [reloaded workflow](http://thinkrelevance.com/blog/2013/06/04/clojure-workflow-reloaded) you would have to restart the JVM
every time you need to change a configuration value. That's less than perfect.

Friboo supports several sources of configuration:

- environment variables: `HTTP_PORT=8081`
- JVM properties: `http.port=8081`
- development configuration from `dev-config.edn`: `{:http-port 8081}`
- default configurations per component (hardcoded)

Another challenge is — how to give components only the configuration they need? What if more than one component would like to use `PORT` variable?
Friboo solves this with namespacing of configuration parameters. Namespace in this case is just a known prefix: `HTTP_`, `API_`, `ENGINE_` etc.

Configuration is in loaded inside `run` by `load-config` function before defining the system:

```clojure
(defn run [args-config]
  (let [config (config/load-config
                 (merge default-http-config
                        args-config)
                 [:http])
        system (component/map->SystemMap
                 {:http (http/make-http "api.yaml" (:http config))})]
    (system/run config system)))
```

Here we make `run` accept a configuration map as an argument, it acts as an additional source of configuration (and it is used by Reloaded Workflow to inject reloadable configuration, see `dev/user.clj`).

`load-config` takes 2 arguments:

- map of default configuration that looks like this:

```clojure
{:http-port   8081
 :db-password "1q2w3e4r5t"
 :foo-bar     "foobar"
```

- list of namespaces, which are known prefixes configuration variables that we expect:

```clojure
[:http :db]
```

Configuration parameters' names are normalized in the following way (this is actually done by [environ](https://github.com/weavejester/environ)):

- `HTTP_PORT` becomes `:http-port`
- `http.port` becomes `:http-port`

`load-config` normalizes names in all configuration sources, merges them (real environment overrules the default config), filters the parameters by known prefixes and returns a nested map:

```clojure
{:http {:port 8081}
 :db   {:password "1q2w3e4r5t"}}
```

Note that `:foo-bar` parameter did not make it into the output, because it does not start with `:http-` nor `:db-`.

After we have this configuration loaded, it's very straightforward to give each component its part:

```clojure
{:http (http/make-http "api.yaml" (:http config))
 :db   (db/make-db (:db config))}
```

`system/run` also takes the entire configuration as the first argument and uses the `:system` part of it.

## Components

### HTTP Component

HTTP component starts a HTTP server and routes the requests based on the Swagger API definition. It lives in `org.zalando.stups.friboo.system.http` namespace.

It has an optional dependency `:controller` that is given to all
API handlers as first argument. The use case is to make it contain some configuration
and dependencies that the handlers should have access to.

```yaml
paths:
  '/hello/{name}':
    get:
      operationId: "com.example.myapp.api/get-hello"
      responses: {}
```

Part of system map (we make `:api` component to be a simple map, it's not necessary 
for every component to implement `com.stuartsierra.component/Lifecycle` protocol):

```clojure
:http      (component/using
             (http/make-http "api.yaml" (:http config))
             {:controller :api})
:api       {:configuration (:api config)}
```

`{:controller :api}` means that `:api` component will be available to `:http` under the name `:controller`, that's what it expects.

In `com.example.myapp.api` namespace:

```clojure
(defn get-hello [{:keys [configuration]} {:keys [name]} request]
  (response {:message (str "Hello " name)}))
```

`get-hello` (and every other API handler function) is called with 3 arguments:

- `:controller` (`:api` component in our example)
- merged parameters map from path, query and body parameters
- raw request map

Every handler function is expected to return a map representing a HTTP response:

```clojure
{:body    {:message "Hello Michael"}
 :headers {}
 :status  200
```

In our example we use `ring.util.response/response` to create a HTTP 200.

#### Configuration Options

* There are all the [configuration options](https://ring-clojure.github.io/ring/ring.adapter.jetty.html) that Jetty supports, for example:

```clojure
{:port        8081
 :cors-origin "*.zalando.de"}
```

### DB Component

DB component encapsulates JDBC connection pool and provides [Flyway](https://flywaydb.org/) to support schema migrations.

When the component starts, it will have additional `:datasource` key that contains an implementation of `javax.sql.DataSource`. You can use it as you like.

One of the examples is in friboo-ext-zalando:

    $ lein new friboo-ext-zalando db-example

Take a look at the following files:

```
example
├── resources
│   └── db
│       ├── migration
│       │   └── V1__initial_schema.sql
│       └── queries.sql
└── src
    └── db_example
        ├── api.clj
        ├── core.clj
        └── sql.clj
```

#### Configuration Options

For available options please refer to `org.zalando.stups.friboo.system.db/start-component`

### Metrics Component

The metrics component initializes a [Dropwizard MetricsRegistry](http://metrics.dropwizard.io) to measure
frequency and performance of the Swagger API endpoints; see [HTTP component](#http-component).

### Management HTTP component

This component starts another embedded Jetty at a different port (default 7979) and exposes endpoints used to monitor and manage the application:

* `/metrics`: A JSON document containing all metrics, gathered by the metrics component
* `/hystrix.stream`: The [Hystrix](https://github.com/Netflix/Hystrix) stream (can be aggregated by [Turbine](https://github.com/Netflix/Turbine))
* `/monitor/monitor.html`: The Hystrix dashboard

#### Configuration Options

All [Jetty configuration options](https://ring-clojure.github.io/ring/ring.adapter.jetty.html). 

## Real-World Usage

There are multiple examples of real-world usages of Friboo, including among Zalando's STUPS components:

* [Pier One Docker registry](https://github.com/zalando-stups/pierone) (REST service with DB and S3 backend)
* [Kio application registry](https://github.com/zalando-stups/kio) (REST service with DB)
* [Even SSH access granting service](https://github.com/zalando-stups/even) (REST service with DB)
* [Essentials](https://github.com/zalando-stups/essentials) (REST service with DB)

TODO HINT: set java.util.logging.manager= org.apache.logging.log4j.jul.LogManager to have proper JUL logging.

## License

Copyright © 2016 Zalando SE

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
