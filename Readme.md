## Teflon ##

Teflon is a desktop chat application.

#### Network Interfaces ####

This program uses heuristics to automatically select an appropriate network interface.
If there are multiple viable interfaces detected, a warning will be printed to the log.

#### Multicast Groups ####

There are two reference addresses, one IPv6 and one IPV4.
The candidate IPv6 address is a "Link-Local Scope Multicast Addresses" range as it begins with FF02.
The candidate IPv4 address is a class D address specifically in the designated multicast range (224.0.0.0 - 239.255.255.255).
This program will attempt to use IPv6 and fallback to IPv4 when establishing a multicast membership.

### Related ###

* https://www.iana.org/assignments/ipv6-multicast-addresses/ipv6-multicast-addresses.xhtml
* https://en.wikipedia.org/wiki/Link-local_address#IPv6
* https://www.rfc-editor.org/rfc/rfc3171