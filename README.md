# Denarius

[![Build Status](https://secure.travis-ci.org/denarius-exchange/denarius.png?branch=master)](http://travis-ci.org/denarius-exchange/denarius)

Open-source financial exchange software.

## Status

- A matching engine that implements market and limit orders.
- An TCP/JSON backend server for receiving oders

## ToDo (Immediately)

- Asset properties handling
- Customer/Broker Desks account handling

## Dependencies

- Aleph/Lamina/Gloss
- JSON (data.json)
- Log (tools.logging)

## Usage

Currently there are no binaries to this software.

To try out Denarius, start the matching engine component with:
```Bash
lein run
```

Optionally, start one (or more) connector component with
```Bash
lein run -c connector
```

Then send orders to the connector with the utility client ([See Wiki](https://github.com/denarius-exchange/denarius/wiki/Taste-it:-Interactive-order-entry-command-line))

```Bash
lein run -m util.client/-main
```

In this scenario, the utility client plays the role of trading desk ([See Architecture](https://github.com/denarius-exchange/denarius/wiki/Architecture)).
You can send orders directly to the engine, since the protocol is the same.

If you want to make your own client API, you can follow the code in the
utility client.

The server now informs about (partial) order execution, on every execution
it makes, with the communications channel registered upon order entry. 


## Contact

Mailing list (important announcements): denarius@librelist.com

General announcements: http://machinomics.blogspot.com

## License

Copyright © 2013 Javier Arriero-Pais

Distributed under the MIT License.
