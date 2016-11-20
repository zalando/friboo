## 2.0.0 (date TBD)

Breaking changes:

- `org.zalando.stups.friboo.system.http/def-http-component` is removed. One should use
`org.zalando.stups.friboo.system.http/make-http` or
`org.zalando.stups.friboo.zalando-specific.system.http/make-zalando-http`. Components have to be provided explicitly now.
For examples see `test/org/zalando/stups/friboo/zalando_specific/system_test.clj`.
- Notion of controller is introduced to decouple Http serving and API handling. Controller is a component that contains
all the dependencies that API handlers might need access to - such as database, metrics, authorizer etc. API handlers
by default are called with `[controller params request]` now, this can be customized through
`{:s1st-options {:executor <your_resolver_fn>}}`. `system_test.clj` contains an example.
- `HTTP_TOKENINFO_URL` environment variable is ignored now, `TOKENINFO_URL` and `GLOBAL_TOKENINFO_URL` are used
to enable OAuth 2.0 access token checking in `Http` component created by `make-zalando-http`.
- `org.zalando.stups.friboo.auth` namespace is renamed to `org.zalando.stups.friboo.zalando-specific.auth`,
`Authorizer` component is introduced, `fetch-auth` and `get-auth` functions changed signatures.
- `org.zalando.stups.friboo.system.db/def-db-component` is removed. One should use
`org.zalando.stups.friboo.system.db/map->DB` to create DB component, `:auto-migration?` is provided as part of normal
config.
- `org.zalando.stups.friboo.config/load-configuration` is broken down into `org.zalando.stups.friboo.config/load-config`
 and `org.zalando.stups.friboo.zalando-specific.config/load-config`, signature changed.
