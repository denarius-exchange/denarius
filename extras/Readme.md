# Extras directory

This directory contains libraries that are needed for Denarius to achieve the best performance.
(pending third party licenses).


## ZeroMQ JNI libraries

ZeroMQ is the network multicast service library that Denarius uses for Engine-Connector nodes broadcast
messages, such as price communication.

Denarius ships with the pure Java JeroMQ, which does the exact same work. However, the JNI version jzmq
achieves far superior performance, and should be used in a production environment with moderate load
prospects.

Denarius uses cljzmq, a Clojure wrapper for the underlying Java interface/implementation of ZeroMQ. The
default configuration in the project.clj is for cljzmq to work with the pure Java implementation, which
will work out of the box.

When planning to move to zjmq, you need to know that it needs the native ZeroMQ libraries present on your system.
Please follow the guides in [ZeroMQ website](http://zeromq.org). As a summary, you need to perform the following:
1.   Install ZeroMQ libraries on your system (e.g., /usr/lib on Linux)
2.   Install zjmq libraries on your system (e.g., /usr/lib on Linux)
3.   In the ```project.clj``` file, remove comment the code ```:exclusions [org.zeromq/jzmq]```, which will
     force cljzmq to work with zjmq.

### Windows troubleshooting

For Windows, the website provides no binaries for zjmq. Hence you need to compile them. The
[guide to compile zjmq on Windows](http://zeromq.org/bindings%3ajava) aims to help in this task,
but I would suggest using only Visual Studio (Express 2013 was enough for me) and **deleting the 
```#include <config.hpp>``` line when you find that it does not exist. Visual C++ compiler will
go on anyway and produce you ```zjmq.dll```.

You should now copy the DLL to any directory where Windows looks for DLLs, say ```C:\windows\system```.
Remember that the ZeroMQ library must also be there, with a name like ```libzmq-v120-mt-4_0_4.dll```.
Remember also to perform step 3. above.