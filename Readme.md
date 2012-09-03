## Teflon ##

Teflon is a simple peer to peer chat application implemented in Java.
It was written to demonstrate the ugliness of the Java programming language.
The eventual plan is to port this code to a series of other languages.


* * *

### Howto ###

If you want to build and run this code, but don't know how, then don't despair: it's really easy. 
Install [eclipse](http://eclipse.org/), and [egit](http://eclipse.org/egit/).
Then, run eclipse, and select File -> Import -> Git -> From URI, and then enter the public URI for this project: git://github.com/maxdeliso/teflon.git
That's it! 

Because the repository includes a .project file and some other things, it should automatically build (unless I've messed it up).

### Bugs ###

If you do a build and you find that there is an error in the code which needs to be fixed, are unwilling or unable to fix it yourself, but still care enough to take some action, submit it in the form of an issue to the following URL: https://github.com/maxdeliso/teflon/issues  
If you fix a bug that you found, please submit a merge request.

* * *

### Concurrency Model ###

Teflon has a pretty simple concurrency model, and essentially maintains three threads. 
If you open a debugger and check, there are actually a few more but they are managed by the runtime.
The main thread, which begins executing at the entry point, creates two threads and then waits for both of them to finish.
The first thread is called the local handler thread, which uses Swing to interact with the user.
The second thread is called the remote handler thread, which uses a UDP socket to interact with the network.
Both handler threads maintain their own message queue, and implement postMessage(...).
postMessage(...) just acquires a lock for its parent class's message queue, which is really a linked list, and the appends the passed message to it.
In the main loop of each handler thread, the execution flow looks something like this:

while alive:

1. is there any new data? 
    * if yes, pass it to the opposite handler with postMessage(...)
+  is there anything new in the message queue?
    * if yes, send it however you I know how
    
### Future Plans ###

I thought it might be nice to do some of these things eventually.
We'll see if any of them happen.
They are sorted by decreasing interestingness.

1. port it to Scala, because Scala doesn't suck (i.e. has a much more intuitive concurrency model and type system)
+  add support for some kind of encryption. I was thinking to do some kind of challenge based key agreement (SRP?) and then use a symmetric cipher (AES?)
+  maintain a list of peers, and make the client smart enough to intelligently propagate (address, name) tuples
+  add a public key to the (address, name) tuple and bootstrap with a pgp keyserver, leveraging existing PKI and creating (another) secure p2p chat network, as well an indexed DHT
+  manage and configure persistent user preferences
+  some other things I can't think of right now