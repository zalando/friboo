(ns {{namespace}}.api-test
  (:require
    [clojure.test :refer :all]
    [{{namespace}}.db :as db]
    [{{namespace}}.api :refer :all]
    [midje.sweet :refer :all]))

(deftest can-get-hello
  (is (= (get-hello {:name "Friboo"} nil nil)
         {:status  200
          :headers {"Content-Type" "application/json"}
          :body    {:message "Hello Friboo"}})))

(deftest can-delete-greeting-template
  (let [number-of-calls (atom 0)]
    (with-redefs [db/cmd-delete-greeting!
                  (fn [data conn]
                    (swap! number-of-calls inc)
                    (is (= data {:id "foo"}))
                    (is (= conn {:connection "db-conn"})))]
      (is (= (select-keys (delete-greeting-template {:greeting_id "foo"} nil "db-conn")
                          [:status])
             {:status 204}))
      (is (= @number-of-calls 1)))))

(deftest wrap-midje-facts

  (facts "about delete-greeting-template"
         (fact "works"
               (delete-greeting-template {:greeting_id ..greeting-id..} nil ..db..) => (contains {:status 204})
               (provided
                 (db/cmd-delete-greeting! {:id ..greeting-id..} {:connection ..db..}) => nil :times 1)))

  )
