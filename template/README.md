# friboo-template

A Leiningen template for Friboo library (https://github.com/zalando-stups/friboo).

This document only describes development guidelines. For usage please refer the the main README of Friboo.

## Development

In order to try the template out without releasing to clojars, install it to the local `~/.m2` and specify `--snapshot` flag:

```
$ lein install
$ cd ../..
$ lein new friboo <project> --snapshot
```

## Testing

So far this is a manual step. Docker is required.

```
$ ./itest.sh
```

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
