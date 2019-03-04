## Teflon ##

Teflon is a simple, configurable peer to peer chat application implemented in Java. It uses IPV6 multicast over UDP to transmit, Swing for a user interface, and JSON serialization.

### Modules ###

There are two Maven modules, one implementing the core network event loop, and the other providing a simple Swing wrapper around the former, which is packaged as a runnable Jar.

### Generating Binaries ###

    mvn install

_note: the resulting runnable Jar will appear at the following path: swing\target\swing-{version}-....jar_

### Configuring ###

You need a configuration file in the working directory of the program, of name "teflon.json".

Here is the documentation on each of the fields:

* udpPort - the port to send and receive on
* bufferLength - the largest message buffer you want to receive
* backlogLength - how many messages to store in memory before dropping
* hostAddress - a host address, probably the IPv6 multicast group address to join
* interfaceName - the network interface name to join a multicast group with

_note: see the provided teflon.json reference in the swing module._

#### Network Interfaces ####

If you run the program with a command line argument -L, it will list the names of all available interfaces.

I may in the future attempt to use some heuristics to automatically select an appropriate network interface, or at least make the selection more user friendly, but this is not yet done.

#### Multicast Groups ####

* https://www.iana.org/assignments/ipv6-multicast-addresses/ipv6-multicast-addresses.xhtml
* https://en.wikipedia.org/wiki/Link-local_address#IPv6

The reference address is in the "Link-Local Scope Multicast Addresses" range as it begins with FF02. This means it has a pretty good chance of being routed to all of the machines on the broadcast domain. I have tested delivery successfully on a consumer grade router with multiple machines of varying configurations, but of course YMMV.

### Project Page ###

https://github.com/maxdeliso/teflon