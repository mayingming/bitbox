package unimelb.bitbox;


import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Base64.Decoder;

import javax.crypto.Cipher;

/**--------------------- 
 *Credit to:
 *1. https://stackoverflow.com/questions/19365940/convert-openssh-rsa-key-to-javax-crypto-cipher-compatible-format 
 *2. https://blog.csdn.net/qq_32523587/article/details/79146977
 */

public class Public_Private_Keys {

	public static PublicKey GetPubKey (String pub) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
		KeyFactory f = KeyFactory.getInstance("RSA");
	    RSAPublicKeySpec pubspec = decodeOpenSSH(pub);
	    PublicKey pubKey = f.generatePublic(pubspec);
	    return pubKey;
	}
	
	static RSAPublicKeySpec decodeOpenSSH(String input) {
	    String[] fields = input.split(" ");
	    if ((fields.length < 2) || (!fields[0].equals("ssh-rsa"))) throw new IllegalArgumentException("Unsupported type");
	    byte[] std = Base64.getDecoder().decode(fields[1]);
	    return decodeRSAPublicSSH(std);
	}

	static RSAPublicKeySpec decodeRSAPublicSSH(byte[] encoded) {
	    ByteBuffer input = ByteBuffer.wrap(encoded);
	    String type = string(input);
	    if (!"ssh-rsa".equals(type)) throw new IllegalArgumentException("Unsupported type");
	    BigInteger exp = sshint(input);
	    BigInteger mod = sshint(input);
	    if (input.hasRemaining()) throw new IllegalArgumentException("Excess data");
	    return new RSAPublicKeySpec(mod, exp);
	}

	private static String string(ByteBuffer buf) {
	    return new String(lenval(buf), Charset.forName("US-ASCII"));
	}

	private static BigInteger sshint(ByteBuffer buf) {
	    return new BigInteger(+1, lenval(buf));
	}

	private static byte[] lenval(ByteBuffer buf) {
	    byte[] copy = new byte[buf.getInt()];
	    buf.get(copy);
	    return copy;
	}

	public static byte[] publicEncrypt(byte[] content, PublicKey publicKey) throws Exception{
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cipher.init(Cipher.ENCRYPT_MODE, publicKey);
		byte[] bytes = cipher.doFinal(content);
		return bytes;
	}
}

