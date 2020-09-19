package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Scanner;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import unimelb.bitbox.util.HostPort;

public class Client {
	public static Socket socket;
	public static BufferedReader in;
	public static BufferedWriter out;
	public static String remoteHost;
	public static int remotePort;
	public static SecretKey aesKey;
	public static PrivateKey rsaPri;
	public static String PrivateKey_File = "bitboxclient_rsa";
	public static JSONParser parser;

	public static String command;
	public static String hostName;
	public static int port;
	public static String authName;

	private static boolean connect(String hostName, int port) {
		try {
			socket = new Socket(hostName, port);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
			out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
			remotePort = socket.getPort();
			remoteHost = socket.getInetAddress().getHostName();
			System.out.println("Connected to " + remoteHost + ":" + remotePort);
			return true;
		} catch (Exception e) {
			System.out.println("connect fail");
			return false;
		}
	}

	private static boolean exchangeAES() {
		try {
			send(Json.CLIENT_CHALLENGE_REQUEST(authName));
			String msg = in.readLine();
			System.out.println(msg + " from " + remoteHost);
			JSONObject json = (JSONObject) parser.parse(msg);
			Boolean status = (Boolean) json.get("status");
			String encodedAes = (String) json.get("AES128");
			if (status) {
				byte[] content = Base64.getDecoder().decode(encodedAes);
				byte[] key = RSAUtil.privateDecrypt(content, rsaPri);
				aesKey = new SecretKeySpec(key, "AES");
				return true;
			} else
				return false;
		} catch (Exception e) {
			System.out.println("exchange AES fail");
			return false;
		}
	}

	private static void send(String str) throws IOException {
		System.out.println(str + " send to " + remoteHost);
		out.write(str + "\n");
		out.flush();
	}

	private static void sendEncrypted(String message) {
		try {
			System.out.println(message + " will be encrypted to " + remoteHost);
			
			//padding
			int remaining = message.length()%128;
			if(remaining>0)
			{
				message = message + "\n";
				remaining--;
			}
			for(int i=0;i<remaining;i++)
				message = message + (char)(int)(Math.random()*26+97);
			
			//encrypt
			Cipher cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.ENCRYPT_MODE, aesKey);
			byte[] encrypted = cipher.doFinal(message.getBytes("UTF-8"));
			String payload = Base64.getEncoder().encodeToString(encrypted);
			send(Json.PAYLOAD(payload));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void send_LISTS_PEERS_REQUEST() throws IOException {
		sendEncrypted(Json.LIST_PEERS_REQUEST());
	}

	private static void send_CONNECT_PEER_REQUEST(String host, int port) throws IOException {
		sendEncrypted(Json.CONNECT_PEER_REQUEST(host, port));
	}

	private static void send_DISCONNECT_PEER_REQUEST(String host, int port) throws IOException {
		sendEncrypted(Json.DISCONNECT_PEER_REQUEST(host, port));
	}

	public static void main(String[] args) throws InterruptedException {
		CmdLineArgs argsBean = new CmdLineArgs();
		CmdLineParser argsParser = new CmdLineParser(argsBean);
		try {
			argsParser.parseArgument(args);
			command = argsBean.getCommand();
			authName = argsBean.getId();
			HostPort hp = new HostPort(argsBean.getServer());
			hostName = hp.host;
			port = hp.port;
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			argsParser.printUsage(System.err);
		}
		
		try {
			rsaPri = RSAUtil.GetPriKey(new FileInputStream(PrivateKey_File));
			parser = new JSONParser();
			if (connect(hostName, port) && exchangeAES()) {
				if(command.toLowerCase().equals("list_peers"))
					send_LISTS_PEERS_REQUEST();
				else
				{
					HostPort peerHP = new HostPort(argsBean.getPeer());
					String peerHostName = peerHP.host;
					int peerPort = peerHP.port;
					if(command.toLowerCase().equals("connect_peer"))
						send_CONNECT_PEER_REQUEST(peerHostName, peerPort);
					else if(command.toLowerCase().equals("disconnect_peer"))
						send_DISCONNECT_PEER_REQUEST(peerHostName, peerPort);
				}
				//recv
				String msg = in.readLine();
				JSONObject jsonLoad = (JSONObject) parser.parse(msg);
				String payload = (String) jsonLoad.get("payload");
				if (payload != null) {
					if(payload.contains("\n"))
						payload = payload.split("\n")[0];
					Cipher cipher = Cipher.getInstance("AES");
					cipher.init(Cipher.DECRYPT_MODE, aesKey);
					String message = new String(cipher.doFinal(Base64.getDecoder().decode(payload.getBytes())));
					System.out.println(message + " from " + remoteHost);
				}
			}
			socket.close();
		} catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException | IllegalBlockSizeException
				| BadPaddingException | NoSuchPaddingException | InvalidKeyException | ParseException e) {
			e.printStackTrace();
		}
	}
}