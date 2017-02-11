Monero sound
============

This is the code of a simple web app that plays sounds in relation to events in
the [monero](http://getmonero.org) network.

To see it in action go to [http://monerosound.info/](http://monerosound.info/).

It is written in Scala using the [Play
Framework](https://www.playframework.com/).

Usage
-----

### Configuration

Changes to the configuration can be made by adjusting the settings in
`conf/application.conf`.

To run the application you need to specify the nodes from which it can fetch the
information about events in the network (`xmr-nodes`).

Assuming that you are running a full monero node on your machine you can use the
default configuration that uses the RPC at `http://127.0.0.1:18081`.

You may specify more then one node that the application will use. If you specify
multpile nodes it may make sense increase the `no-connections` option which sets
the number of connections to the nodes from the list that are kept
simultaneously. 

The setting of `node-fetch-period` indicates how long each connection should
wait until making the next request to the node (tick). The number of requests
after which the node is abandoned and another random node from the list starts
to be used can be changed by setting `ticks-change`.

### Running

To build the application you will need
[sbt](https://www.scala-sbt.org/download.html). For more information about
running see Play Framework
[documentation](https://www.playframework.com/documentation/2.5.x/PlayConsole).
