# friboo

![Maven Central](https://img.shields.io/maven-central/v/org.zalando.stups/friboo.svg)
[![Build Status](https://travis-ci.org/zalando-stups/friboo.svg?branch=master)](https://travis-ci.org/zalando-stups/friboo)
[![Coverage Status](https://coveralls.io/repos/zalando-stups/friboo/badge.svg)](https://coveralls.io/r/zalando-stups/friboo)

A utility library to write microservices in Clojure. The library provides some components that can be used with the
[component lifecycle framework](https://github.com/stuartsierra/component).

Friboo encourages the Swagger API-first approach where the REST API is defined as YAML.
See the [swagger1st library](https://github.com/sarnowski/swagger1st).

## Usage

Dependency:

    [org.zalando.stups/friboo <latest version>]

from Maven central.

Most simple case:

```clojure
(ns examples
  (:require [org.zalando.stups.friboo.system :as system]
            [com.stuartsierra.component :as component])
  (:gen-class))

(defn -main [&args]
  (let [configuration (system/load-configuration)
        system (component/new-system
          ; your system setup, see https://github.com/stuartsierra/component
          )]
    (system/run configuration)))
```

### Configuration options

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

* [Kio application registry](https://github.com/zalando-stups/kio) (REST service with DB)
* [Pier One Docker registry](https://github.com/zalando-stups/pierone) (REST service with DB and S3 backend)
* [Even SSH access granting service](https://github.com/zalando-stups/even) (REST service with DB)
* [Hello world example](https://github.com/hjacobs/friboo-hello-world) (very simple REST service without DB)

TODO HINT: set java.util.logging.manager= org.apache.logging.log4j.jul.LogManager to have proper JUL logging

## License

Copyright © 2015 Zalando SE

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
