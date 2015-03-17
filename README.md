# friboo

A utility library to write microservices in Clojure. The library provides some components that can be used with the
[component lifecycle framework](https://github.com/stuartsierra/component).

## Usage

Most simple case:

```clojure
(ns examples
  (:require [org.zalando.friboo.system :as system])
  (:gen-class))

(defn -main [&args]
  (let [configuration (system/load-configuration)]
    (system/run configuration)))
```

### Configuration options

* `:http-definition` has to point to a Swagger 2.0 YAML file in the classpath (see
  [swagger1st](https://github.com/sarnowski/swagger1st))
* `:http-cors-origin` may be set to a domain mask for CORS access (e.g. `*.zalando.de`).
* All configurations that Jetty supports prefix with 'http-' like 'http-port'.

## Real-world usage

If you want to leverage the component framework correctly, the simple use case above won't help you as you don't get
any dependencies into your API functions.

TODO provide example application; demonstrate the API protocol

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
