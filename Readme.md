## Teflon ##

Teflon is a simple chat application implemented in Java. It uses IPV6 multicast over UDP to transmit, Swing for a user interface, and JSON serialization.

### Generating Binaries ###

    mvn install

#### Network Interfaces ####

This program uses heuristics to automatically select an appropriate network interface.

#### Multicast Groups ####

* https://www.iana.org/assignments/ipv6-multicast-addresses/ipv6-multicast-addresses.xhtml
* https://en.wikipedia.org/wiki/Link-local_address#IPv6

The reference address is in the "Link-Local Scope Multicast Addresses" range as it begins with FF02. This means it has a pretty good chance of being routed to all of the machines on the broadcast domain. I have tested delivery successfully on a consumer grade router with multiple machines of varying configurations, but of course YMMV.

### Project Page ###

https://github.com/maxdeliso/teflon