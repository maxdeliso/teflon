## Teflon ##

Teflon is a peer to peer desktop chat application built using Java Swing.

#### Network Interfaces ####

There is a dialog to select an appropriate pair of network interface and bind address.
Other instances of the program that are bound to the same address on the same LAN should be able to communicate directly.
#### Multicast Groups ####

There are two reference addresses, one IPv6 and one IPV4.
The candidate IPv6 address is a "Link-Local Scope Multicast Addresses" range as it begins with FF02.
The candidate IPv4 address is a class D address specifically in the designated multicast range (224.0.0.0 - 239.255.255.255).
If you would like to use a different multicast address that is supported also.

### Related ###

* https://www.iana.org/assignments/ipv6-multicast-addresses/ipv6-multicast-addresses.xhtml
* https://en.wikipedia.org/wiki/Link-local_address#IPv6
* https://www.rfc-editor.org/rfc/rfc3171
* https://en.wikipedia.org/wiki/Swing_(Java)