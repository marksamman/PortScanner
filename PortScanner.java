/*
 * Copyright (c) 2013 Mark Samman <https://github.com/marksamman/PortScanner>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import java.io.FileWriter;

import java.net.InetSocketAddress;

import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

class Destination
{
	private long ip;
	private int port;

	public Destination(long ip, int port)
	{
		this.ip = ip;
		this.port = port;
	}

	public long getIP() { return ip; }
	public int getPort() { return port; }
}

class SchedulePortScanTask implements Callable<Void>
{
	private String ipString;
	private Destination destination;

	public SchedulePortScanTask(String ipString, Destination destination)
	{
		this.ipString = ipString;
		this.destination = destination;
	}

	public Void call() throws Exception
	{
		AsynchronousSocketChannel.open(PortScanner.channelGroup).connect(new InetSocketAddress(ipString, destination.getPort()), destination, PortScanner.connectionHandler);
		return null;
	}
}

class PortScanConnectionHandler implements CompletionHandler<Void, Destination>
{
	public void completed(Void result, Destination destination)
	{
		PortScanner.lock.lock();
		TreeSet<Integer> set = PortScanner.openPortsMap.get(destination.getIP());
		if (set == null) {
			set = new TreeSet<Integer>();
			PortScanner.openPortsMap.put(destination.getIP(), set);
		}
		set.add(destination.getPort());
		PortScanner.lock.unlock();

		PortScanner.scanned.incrementAndGet();
	}

	public void failed(Throwable exc, Destination destination)
	{
		PortScanner.scanned.incrementAndGet();
	}
}

class PortScanner
{
	public static TreeMap<Long, TreeSet<Integer>> openPortsMap = new TreeMap<Long, TreeSet<Integer>>();
	public static AtomicLong scanned = new AtomicLong(0);
	public static ReentrantLock lock = new ReentrantLock();
	public static PortScanConnectionHandler connectionHandler = new PortScanConnectionHandler();
	public static AsynchronousChannelGroup channelGroup;

	public static int str2int(String str)
	{
		int n;
		try {
			n = Integer.parseInt(str);
		} catch (Exception e) {
			n = 0;
		}
		return n;
	}

	public static long stringIpToLong(String ip)
	{
		String[] splitted = ip.split("\\.");
		return (str2int(splitted[0]) << 24) | (str2int(splitted[1]) << 16) | (str2int(splitted[2]) << 8) | str2int(splitted[3]);
	}

	public static String longIpToString(long ip)
	{
		return (ip >> 24) + "." + ((ip >> 16) & 255) + "." + ((ip >> 8) & 255) + "." + (ip & 255);
	}

	public static void main(String[] args) throws Exception
	{
		long ip_start = stringIpToLong(args[0]);
		long ip_stop = stringIpToLong(args[1]);
		int port_start = Math.max(0, Math.min(65535, str2int(args[2])));
		int port_stop = Math.max(0, Math.min(65535, str2int(args[3])));
		String result_file = args[4];

		ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());		
		channelGroup = AsynchronousChannelGroup.withThreadPool(pool);

		long count = ((ip_stop - ip_start) + 1) * ((port_stop - port_start) + 1);
		while (ip_start <= ip_stop) {
			String ip_string = longIpToString(ip_start);
			int port = port_start;
			while (port <= port_stop) {
				pool.submit(new SchedulePortScanTask(ip_string, new Destination(ip_start, port++)));
			}
			ip_start++;
		}

		while (scanned.get() != count) {
			System.out.println(scanned.get() + "/" + count);
			Thread.sleep(250);
		}
		channelGroup.shutdownNow();
		pool.shutdown();

		FileWriter writer = new FileWriter(result_file);
		for (Long host : openPortsMap.keySet()) {
			String ip_str = longIpToString(host);
			for (Integer port : openPortsMap.get(host)) {
				writer.write(ip_str + ":" + port + System.getProperty("line.separator"));
			}
		}
		writer.close();
	}
}
