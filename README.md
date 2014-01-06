# Denarius

Open-source financial exchange software.

## Status

- A matching engine that implements market and limit orders.
- An HTTP/JSON backend server for receiving oders

## Dependencies

- HTTP-Kit/Compojure/Ring
- JSON (data.json)
- Log (tools.logging)

## Usage

Currently there are no binaries to this software.
Development servers can be set up with Leiningen with

```Bash
lein run
```

Assiming you have clojure.data.json required as json, and http-kit as http,
you can send ASK orders (choose size or leave 1) from the REPL with

```Clojure
(let [options    {:timeout 200
                     :basic-auth ["user" "pass"]
                     :user-agent "User-Agent-string"
                     :headers {"X-Header" "Value"}}
      port    8081
      opt-ask (json/write-str {:broker-id 1 :side :ask :size 1 :price 10})]
   @(http/post (str "http://localhost:" port "/order-new-limit")
               (assoc options :query-params {:order opt-ask}) ) )
```

Similarly, you can send BID orders by changing the :side parameter.

Notice that *you will get no response from the server*
(until the cross callbacks are implemented and connected with the HTTP/JSON
code). 

## License

Copyright © 2013 Javier Arriero-Pais

Distributed under the MIT License.
