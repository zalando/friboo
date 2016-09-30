(ns org.zalando.stups.friboo.security-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.friboo.security :refer :all]
            [clj-http.client :as client]))

(deftest wrap-midje-facts

  (facts "about check-corresponding-attributes"
    (check-corresponding-attributes {"application.write" true} ["application.write"]) => truthy
    (check-corresponding-attributes {"application.write" true} ["application.write_all"]) => falsey
    (check-corresponding-attributes {"application.write" nil} ["application.write"]) => falsey)

  (facts "about checking oauth2 tokens"

    (fact "if no tokenninfo-url specified, do not check tokens"
      ((make-oauth2-security-handler nil) ..request.. anything anything) => ..request..)

    (fact "when tokeninfo returns 200, adds its response to the request"
      (with-redefs [resolve-access-token resolve-access-token-real]
        ((make-oauth2-security-handler ..tokeninfo-url..) {:headers {"authorization" "Bearer foo"}}
          nil
          ["application.write"]))
      => {:headers {"authorization" "Bearer foo"} :tokeninfo {"application.write" true}}
      (provided
        (client/get ..tokeninfo-url.. anything)
        => {:status 200 :body {"application.write" true}}))

    (fact "when there are insufficient scopes, return 403"
      (with-redefs [resolve-access-token resolve-access-token-real]
        ((make-oauth2-security-handler ..tokeninfo-url..) {:headers {"authorization" "Bearer foo"}}
          nil
          ["application.write"]))
      => (contains {:status 403})
      (provided
        (client/get ..tokeninfo-url.. anything)
        => {:status 200 :body {}}))

    (fact "when tokeninfo fails, return 401"
      (with-redefs [resolve-access-token resolve-access-token-real]
        ((make-oauth2-security-handler ..tokeninfo-url..) {:headers {"authorization" "Bearer foo"}}
          nil
          []))
      => (contains {:status 401})
      (provided
        (client/get ..tokeninfo-url.. anything)
        => {:status 400 :body {}}))

    (fact "when there is no token in the request, return 401"
      ((make-oauth2-security-handler ..tokeninfo-url..) {:headers {}} nil [])
      => (contains {:status 401})))

  )
