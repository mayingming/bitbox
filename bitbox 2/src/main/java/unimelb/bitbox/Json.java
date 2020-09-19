package unimelb.bitbox;

import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import unimelb.bitbox.util.FileSystemManager.FileDescriptor;

/**
 * This class contains boring functions to create Json files
 * 
 * @author Group: Coconut Opener
 */

public class Json {

	@SuppressWarnings("unchecked")
	public static String INVALID_PROTOCOL(String reason) {
		JSONObject result = new JSONObject();
		result.put("message", reason);
		result.put("command", "INVALID_PROTOCOL");
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static JSONArray ConnectionList(ServerThread[] STs) {
		JSONArray peers = new JSONArray();
		for (int i = 0; i < STs.length; i++) {
			if (STs[i] != null) {
				JSONObject peer = new JSONObject();
				peer.put("host", STs[i].remoteHost);
				peer.put("port", STs[i].remoteServerPort);
				peers.add(peer);
			}
		}
		ArrayList<ServerThread> STs2 = Peer.servers;
		for (int i = 0; i < STs2.size(); i++) {
			JSONObject peer = new JSONObject();
			peer.put("host", STs2.get(i).remoteHost);
			peer.put("port", STs2.get(i).remoteServerPort);
			peers.add(peer);
		}
		return peers;
	}

	@SuppressWarnings("unchecked")
	public static String CONNECTION_REFUSED(ServerThread[] STs) {
		JSONObject result = new JSONObject();
		result.put("peers", ConnectionList(STs));
		result.put("message", "connection limit reached");
		result.put("command", "CONNECTION_REFUSED");

		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String HANDSHAKE_REQUEST(String host, int port) {
		JSONObject result = new JSONObject();
		JSONObject hostPort = new JSONObject();
		hostPort.put("port", port);
		hostPort.put("host", host);
		result.put("hostPort", hostPort);
		result.put("command", "HANDSHAKE_REQUEST");
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String HANDSHAKE_RESPONSE(String host, int port) {
		JSONObject result = new JSONObject();
		JSONObject hostPort = new JSONObject();
		hostPort.put("port", port);
		hostPort.put("host", host);
		result.put("hostPort", hostPort);
		result.put("command", "HANDSHAKE_RESPONSE");
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String DIRECTORY_CREATE_REQUEST(String pathName) {
		JSONObject result = new JSONObject();
		result.put("pathName", pathName);
		result.put("command", "DIRECTORY_CREATE_REQUEST");
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String DIRECTORY_CREATE_RESPONSE(String pathName, String message, boolean status) {
		JSONObject result = new JSONObject();
		result.put("pathName", pathName);
		result.put("message", message);
		result.put("status", status);
		result.put("command", "DIRECTORY_CREATE_RESPONSE");
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String DIRECTORY_DELETE_REQUEST(String pathName) {
		JSONObject result = new JSONObject();
		result.put("pathName", pathName);
		result.put("command", "DIRECTORY_DELETE_REQUEST");
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String DIRECTORY_DELETE_RESPONSE(String pathName, String message, boolean status) {
		JSONObject result = new JSONObject();
		result.put("pathName", pathName);
		result.put("message", message);
		result.put("status", status);
		result.put("command", "DIRECTORY_DELETE_RESPONSE");
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String FILE_CREATE_REQUEST(FileDescriptor fileDescriptor, String pathName) {
		JSONObject result = new JSONObject();
		JSONObject filedescreptor = new JSONObject();
		filedescreptor.put("md5", fileDescriptor.md5);
		filedescreptor.put("lastModified", fileDescriptor.lastModified);
		filedescreptor.put("fileSize", fileDescriptor.fileSize);
		result.put("fileDescriptor", filedescreptor);
		result.put("pathName", pathName);
		result.put("command", "FILE_CREATE_REQUEST");
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String FILE_CREATE_RESPONSE(FileDescriptor fileDescriptor, String pathName, String message,
			boolean status) {
		JSONObject result = new JSONObject();
		JSONObject filedescreptor = new JSONObject();
		filedescreptor.put("md5", fileDescriptor.md5);
		filedescreptor.put("lastModified", fileDescriptor.lastModified);
		filedescreptor.put("fileSize", fileDescriptor.fileSize);
		result.put("fileDescriptor", filedescreptor);
		result.put("pathName", pathName);
		result.put("status", status);
		result.put("message", message);
		result.put("command", "FILE_CREATE_RESPONSE");
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String FILE_BYTES_REQUEST(FileDescriptor fileDescriptor, String pathName, long position,
			long length) {
		JSONObject result = new JSONObject();
		JSONObject filedescreptor = new JSONObject();
		filedescreptor.put("md5", fileDescriptor.md5);
		filedescreptor.put("lastModified", fileDescriptor.lastModified);
		filedescreptor.put("fileSize", fileDescriptor.fileSize);
		result.put("fileDescriptor", filedescreptor);
		result.put("pathName", pathName);
		result.put("position", position);
		result.put("length", length);
		result.put("command", "FILE_BYTES_REQUEST");
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String FILE_BYTES_RESPONSE(FileDescriptor fileDescriptor, String pathName, String content,
			String message, long position, long length, boolean status) {
		JSONObject result = new JSONObject();
		JSONObject filedescreptor = new JSONObject();
		filedescreptor.put("md5", fileDescriptor.md5);
		filedescreptor.put("lastModified", fileDescriptor.lastModified);
		filedescreptor.put("fileSize", fileDescriptor.fileSize);
		result.put("fileDescriptor", filedescreptor);
		result.put("pathName", pathName);
		result.put("position", position);
		result.put("length", length);
		result.put("content", content);
		result.put("message", message);
		result.put("status", status);
		result.put("command", "FILE_BYTES_RESPONSE");
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String FILE_DELETE_REQUEST(FileDescriptor fileDescriptor, String pathName) {
		JSONObject result = new JSONObject();
		JSONObject filedescreptor = new JSONObject();
		filedescreptor.put("md5", fileDescriptor.md5);
		filedescreptor.put("lastModified", fileDescriptor.lastModified);
		filedescreptor.put("fileSize", fileDescriptor.fileSize);
		result.put("fileDescriptor", filedescreptor);
		result.put("pathName", pathName);
		result.put("command", "FILE_DELETE_REQUEST");
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String FILE_DELETE_RESPONSE(FileDescriptor fileDescriptor, String pathName, String message,
			boolean status) {
		JSONObject result = new JSONObject();
		JSONObject filedescreptor = new JSONObject();
		filedescreptor.put("md5", fileDescriptor.md5);
		filedescreptor.put("lastModified", fileDescriptor.lastModified);
		filedescreptor.put("fileSize", fileDescriptor.fileSize);
		result.put("fileDescriptor", filedescreptor);
		result.put("pathName", pathName);
		result.put("command", "FILE_DELETE_RESPONSE");
		result.put("message", message);
		result.put("status", status);
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String FILE_MODIFY_REQUEST(FileDescriptor fileDescriptor, String pathName) {
		JSONObject result = new JSONObject();
		JSONObject filedescreptor = new JSONObject();
		filedescreptor.put("md5", fileDescriptor.md5);
		filedescreptor.put("lastModified", fileDescriptor.lastModified);
		filedescreptor.put("fileSize", fileDescriptor.fileSize);
		result.put("fileDescriptor", filedescreptor);
		result.put("pathName", pathName);
		result.put("command", "FILE_MODIFY_REQUEST");
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String FILE_MODIFY_RESPONSE(FileDescriptor fileDescriptor, String pathName, String message,
			boolean status) {
		JSONObject result = new JSONObject();
		JSONObject filedescreptor = new JSONObject();
		filedescreptor.put("md5", fileDescriptor.md5);
		filedescreptor.put("lastModified", fileDescriptor.lastModified);
		filedescreptor.put("fileSize", fileDescriptor.fileSize);
		result.put("fileDescriptor", filedescreptor);
		result.put("pathName", pathName);
		result.put("status", status);
		result.put("message", message);
		result.put("command", "FILE_MODIFY_RESPONSE");
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String CLIENT_CHALLENGE_RESPONSE(String aesKey, boolean status, String message) {
		JSONObject result = new JSONObject();
		result.put("command", "AUTH_RESPONSE");
		result.put("AES128", aesKey);
		result.put("status", status);
		result.put("message", message);
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String CLIENT_CHALLENGE_RESPONSE(boolean status, String message) {
		JSONObject result = new JSONObject();
		result.put("command", "AUTH_RESPONSE");
		result.put("status", status);
		result.put("message", message);
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String PAYLOAD(String payload) {
		JSONObject result = new JSONObject();
		result.put("payload", payload);
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String LIST_PEERS_RESPONSE(JSONArray jsonArray) {
		JSONObject result = new JSONObject();
		result.put("command", "LIST_PEERS_RESPONSE");
		result.put("peers", jsonArray);
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String CONNECT_PEER_RESPONSE(String host, int port, boolean status, String message) {
		JSONObject result = new JSONObject();
		result.put("host", host);
		result.put("port", port);
		result.put("command", "CONNECT_PEER_RESPONSE");
		result.put("status", status);
		result.put("message", message);
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String DISCONNECT_PEER_RESPONSE(String host, int port, boolean status, String message) {
		JSONObject result = new JSONObject();
		result.put("host", host);
		result.put("port", port);
		result.put("command", "DISCONNECT_PEER_RESPONSE");
		result.put("status", status);
		result.put("message", message);
		return result.toJSONString();
	}
	
	@SuppressWarnings("unchecked")
	public static String CLIENT_CHALLENGE_REQUEST(String identity) {
		JSONObject result = new JSONObject();
		result.put("command", "AUTH_REQUEST");
		result.put("identity", identity);
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String LIST_PEERS_REQUEST() {
		JSONObject result = new JSONObject();
		result.put("command", "LIST_PEERS_REQUEST");
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String CONNECT_PEER_REQUEST(String host, int port) {
		JSONObject result = new JSONObject();
		result.put("command", "CONNECT_PEER_REQUEST");
		result.put("host", host);
		result.put("port", port);
		return result.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public static String DISCONNECT_PEER_REQUEST(String host, int port) {
		JSONObject result = new JSONObject();
		result.put("command", "DISCONNECT_PEER_REQUEST");
		result.put("host", host);
		result.put("port", port);
		return result.toJSONString();
	}
}
