# friboo

![Maven Central](https://img.shields.io/maven-central/v/org.zalando.stups/friboo.svg)
[![Build Status](https://travis-ci.org/zalando-stups/friboo.svg?branch=master)](https://travis-ci.org/zalando-stups/friboo)
[![Coverage Status](https://coveralls.io/repos/zalando-stups/friboo/badge.svg?branch=master&service=github)](https://coveralls.io/github/zalando-stups/friboo?branch=master)

A utility library to write microservices in Clojure. The library provides some components that can be used with the
[component lifecycle framework](https://github.com/stuartsierra/component).

Friboo encourages the Swagger API-first approach where the REST API is defined as YAML.
See the [swagger1st library](https://github.com/sarnowski/swagger1st).

## Dependency

    [org.zalando.stups/friboo <latest version>]
    
## Why Friboo?

1. Friboo allows you to first define your API in a portable, language-agnostic format and implement it after (with help of [swagger1st](https://github.com/sarnowski/swagger1st)).
2. Friboo contains ready-made components, building blocks for your applications (HTTP server, DB access layer and more, see [Helpful components](#helpful-components)).
3. It's actually a lightweight framework, it contains the glue code for you, and there is already a recommended way of doing things.

## Usage

### Starting a new project

To start a new project based on the Friboo library, use the Leiningen template:

    $ lein new friboo <project>

This will generate an example project containing some "foobar" logic that can serve as a starting point in your experiments.

A new directory with `<project>` name will be created in the current directory, containing the following files:

```
friboo-is-awesome
├── Dockerfile
├── README.md
├── db.sh
├── dev
│   └── user.clj
├── dev-config.edn
├── project.clj
├── resources
│   ├── api
│   │   └── api.yaml
│   └── db
│       ├── migration
│       │   └── V1__initial_schema.sql
│       └── queries.sql
├── src
│   └── friboo_is_awesome
│       ├── api.clj
│       ├── core.clj
│       └── db.clj
└── test
    └── friboo_is_awesome
        ├── api_test.clj
        └── core_test.clj
```

* `Dockerfile` contains basic instructions for packaging the uberjar into a docker image.
* `README.md` contains some pregenerated development tips for the new project.
* `db.sh` contains handy scripts to run a PostgreSQL database in a docker container for development and integration testing.
* `dev/user.clj` contains functions for [Reloaded Workflow](http://thinkrelevance.com/blog/2013/06/04/clojure-workflow-reloaded).
* `dev-config.edn` contains environment variables that will be used during reloaded workflow. (Instead of putting them into `profiles.clj`)
* `project.clj` contains project definition with all the dependencies and some additional plugins.
* `resources/api.yaml` contains [Swagger API definition](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md) in .yaml format.
* `resources/db/migration/V1__initial_schema.sql` contains some DDL for the example (used by [Flyway](https://flywaydb.org/) library).
* `resources/db/queries.sql` contains example queries for the app (used by [Yesql](https://github.com/krisajenkins/yesql) library).
* `src` directory contains some components:
	* `core.clj` is the [system](https://github.com/stuartsierra/component#systems) definition.
	* `api.clj` contains API endpoint handlers.
	* `db.clj` contains generated functions for accessing the database.
* `test` directory contains unit test examples using both `clojure.test` and [Midje](https://github.com/marick/Midje).

### Configuration options

In `dev/user.clj`:

* `:system-log-level` can be used to set the root logger to something different than `INFO`.

## Helpful components

Friboo mainly provides some helpful utilities which are mostly presented as components.

### HTTP component

The HTTP component is generated on demand by the `def-http-component` macro. You can define which dependencies your
API functions require.

```clojure
(ns myapi
  (:require [org.zalando.stups.friboo.system.http :refer [def-http-component]))

(def-http-component MyAPI "my-api.yaml" [db scheduler])

(defn my-api-function [parameters request db scheduler]
  ; your implementation that can use 'db' as well as 'scheduler' dependencies
  )
```

The first argument of your function will always be a flattened map (parameter name -> parameter value) without `in`
categorisation. This does violate the swagger spec which calls parameter names only unique in combination with your
`in` type. You have to take care during modelling your API.

The HTTP component has some dependencies to other components (audit-log, metrics). It is recommended to use the
convenience function, to create an http system:

```clojure
(ns my-app.core
  (:require [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.friboo.system :as system])

(defn run
  [default-configuration]
  (let [configuration (config/load-configuration
                         (system/default-http-namespaces-and :db)
                         [my-app.sql/default-db-configuration
                          my-app.api/default-http-configuration
                          default-configuration])
        system (system/http-system-map configuration
                  my-app.api/map->API [:db]
                  :db (my-app.sql/map->DB {:configuration (:db configuration)}))]

    (system/run configuration system)))
```

#### Configuration options

The component has to be initialized with its configuration in the `:configuration` key.

    (map->MyAPI {:configuration {:port        8080
                                 :cors-origin "*.zalando.de"}})

* `:cors-origin` may be set to a domain mask for CORS access (e.g. `*.zalando.de`).
* All configurations that Jetty supports.

### DB component

The DB component is generated on demand by the `def-db-component` macro. The DB component is itself a `db-spec`
compliant data structure, backed by a connection pool.

```clojure
(ns mydb
  (:require [org.zalando.stups.friboo.system.db :refer [def-db-component]))

(def-db-component MyDB)
```

#### Configuration options

The component has to be initialized with its configuration in the `:configuration` key.

    (map->MyDB {:configuration {:subprotocol "postgresql"
                                :subname     "localhost/mydb"}})

TODO link to jdbc documentation, pool specific configuration like min- and max-pool-size

### Audit log component

The aim of the audit log component is to collect logs of all modifying http requests (POST, PUT, PATCH, DELETE) and 
store them in an S3 bucket. These information can then be used to create an audit trail.

#### Configuration options

* Set `:audit-log-bucket` to enable this component
    * the value should address the name of an S3 bucket
    * the application must have write access to this bucket
    * the frequency, with which the log files are written, can be adjusted with `:audit-log-flush-millis` (defaults to 10s)
        * of course, no empty files will be written
    * to build a meaningful file name pattern, the environment variables `APPLICATION_ID`, `APPLICATION_VERSION`
      and `INSTANCE_ID` are used
        * `INSTANCE_ID` defaults to random UUID

### Metrics component

The metrics component initializes a [Dropwizard MetricsRegistry](http://metrics.dropwizard.io) to measure
frequency and performance of the Swagger API endpoints (see HTTP component)

### Management HTTP component

This component starts another embedded Jetty at a different port (default 7979) and exposes endpoints used to monitor and manage the
application:

* `/metrics` - A JSON document containing all metrics, gathered by the metrics component
* `/hystrix.stream` - The Hystrix stream (can be aggregated by Turbine)
* `/monitor/monitor.html` - The Hystrix dashboard

#### Configuration options

All Jetty configuration options, prefixed with `:mgmt-http-` or `MGMT_HTTP_`. 

## Real-world usage

There are multiple examples of real-world usages among the STUPS components:

* [Pier One Docker registry](https://github.com/zalando-stups/pierone) (REST service with DB and S3 backend)
* [Kio application registry](https://github.com/zalando-stups/kio) (REST service with DB)
* [Even SSH access granting service](https://github.com/zalando-stups/even) (REST service with DB)
* [Essentials](https://github.com/zalando-stups/essentials) (REST service with DB)
* [Hello world example](https://github.com/dryewo/friboo-hello-world-full) (project that served as base for the Leiningen template)

TODO HINT: set java.util.logging.manager= org.apache.logging.log4j.jul.LogManager to have proper JUL logging

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
