package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.logging.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.FileDescriptor;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

//import java.util.Random; //This is to simulate the bad network environment

/**
 * This class control a single connection to/from another peer. Almost every
 * specific functions are implemented in this class. An instance of this class
 * is created when a socket connect successfully. The timeout of handshake is
 * 20s. If the handshake are not done in 20s, the socket and thread close. Some
 * recv functions are empty as every message has been logged at the beginning.
 * 
 * The connection status area is important! Those boolean variable control the
 * performance of this class, such as should we send handshake request to
 * opposite or should we wait for it from opposite.
 * 
 * @author Group: Coconut Opener
 */

public class ServerThread extends Thread {
	// basic information area
	private static Logger log = Logger.getLogger(ServerThread.class.getName());
	private Socket socket;
	private ServerMain main;
	private BufferedReader in;
	private BufferedWriter out;
	private JSONParser parser;
	private FileSystemManager fsm;
	public int remotePort;
	public String remoteHost;
	public int localPort;
	public int blockSize;
	public int remoteServerPort;

	public ArrayList<FileSystemEvent> events;
	public boolean sendHandShake; // if true, send HandShake Request
	private ArrayList<JSONObject> peersInRefuse; // BFS if connection was refused

	// connection status area, to judge if the command is legal and control the next
	// performance
	private boolean stop; // if true, this thread will end after current send/recv function
	private boolean refused; // if true, try to connect next peer in peersInRefuse
	private boolean requestSent; // if true, we have sent a HANDSHAKE_REQUEST and are waiting for response
	private boolean connected; // if true, the handshake had been done. Otherwise we should refuse any other
								// command
	private boolean firstSync; // if true, the first sync has been called. Otherwise do it.

	public boolean isConnected() {
		return connected;
	}

	// For UDP only
	byte[] sendData = new byte[16384];
	InetAddress remoteIP;
	public ArrayList<String> udpMsg;
	private ArrayList<String> sendList;
	private String maybeResend;
	private boolean waitingResponse;
	private int wait;
	private int resend;
	// public Random test; ////This is to simulate the bad network environment

	private boolean isREQUEST(String str) {
		JSONObject jsonLoad;
		try {
			jsonLoad = (JSONObject) parser.parse(str);
			String command = (String) jsonLoad.get("command");
			if (command != null && command.contains("REQUEST"))
				return true;
			return false;
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	private void send_udp(String str) throws IOException {
		// The comments in this method is to simulate the bad network environment
		// test = new Random();
		// int a = test.nextInt(2);
		if (str.length() < 500)
			log.info(str + " send to " + remoteHost);
		else
			log.info("transfering big file......");
		sendData = str.getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, remoteIP, remotePort);
		// if(a == 0)
		main.udpSocket.send(sendPacket);
		// else
		// log.info("send "+ str +" fail");
		if (isREQUEST(str)) {
			waitingResponse = true;
			maybeResend = str;
			wait = 0;
		}
	}

	private void send_tcp(String str) throws IOException {
		if (str.length() < 500)
			log.info(str + " send to " + remoteHost);
		else
			log.info("transfering big file......");
		out.write(str + "\n");
		out.flush();
	}

	private void add_send_udp(String str) throws IOException {
		if (isREQUEST(str) && waitingResponse)
			sendList.add(str);
		else {
			if (isREQUEST(str))
				resend = 0;
			send_udp(str);
		}
	}

	private void send(String str) throws IOException {
		if (Peer.udpMode)
			add_send_udp(str);
		else
			send_tcp(str);
	}

	// pairs of protocols
	private void send_INVALID_PROTOCOL(String reason) throws IOException {
		send(Json.INVALID_PROTOCOL(reason));
		stop = true;
	}

	private void recv_INVALID_PROTOCOL(JSONObject json) throws IOException {
		stop = true;
	}

	private void send_CONNECTION_REFUSED() throws IOException {
		send(Json.CONNECTION_REFUSED(main.servers));
		stop = true;
	}

	private void recv_CONNECTION_REFUSED(JSONObject json) throws IOException {
		JSONArray peers = (JSONArray) json.get("peers");
		if (peers != null)
			for (int i = 0; i < peers.size(); i++)
				peersInRefuse.add((JSONObject) peers.get(i));
		refused = true;
	}

	private void send_HANDSHAKE_REQUEST() throws IOException {
		send(Json.HANDSHAKE_REQUEST(Peer.advertisedName, Peer.port));
		requestSent = true;
		sendHandShake = false;
	}

	private void recv_HANDSHAKE_REQUEST(JSONObject json) throws IOException {
		JSONObject hostPort = (JSONObject) json.get("hostPort");
		if (hostPort == null)
			send_INVALID_PROTOCOL("message must contain a hostPort field");
		else if (connected == true) {
			if (!Peer.udpMode)
				send_INVALID_PROTOCOL("The HandShake had been done before");
			else
				send_HANDSHAKE_RESPONSE();
		} else {
			String host = (String) hostPort.get("host");
			Number port = (Number) hostPort.get("port");
			remoteServerPort = port.intValue();
			if (host == null || port == null)
				send_INVALID_PROTOCOL("message must contain host and port in the hostPort field");
			else if (main.addConnection(this))
				send_HANDSHAKE_RESPONSE();
			else
				send_CONNECTION_REFUSED();
		}
	}

	private void send_HANDSHAKE_RESPONSE() throws IOException {
		send(Json.HANDSHAKE_RESPONSE(Peer.advertisedName, Peer.port));
		connected = true;
	}

	private void recv_HANDSHAKE_RESPONSE(JSONObject json) throws IOException {
		if (!requestSent)
			send_INVALID_PROTOCOL("Please don't send response to me as I never request");
		if (connected == true)
			send_INVALID_PROTOCOL("The HandShake had been done before");
		JSONObject hostPort = (JSONObject) json.get("hostPort");
		if (hostPort == null)
			send_INVALID_PROTOCOL("message must contain a hostPort field");
		else {
			String host = (String) hostPort.get("host");
			Number port = (Number) hostPort.get("port");
			if (host == null || port == null)
				send_INVALID_PROTOCOL("message must contain host and port in the hostPort field");
			else
				connected = true;
		}
	}

	private void send_DIRECTORY_CREATE_REQUEST(String pathName) throws IOException {
		send(Json.DIRECTORY_CREATE_REQUEST(pathName));
	}

	private void recv_DIRECTORY_CREATE_REQUEST(JSONObject json) throws IOException {
		String pathName = (String) json.get("pathName");
		if (!connected)
			send_INVALID_PROTOCOL("handshake has not been done");
		else if (pathName == null)
			send_INVALID_PROTOCOL("path name cannot be null");
		else if (!fsm.isSafePathName(pathName))
			send_DIRECTORY_CREATE_RESPONSE(pathName, "unsafe pathname given", false);
		else if (fsm.dirNameExists(pathName))
			send_DIRECTORY_CREATE_RESPONSE(pathName, "pathname already exists", false);
		else if (fsm.makeDirectory(pathName))
			send_DIRECTORY_CREATE_RESPONSE(pathName, "directory created", true);
		else
			send_DIRECTORY_CREATE_RESPONSE(pathName, "there was a problem creating the directory", false);

	}

	private void send_DIRECTORY_CREATE_RESPONSE(String pathName, String message, boolean status) throws IOException {
		send(Json.DIRECTORY_CREATE_RESPONSE(pathName, message, status));
	}

	private void recv_DIRECTORY_CREATE_RESPONSE(JSONObject json) throws IOException {
	}

	private void send_DIRECTORY_DELETE_REQUEST(String pathName) throws IOException {
		send(Json.DIRECTORY_DELETE_REQUEST(pathName));
	}

	private void recv_DIRECTORY_DELETE_REQUEST(JSONObject json) throws IOException {
		String pathName = (String) json.get("pathName");
		if (!connected)
			send_INVALID_PROTOCOL("handshake has not been done");
		else if (pathName == null)
			send_INVALID_PROTOCOL("path name cannot be null");
		else if (!fsm.isSafePathName(pathName))
			send_DIRECTORY_DELETE_RESPONSE(pathName, "unsafe pathname given", false);
		else if (!fsm.dirNameExists(pathName))
			send_DIRECTORY_DELETE_RESPONSE(pathName, "pathname does not exists", false);
		else if (fsm.deleteDirectory(pathName))
			send_DIRECTORY_DELETE_RESPONSE(pathName, "directory deleted", true);
		else
			send_DIRECTORY_DELETE_RESPONSE(pathName, "there was a problem deleting the directory", false);
	}

	private void send_DIRECTORY_DELETE_RESPONSE(String pathName, String message, boolean status) throws IOException {
		send(Json.DIRECTORY_DELETE_RESPONSE(pathName, message, status));
	}

	private void recv_DIRECTORY_DELETE_RESPONSE(JSONObject json) throws IOException {
	}

	private void send_FILE_CREATE_REQUEST(FileDescriptor fileDescriptor, String pathName) throws IOException {
		send(Json.FILE_CREATE_REQUEST(fileDescriptor, pathName));
	}

	private void recv_FILE_CREATE_REQUEST(JSONObject json) throws IOException, NoSuchAlgorithmException {
		if (!connected)
			send_INVALID_PROTOCOL("handshake has not been done");
		String pathName = (String) json.get("pathName");
		FileDescriptor fD = JsonToFileDescriptor(json);
		if (pathName == null)
			send_INVALID_PROTOCOL("path name cannot be null");
		else if ((fD.md5 == null) || (fD.lastModified == 0))
			send_FILE_CREATE_RESPONSE(fD, pathName, "inforamtion not enough to create file", false);
		else {
			pathName = SeparatorToSystem(pathName);
			if (!fsm.isSafePathName(pathName))
				send_FILE_CREATE_RESPONSE(fD, pathName, "unsafe pathName given", false);
			else if (pathName.contains(File.separator)
					&& !fsm.dirNameExists(pathName.substring(0, pathName.lastIndexOf(File.separator))))
				send_FILE_CREATE_RESPONSE(fD, pathName, "directory does not exist", false);
			else if (fsm.fileNameExists(pathName))
				send_FILE_CREATE_RESPONSE(fD, pathName, "filename already exists", false);
			else if (!fsm.createFileLoader(pathName, fD.md5, fD.fileSize, fD.lastModified))
				send_FILE_CREATE_RESPONSE(fD, pathName, "file loader fail", false);
			else {
				send_FILE_CREATE_RESPONSE(fD, pathName, "file loader ready", true);
				if (!fsm.checkShortcut(pathName)) {
					long length = fD.fileSize;
					if (length > blockSize)
						length = blockSize;
					send_FILE_BYTES_REQUEST(fD, pathName, 0, length);
				}
			}
		}
	}

	private void send_FILE_CREATE_RESPONSE(FileDescriptor fileDescriptor, String pathName, String message,
			boolean status) throws IOException {
		send(Json.FILE_CREATE_RESPONSE(fileDescriptor, pathName, message, status));
	}

	private void recv_FILE_CREATE_RESPONSE(JSONObject json) throws IOException {
		// seems nothing should be done
	}

	private void send_FILE_BYTES_REQUEST(FileDescriptor fileDescriptor, String pathName, long position, long length)
			throws IOException {
		send(Json.FILE_BYTES_REQUEST(fileDescriptor, pathName, position, length));
	}

	private void recv_FILE_BYTES_REQUEST(JSONObject json) throws IOException, NoSuchAlgorithmException {
		String pathName = (String) json.get("pathName");
		long position = (long) json.get("position");
		long length = (long) json.get("length");
		FileDescriptor fD = JsonToFileDescriptor(json);
		ByteBuffer byteBuffer = fsm.readFile(fD.md5, position, length);
		String content = Base64.getEncoder().encodeToString(byteBuffer.array());
		if (!connected)
			send_INVALID_PROTOCOL("handshake has not been done");
		else if (pathName == null)
			send_INVALID_PROTOCOL("path name cannot be null");
		else if ((fD.md5 == null) | (fD.lastModified == 0))
			send_FILE_CREATE_RESPONSE(fD, pathName, "inforamtion not enough to create file", false);
		else if (content == null)
			send_FILE_BYTES_RESPONSE(fD, pathName, content, "unsuccessful read", position, length, false);
		else
			send_FILE_BYTES_RESPONSE(fD, pathName, content, "successful read", position, length, true);
	}

	private void send_FILE_BYTES_RESPONSE(FileDescriptor fileDescriptor, String pathName, String content,
			String message, long position, long length, boolean status) throws IOException {
		send(Json.FILE_BYTES_RESPONSE(fileDescriptor, pathName, content, message, position, length, status));
	}

	private void recv_FILE_BYTES_RESPONSE(JSONObject json) throws IOException, NoSuchAlgorithmException {
		String pathName = (String) json.get("pathName");
		long position = (long) json.get("position");
		long length = (long) json.get("length");
		FileDescriptor fD = JsonToFileDescriptor(json);
		String content = (String) json.get("content");
		if (!connected)
			send_INVALID_PROTOCOL("handshake has not been done");
		else if (pathName == null)
			send_INVALID_PROTOCOL("path name cannot be null");
		else if ((fD.md5 == null) || (fD.lastModified == 0))
			send_INVALID_PROTOCOL("inforamtion not enough to create file");
		else {
			ByteBuffer byteBuffer = ByteBuffer.wrap(Base64.getDecoder().decode(content));
			fsm.writeFile(pathName, byteBuffer, position);
			fsm.checkWriteComplete(pathName);
			position = position + length;
			if (position != fD.fileSize) {
				length = fD.fileSize - position;
				if (length > blockSize)
					length = blockSize;
				send_FILE_BYTES_REQUEST(fD, pathName, position, length);
			}
		}
	}

	private void send_FILE_DELETE_REQUEST(FileDescriptor fileDescriptor, String pathName) throws IOException {
		send(Json.FILE_DELETE_REQUEST(fileDescriptor, pathName));
	}

	private void recv_FILE_DELETE_REQUEST(JSONObject json) throws IOException {
		if (!connected)
			send_INVALID_PROTOCOL("handshake has not been done");
		String pathName = (String) json.get("pathName");
		FileDescriptor fD = JsonToFileDescriptor(json);
		if (pathName == null)
			send_INVALID_PROTOCOL("path name cannot be null");
		else if ((fD.md5 == null) || (fD.lastModified == 0))
			send_FILE_DELETE_RESPONSE(fD, pathName, "inforamtion not enough to delete file", false);
		else {
			pathName = SeparatorToSystem(pathName);
			if (!fsm.isSafePathName(pathName))
				send_FILE_DELETE_RESPONSE(fD, pathName, "unsafe pathName given", false);
			else if (pathName.contains(File.separator)
					&& !fsm.dirNameExists(pathName.substring(0, pathName.lastIndexOf(File.separator))))
				send_FILE_DELETE_RESPONSE(fD, pathName, "directory does not exist", false);
			else if (!fsm.fileNameExists(pathName))
				send_FILE_DELETE_RESPONSE(fD, pathName, "filename not exists", false);
			else if (fsm.deleteFile(pathName, fD.lastModified, fD.md5))
				send_FILE_DELETE_RESPONSE(fD, pathName, "file deleted", true);
			else
				send_FILE_DELETE_RESPONSE(fD, pathName, "there was a problem deleting the file", false);
		}
	}

	private void send_FILE_DELETE_RESPONSE(FileDescriptor fileDescriptor, String pathName, String message,
			boolean status) throws IOException {
		send(Json.FILE_DELETE_RESPONSE(fileDescriptor, pathName, message, status));
	}

	private void recv_FILE_DELETE_RESPONSE(JSONObject json) throws IOException {
	}

	private void send_FILE_MODIFY_REQUEST(FileDescriptor fileDescriptor, String pathName) throws IOException {
		send(Json.FILE_MODIFY_REQUEST(fileDescriptor, pathName));
	}

	private void recv_FILE_MODIFY_REQUEST(JSONObject json) throws IOException, NoSuchAlgorithmException {
		if (!connected)
			send_INVALID_PROTOCOL("handshake has not been done");
		String pathName = (String) json.get("pathName");
		FileDescriptor fD = JsonToFileDescriptor(json);
		if (pathName == null)
			send_INVALID_PROTOCOL("path name cannot be null");
		else if ((fD.md5 == null) || (fD.lastModified == 0))
			send_FILE_MODIFY_RESPONSE(fD, pathName, "inforamtion not enough to MODIFY file", false);
		else {
			pathName = SeparatorToSystem(pathName);
			if (!fsm.isSafePathName(pathName))
				send_FILE_MODIFY_RESPONSE(fD, pathName, "unsafe pathName given", false);
			else if (pathName.contains(File.separator)
					&& !fsm.dirNameExists(pathName.substring(0, pathName.lastIndexOf(File.separator))))
				send_FILE_MODIFY_RESPONSE(fD, pathName, "directory does not exist", false);
			else if (!fsm.fileNameExists(pathName))
				send_FILE_MODIFY_RESPONSE(fD, pathName, "filename not exists", false);
			else if (fsm.fileNameExists(pathName, fD.md5))
				send_FILE_MODIFY_RESPONSE(fD, pathName, "file already exists with matching content", false);
			else if (!fsm.modifyFileLoader(pathName, fD.md5, fD.lastModified))
				send_FILE_MODIFY_RESPONSE(fD, pathName, "modify file loader fail", false);
			else {
				send_FILE_MODIFY_RESPONSE(fD, pathName, "file already to modify", true);
				long length = fD.fileSize;
				if (length > blockSize)
					length = blockSize;
				send_FILE_BYTES_REQUEST(fD, pathName, 0, length);
			}
		}
	}

	private void send_FILE_MODIFY_RESPONSE(FileDescriptor fileDescriptor, String pathName, String message,
			boolean status) throws IOException {
		send(Json.FILE_MODIFY_RESPONSE(fileDescriptor, pathName, message, status));
	}

	private void recv_FILE_MODIFY_RESPONSE(JSONObject json) throws IOException {
	}

	private FileDescriptor JsonToFileDescriptor(JSONObject json) {
		JSONObject filedescriptor = (JSONObject) json.get("fileDescriptor");
		String md5 = (String) filedescriptor.get("md5");
		long lastModified = (long) filedescriptor.get("lastModified");
		long fileSize = (long) filedescriptor.get("fileSize");
		FileDescriptor fD = fsm.new FileDescriptor(lastModified, md5, fileSize);
		return fD;
	}

	private String SeparatorToSystem(String str) {
		if (File.separator == "\\")
			return str.replace("/", File.separator);
		else
			return str.replace("/", File.separator);
	}

	private void clearEvents(ArrayList<FileSystemEvent> eventQueue) throws IOException {
		FileSystemEvent event;
		while (!eventQueue.isEmpty()) {
			event = eventQueue.remove(0);
			switch (event.event.toString()) {
			case "DIRECTORY_CREATE":
				send_DIRECTORY_CREATE_REQUEST(event.pathName);
				break;
			case "DIRECTORY_DELETE":
				send_DIRECTORY_DELETE_REQUEST(event.pathName);
				break;
			case "FILE_CREATE":
				send_FILE_CREATE_REQUEST(event.fileDescriptor, event.pathName);
				break;
			case "FILE_DELETE":
				send_FILE_DELETE_REQUEST(event.fileDescriptor, event.pathName);
				break;
			case "FILE_MODIFY":
				send_FILE_MODIFY_REQUEST(event.fileDescriptor, event.pathName);
				break;
			}
		}
	}

	private void recvJson(String msg) throws IOException, NoSuchAlgorithmException, ParseException {
		JSONObject jsonLoad = (JSONObject) parser.parse(msg);
		String command = (String) jsonLoad.get("command");
		if (command == null)
			send_INVALID_PROTOCOL("message must contain a command field as string");
		switch (command) {
		case "INVALID_PROTOCOL":
			recv_INVALID_PROTOCOL(jsonLoad);
			break;
		case "CONNECTION_REFUSED":
			recv_CONNECTION_REFUSED(jsonLoad);
			break;
		case "HANDSHAKE_REQUEST":
			recv_HANDSHAKE_REQUEST(jsonLoad);
			break;
		case "HANDSHAKE_RESPONSE":
			recv_HANDSHAKE_RESPONSE(jsonLoad);
			break;
		case "DIRECTORY_CREATE_REQUEST":
			recv_DIRECTORY_CREATE_REQUEST(jsonLoad);
			break;
		case "DIRECTORY_CREATE_RESPONSE":
			recv_DIRECTORY_CREATE_RESPONSE(jsonLoad);
			break;
		case "DIRECTORY_DELETE_REQUEST":
			recv_DIRECTORY_DELETE_REQUEST(jsonLoad);
			break;
		case "DIRECTORY_DELETE_RESPONSE":
			recv_DIRECTORY_DELETE_RESPONSE(jsonLoad);
			break;
		case "FILE_CREATE_REQUEST":
			recv_FILE_CREATE_REQUEST(jsonLoad);
			break;
		case "FILE_CREATE_RESPONSE":
			recv_FILE_CREATE_RESPONSE(jsonLoad);
			break;
		case "FILE_DELETE_REQUEST":
			recv_FILE_DELETE_REQUEST(jsonLoad);
			break;
		case "FILE_DELETE_RESPONSE":
			recv_FILE_DELETE_RESPONSE(jsonLoad);
			break;
		case "FILE_MODIFY_REQUEST":
			recv_FILE_MODIFY_REQUEST(jsonLoad);
			break;
		case "FILE_MODIFY_RESPONSE":
			recv_FILE_MODIFY_RESPONSE(jsonLoad);
			break;
		case "FILE_BYTES_REQUEST":
			recv_FILE_BYTES_REQUEST(jsonLoad);
			break;
		case "FILE_BYTES_RESPONSE":
			recv_FILE_BYTES_RESPONSE(jsonLoad);
			break;
		default:
			send_INVALID_PROTOCOL("unknown command");
			return;
		}
		if (Peer.udpMode) {
			if (sendList.isEmpty()) {
				waitingResponse = false;
				wait = 0;
			} else if (command.contains("RESPONSE")) {
				send_udp(sendList.remove(0));
				resend = 0;
			}
		}
	}

	// Constructor and run
	public ServerThread(Socket socket, ServerMain main) throws UnsupportedEncodingException, IOException {
		this.socket = socket;
		this.main = main;
		fsm = main.fileSystemManager;
		in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
		out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
		parser = new JSONParser();
		remotePort = socket.getPort();
		remoteHost = socket.getInetAddress().getHostName();
		localPort = socket.getLocalPort();
		blockSize = Peer.blockSize;
		remoteServerPort = remotePort;
		sendHandShake = false;
		events = new ArrayList<FileSystemEvent>();
		peersInRefuse = new ArrayList<JSONObject>();
		stop = false;
		requestSent = false;
		connected = false;
		firstSync = false;
	}

	public ServerThread(ServerMain main, String ip, int port) throws UnknownHostException {
		this.main = main;
		remoteHost = ip;
		remoteIP = InetAddress.getByName(ip);
		remotePort = port;
		remoteServerPort = port;
		localPort = Peer.port;
		fsm = main.fileSystemManager;
		parser = new JSONParser();
		blockSize = Peer.blockSize;
		sendHandShake = false;
		events = new ArrayList<FileSystemEvent>();
		peersInRefuse = new ArrayList<JSONObject>();
		stop = false;
		requestSent = false;
		connected = false;
		firstSync = false;
		udpMsg = new ArrayList<String>();
		sendList = new ArrayList<String>();
		waitingResponse = false;
		wait = 0;
		resend = 0;
	}

	private void udp_recv() throws NoSuchAlgorithmException, IOException, ParseException {
		while (!udpMsg.isEmpty())
			recvJson(udpMsg.remove(0));
	}

	private void tcp_recv() {
		String msg = null;
		try {
			while (in.ready()) {
				msg = in.readLine();
				if (msg.length() < 500)
					log.info(msg + "From " + remoteHost);
				else
					log.info("File transfering...");
				recvJson(msg);
			}
		} catch (NoSuchAlgorithmException | IOException | ParseException e) {
			e.printStackTrace();
		}
	}

	private void udp_reconnect() throws UnknownHostException {
		JSONObject peer = peersInRefuse.remove(0);
		String ip = (String) peer.get("host");
		int port = ((Number) peer.get("port")).intValue();
		log.info("reconnect to " + ip + ":" + port);
		sendHandShake = true;
		refused = false;
		requestSent = false;
		remoteHost = ip;
		remoteIP = InetAddress.getByName(ip);
		remotePort = port;
		remoteServerPort = port;
	}

	private void tcp_reconnect() throws UnsupportedEncodingException, IOException {
		JSONObject peer = peersInRefuse.remove(0);
		String host = (String) peer.get("host");
		Number port = (Number) peer.get("port");
		socket.close();
		log.info("reconnect to " + host + ":" + port);
		socket = new Socket(host, port.intValue());
		in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
		out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
		sendHandShake = true;
		refused = false;
		requestSent = false;
	}

	public void stopConnect() {
		stop = true;
	}

	public void run() {
		try {
			int time = 0;
			while (true) {
				if (stop || (!connected && time > 20 && !Peer.udpMode) || (!Peer.udpMode && socket.isClosed())) 
				{
					connected = false;
					main.endConnection(this);
					Peer.servers.remove(this);
					if (socket != null)
						socket.close();
					log.info("one thread closed");
					break;
				}

				if (refused) {
					if (peersInRefuse.isEmpty())
						stop = true;
					else {
						if (Peer.udpMode)
							udp_reconnect();
						else
							tcp_reconnect();
						time = 0;
					}
				}

				if (!connected && !requestSent && sendHandShake)
					send_HANDSHAKE_REQUEST();
				if (!events.isEmpty() && connected)
					clearEvents(events);
				if (Peer.udpMode)
					udp_recv();
				else
					tcp_recv();

				if (Peer.udpMode) {
					if (waitingResponse)
						wait++;
					if (wait > Peer.waitingTime * 2) {
						if (resend >= Peer.resendTime) {
							stop = true;
							continue;
						}
						send_udp(maybeResend);
						resend++;
					}
				}
				Thread.sleep(500);
				time++;
				if (connected && (time >= Peer.syncInterval * 2 || !firstSync)) {
					clearEvents(fsm.generateSyncEvents());
					firstSync = true;
					time = 0;
				}
			}
		} catch (SocketException e) {
			System.out.println("closed...");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}
}