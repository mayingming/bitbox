package unimelb.bitbox;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.EVENT;
import unimelb.bitbox.util.FileSystemManager.FileDescriptor;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
import java.net.*;

/**
 * This class(Thread) control the main server on the port in configuration.
 * This thread will create ServerThreads to deal with every connection
 * after receive socket and limit the total incoming number.
 * 
 * This class also implements FileSystemObserver and allocate events to all ServerThreads
 * 
 * @author Group: Coconut Opener
 */

public class ServerMain extends Thread implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected FileSystemManager fileSystemManager;
	private ServerSocket listeningSocket;
	public DatagramSocket udpSocket;
	
	// all connections from others peers(This do not include the initial servers!)
	public ServerThread servers[]; 
	public boolean addConnection(ServerThread x)
	{
		for(int i=0;i<servers.length;i++)
			if(servers[i]==null)
			{
				servers[i] = x;
				return true;
			}
		return false;
	}
	public boolean endConnection(ServerThread x)
	{
		for(int i=0;i<servers.length;i++)
			if(servers[i]==x)
			{
				servers[i] = null;
				return true;
			}
		return false;
	}
	
	private String deleteVoid(String str)
	{
		int num = 1;
		int length = str.length();
		while(str.charAt(length-num)==(char)0)
			num++;
		return str.substring(0,length-num+1);
	}
	private void tcp()
	{
		try {
			listeningSocket = new ServerSocket(Peer.port);
			log.info("Server listening on port "+Peer.port+" for a connection");
			while (true) {
				Socket socket = listeningSocket.accept();
				ServerThread thread = new ServerThread(socket,this);
				thread.start();
				log.info("Connection "+thread.remoteHost+":"+thread.remotePort+" accepted"+"(local:"+thread.localPort);
			}
		} catch (SocketException ex) {
			ex.printStackTrace();
		}catch (IOException e) {
			e.printStackTrace();
		} 
		finally {
			if(listeningSocket != null) {
				try {
					listeningSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	private void udp()
	{
		try {
			DatagramPacket receivePacket;
			String sentence;
			InetAddress IPAddress;
			int port;
			ServerThread thread;
			udpSocket = new DatagramSocket(Peer.port);
			while(true)
	        {
				byte[] receiveData = new byte[16384];
				receivePacket = new DatagramPacket(receiveData, receiveData.length);
				udpSocket.receive(receivePacket);
				sentence = new String(receivePacket.getData());
				sentence = deleteVoid(sentence);
				IPAddress = receivePacket.getAddress();
				port = receivePacket.getPort();
				log.info("RECEIVED: " + sentence + " (From " + IPAddress+ ":" + port);
				
				thread = Peer.isConnected(IPAddress, port);
				if(thread!=null)
					thread.udpMsg.add(sentence);
				else
				{
					thread = new ServerThread(this, IPAddress.getHostAddress(), port);
					thread.udpMsg.add(sentence);
					thread.start();
				}
			}
		} catch (SocketException e1) {
			e1.printStackTrace();
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public ServerMain(int max) throws NumberFormatException, IOException, NoSuchAlgorithmException 
	{
		fileSystemManager=new FileSystemManager(Configuration.getConfigurationValue("path"),this);
		servers = new ServerThread[max];
	}
	public void run() 
	{
		if(Peer.udpMode)
			udp();
		else
			tcp();
	}
	
	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent)
	{	
		for(ServerThread x:servers)
			if(x!=null)
				x.events.add(fileSystemEvent);
		for(ServerThread x:Peer.servers)
			if(x!=null)
				x.events.add(fileSystemEvent);
	}
}
