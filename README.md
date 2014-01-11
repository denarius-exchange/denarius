# Denarius

Open-source financial exchange software.

## Status

- A matching engine that implements market and limit orders.
- An HTTP/JSON backend server for receiving oders

## ToDo (Immediately)

- Asset properties handling
- Customer/Broker Desks account handling
- Feedback to order senders (order execution, desks account balance...)

## Dependencies

- HTTP-Kit/Compojure/Ring
- JSON (data.json)
- Log (tools.logging)

## Usage

Currently there are no binaries to this software.

To try out Denarius, start a development server with:

```Bash
lein run
```

Then start the REPL:

```Bash
lein repl
```

And send orders to the server with:

```Clojure
(require '[clojure.data.json :as json])

(require '[org.httpkit.client :as http])

(defn callback [{:keys [status headers body error opts]}]
	(println body) )
       
(let [options {:timeout 200
               :basic-auth ["user" "pass"]
               :user-agent "User-Agent-string"
               :headers {"X-Header" "Value"}}
      port    8081
      opt-ask (json/write-str {:broker-id 1 :side :ask :size 1 :price 10})]
   @(http/post (str "http://localhost:" port "/order-new-limit")
               (assoc options :query-params {:order opt-ask})
               callback ) )
```

You can change order size by changing the ``:size`` parameter and order
side by changing the ``:side`` paramter to ``BID``.

The HTTP backend server now returns matching information upon order full execution.

##

Mailing list (important announcements): denarius@librelist.com
General announcements: http://machinomics.blogspot.com

## License

Copyright © 2013 Javier Arriero-Pais

Distributed under the MIT License.
