## Teflon ##

Teflon is a simple, configurable peer to peer chat application implemented in Java. It uses IPV6 multicast over UDP to transmit, Swing for a user interface, and JSON serialization.

### Building ###

    mvn compile

### Generating Binaries ###

    mvn install

### Configuring ###

You need a configuration in the working directory of the program, of name "teflon.json".

Here is the documentation on each of the fields:

* udpPort - the port to send and receive on
* inputBufferLength - the largest message buffer you want to receive
* backlogLength - how many messages to store in memory before dropping
* multicastGroup - the IPv6 multicast group address to join
* interfaceName - the network interface name to join a multicast group with

#### Network Interfaces ####

If you run the program with a command line argument -L, it will list the names of all available interfaces.

I may in the future attempt to use some heuristics to automatically select an appropriate network interface, or at least make the selection more user friendly, but this is not yet done.

#### Multicast Groups ####

* https://www.iana.org/assignments/ipv6-multicast-addresses/ipv6-multicast-addresses.xhtml
* https://en.wikipedia.org/wiki/Link-local_address#IPv6

The reference address is in the "Link-Local Scope Multicast Addresses" range as it begins with FF02. This means it has a pretty good chance of being routed to all of the machines on the broadcast domain. I have tested delivery successfully on a consumer grade router with multiple machines of varying configurations, but of course YMMV.

### Project Page ###

https://github.com/maxdeliso/teflon