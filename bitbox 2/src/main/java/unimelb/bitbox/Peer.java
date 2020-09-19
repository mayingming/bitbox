package unimelb.bitbox;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.Logger;

import org.json.simple.JSONArray;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.HostPort;

/**
 * This class include main(), some static configuration valuable read form
 * configuration.properties, and a list for all initial connection .
 * 
 * @author Group: Coconut Opener
 */
public class Peer {
	private static Logger log = Logger.getLogger(Peer.class.getName());
	private static ServerMain mainServer;
	private static SecureServer secure;
	public static String path;
	public static int port;
	public static String advertisedName;
	public static String[] peers;
	public static int maximumIncommingConnections;
	public static int blockSize;
	public static int syncInterval;
	public static ArrayList<ServerThread> servers; // all initial connection
	public static boolean udpMode;
	public static int clientPort;
	public static int waitingTime;
	public static int resendTime;

	public static ServerThread isConnected(InetAddress host, int port) throws UnknownHostException {
		ServerThread result = null;
		for (ServerThread th : mainServer.servers) {
			if (th != null && host.equals(InetAddress.getByName(th.remoteHost)) && port == th.remotePort) {
				result = th;
				break;
			}
		}
		for (ServerThread th : servers) {
			if (host.equals(InetAddress.getByName(th.remoteHost)) && port == th.remotePort) {
				result = th;
				break;
			}
		}
		return result;
	}

	private static boolean connect_udp(String hostName, int port) throws UnknownHostException, InterruptedException {
		log.info("connect to " + hostName + ":" + port + ")");
		InetAddress host = InetAddress.getByName(hostName);
		ServerThread thread = isConnected(host, port);
		if (thread == null) {
			thread = new ServerThread(mainServer, hostName, port);
			servers.add(thread);
			thread.sendHandShake = true;
			thread.start();
			int time = 0;
			while (time < Peer.resendTime * Peer.waitingTime * 10 + 50) {
				Thread.sleep(100);
				if (thread.isConnected())
					return true;
				time++;
			}
		} else
			log.info(hostName + " had been connected before");
		return false;
	}

	private static boolean connect_tcp(String hostName, int port) throws UnknownHostException, InterruptedException {
		log.info("connect to " + hostName + ":" + port + ")");
		InetAddress host = InetAddress.getByName(hostName);
		Socket s = null;
		try {
			ServerThread thread = isConnected(host, port);
			if (thread == null) {
				s = new Socket(hostName, port);
				if (s != null) {
					thread = new ServerThread(s, mainServer);
					servers.add(thread);
					thread.sendHandShake = true;
					thread.start();
					int time = 0;
					while (time < 100) {
						Thread.sleep(100);
						if (thread.isConnected())
							return true;
						time++;
					}
				}
			} else
				log.info(hostName + " had been connected before");
		} catch (IOException e) {
			s = null;
			log.info("connect fail (to " + hostName + ":" + port + ")");
			return false;
		}
		return false;
	}

	public static boolean connect(String hostName, int port) throws InterruptedException {
		try {
			InetAddress host = InetAddress.getByName(hostName);
			if (udpMode)
				return connect_udp(hostName, port);
			else
				return connect_tcp(hostName, port);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return false;
		}
	}

	public static boolean disconnect(String hostName, int port) {
		ServerThread thread = null;
		InetAddress host;
		try {
			host = InetAddress.getByName(hostName);
			thread = isConnected(host, port);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return false;
		}
		if (thread == null || thread.isConnected() == false)
			return false;
		thread.stopConnect();
		return true;
	}

	public static JSONArray connectionList() {
		return Json.ConnectionList(mainServer.servers);
	}

	private static void setConfiguration() {
		Configuration.getConfiguration();
		path = Configuration.getConfigurationValue("path");
		port = Integer.parseInt(Configuration.getConfigurationValue("port"));
		clientPort = Integer.parseInt(Configuration.getConfigurationValue("clientPort"));
		advertisedName = Configuration.getConfigurationValue("advertisedName");
		String mode = Configuration.getConfigurationValue("mode");
		udpMode = mode.equals("udp");
		log.info("Mode: " + mode);
		String peerSet = Configuration.getConfigurationValue("peers");
		if (peerSet.length() > 1)
			peers = peerSet.split("\\s*,\\s*");
		else
			peers = null;
		maximumIncommingConnections = Integer
				.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
		blockSize = Integer.parseInt(Configuration.getConfigurationValue("blockSize"));
		if (udpMode && blockSize > 8192)
			blockSize = 8192;
		syncInterval = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
		waitingTime = Integer.parseInt(Configuration.getConfigurationValue("waitingTime"));
		resendTime = Integer.parseInt(Configuration.getConfigurationValue("resendTime"));

	}

	public static void main(String[] args)
			throws IOException, NumberFormatException, NoSuchAlgorithmException, InterruptedException {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tc] %2$s %4$s: %5$s%n");
		log.info("BitBox Peer starting...");
		setConfiguration();
		servers = new ArrayList<ServerThread>();

		mainServer = new ServerMain(maximumIncommingConnections);
		mainServer.start();

		secure = new SecureServer();
		secure.start();

		if (peers != null)
			for (int i = 0; i < peers.length; i++) {
				HostPort hp = new HostPort(peers[i]);
				connect(hp.host, hp.port);
			}

		Scanner scanner = new Scanner(System.in);
		String input = null;
		while (!(input = scanner.nextLine()).equals("exit"))
			log.info(input);
	}
}
