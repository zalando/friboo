# {{name}}

A project based on Friboo library.

## Development

Run  the application:

```
$ lein repl
user=> (reset)
```

For REPL-driven interactive development configuration variables can be provided in `dev-config.edn` file, which will be read on each system restart.

## Testing

```
$ lein test
```

## Building

```
$ lein uberjar
```

## Running

```
$ lein run
```

The following configuration environment variables are available:

| Variable | Meaning | Default | Example |
|---|---|---|---|
| API_EXAMPLE_PARAM | Example parameter with `:api-` prefix | `bar` | `foo` |

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
