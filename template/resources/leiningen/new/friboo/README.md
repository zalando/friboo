# {{name}}

A project based on Friboo library.

## Development

Add this line into your `/etc/hosts` file:

```
192.168.99.100  docker     # With docker-machine on OS X
127.0.0.1       docker     # When running docker natively on Linux 
```

You can use `db.sh` to start the database container:

```
$ ./db.sh drun
```

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
$ lein do uberjar, scm-source, docker build
```

## Running

```
$ docker run -it -p 8080:8080 {{name}}
```

The following configuration environment variables are available:

| Variable | Meaning | Default | Example |
|---|---|---|---|
| TOKENINFO_URL | Token info URL to validate access tokens against. | By default security is not enforced. | `https://auth.example.com/oauth2/tokeninfo` |
| DB_SUBNAME | PostgreSQL connection string | `//localhost:5432/pierone` | `//{{name}}.db.example.com:5432/{{name}}?ssl=true` |
| DB_USER | PostgreSQL username. | `postgres` | |
| DB_PASSWORD | PostgreSQL password. | `postgres` | |

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
