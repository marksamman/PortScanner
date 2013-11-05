PortScanner
===========
PortScanner is an asynchronous port scanner written in Java that scans a specified port range in a specified IPv4 range.

CLI arguments
===========
1. first IPv4-address for the scan range
2. last IPv4-address for the scan range
3. first port
4. last port
5. output file

Example to scan port 0 to 1024 in the range 192.168.0.0 -> 192.168.0.255:
java PortScanner 192.168.0.0 192.168.0.255 0 1024 out.txt
