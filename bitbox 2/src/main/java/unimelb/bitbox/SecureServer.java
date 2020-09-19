package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import unimelb.bitbox.util.Configuration;

public class SecureServer extends Thread {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	private JSONParser parser;
	private ServerSocket listeningSocket;
	private BufferedReader in;
	private BufferedWriter out;
	private int remotePort;
	private String remoteHost;
	private static String[] pubKeys;

	private PublicKey rsaPub;
	private SecretKey sreKey;
	private boolean exchanged;

	private void send(String str) throws IOException {
		log.info("(" + str + " send to " + remoteHost);
		out.write(str + "\n");
		out.flush();
	}

	private void sendEncrypted(String message) {
		try {
			log.info(message + " will be encrypted to " + remoteHost);
			
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
			cipher.init(Cipher.ENCRYPT_MODE, sreKey);
			byte[] encrypted = cipher.doFinal(message.getBytes("UTF-8"));
			String payload = Base64.getEncoder().encodeToString(encrypted);
			send(Json.PAYLOAD(payload));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public PublicKey GetRsaPub(String identity) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
		for (String key : pubKeys)
			if (key.contains(identity))
				return Public_Private_Keys.GetPubKey(key);
		return null;
	}

	private void recv_CLIENT_CHALLENGE_REQUEST(JSONObject json) throws Exception {
		String identity = (String) json.get("identity");
		rsaPub = GetRsaPub(identity);
		if (rsaPub != null) {
			// generate AES
			KeyGenerator keyGen = KeyGenerator.getInstance("AES");
			keyGen.init(128);
			sreKey = keyGen.generateKey();

			// send
			byte[] encryptAesKey = Public_Private_Keys.publicEncrypt(sreKey.getEncoded(), rsaPub);
			String rsaEncAes = Base64.getEncoder().encodeToString(encryptAesKey);
			send(Json.CLIENT_CHALLENGE_RESPONSE(rsaEncAes, true, "public key found"));
			exchanged = true;
		} else
			send(Json.CLIENT_CHALLENGE_RESPONSE(false, "public key not found"));
	}

	private void recv_command(String payload)
			throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException,
			BadPaddingException, IOException, ParseException, InterruptedException {
		
		// decrypt
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.DECRYPT_MODE, sreKey);
		String message = new String(cipher.doFinal(Base64.getDecoder().decode(payload.getBytes())));
		
		//unpadding
		if(message.contains("\n"))
			message = message.split("\n")[0];
		
		log.info("Decrypted message: " + message);
		
		JSONObject jsonLoad = (JSONObject) parser.parse(message);
		String command = (String) jsonLoad.get("command");
		if (command.equals("LIST_PEERS_REQUEST")) {
			recv_LIST_PEERS_REQUEST();
		} else if (command.equals("CONNECT_PEER_REQUEST")) {
			recv_CONNECT_PEER_REQUEST(jsonLoad);
		} else if (command.equals("DISCONNECT_PEER_REQUEST")) {
			recv_DISCONNECT_PEER_REQUEST(jsonLoad);
		}
	}

	private void recv_LIST_PEERS_REQUEST() throws IOException {
		sendEncrypted(Json.LIST_PEERS_RESPONSE(Peer.connectionList()));
	}

	private void recv_CONNECT_PEER_REQUEST(JSONObject json) throws IOException, InterruptedException {
		String host = (String) json.get("host");
		int port = ((Number) json.get("port")).intValue();
		String message;
		boolean success =  Peer.connect(host, port);
		if (success)
			message = "connected to peer";
		else
			message = "connection failed";
		sendEncrypted(Json.CONNECT_PEER_RESPONSE(host, port, success, message));
	}

	private void recv_DISCONNECT_PEER_REQUEST(JSONObject json) throws IOException {
		String host = (String) json.get("host");
		int port = ((Number) json.get("port")).intValue();
		String message;
		boolean success =  Peer.disconnect(host, port);
		if (success)
			message = "disconnected from peer";
		else
			message = "connection not active";
		sendEncrypted(Json.DISCONNECT_PEER_RESPONSE(host, port, success, message));
	}

	public SecureServer() throws IOException {
		Configuration.getConfiguration();
		parser = new JSONParser();
		String keySet = Configuration.getConfigurationValue("authorized_keys");
		if (keySet.length() > 1)
			pubKeys = keySet.split("\\s*,\\s*");
		else
			pubKeys = null;
	}

	private void init(Socket socket) throws UnsupportedEncodingException, IOException {
		in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
		out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
		remotePort = socket.getPort();
		remoteHost = socket.getInetAddress().getHostName();
		rsaPub = null;
		sreKey = null;
		exchanged = false;
	}

	public void run() {
		try {
			listeningSocket = new ServerSocket(Peer.clientPort);
			log.info("Server listening on port " + Peer.clientPort + " for secure client connection");
			while (true) {
				Socket socket = listeningSocket.accept();
				init(socket);
				log.info("Connection " + remoteHost + ":" + remotePort + " accepted");
				while (true) {
					String msg = null;

					try {
						if (in.ready()) {
							msg = in.readLine();
							log.info("(" + msg + " from " + remoteHost);

							JSONObject jsonLoad = (JSONObject) parser.parse(msg);
							String identity = (String) jsonLoad.get("identity");
							String payload = (String) jsonLoad.get("payload");

							if (identity != null)
								recv_CLIENT_CHALLENGE_REQUEST(jsonLoad);
							else if (payload != null && exchanged)
							{
								recv_command(payload);
								socket.close();
							}
						}
					} catch (Exception e) {
						socket.close();
						e.printStackTrace();
					}

					if (socket.isClosed())
						break;
				}
			}
		} catch (SocketException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (listeningSocket != null) {
				try {
					listeningSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
