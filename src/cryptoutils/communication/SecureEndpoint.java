package cryptoutils.communication;

import cryptoutils.cipherutils.CryptoManager;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import javax.crypto.Mac;
import cryptoutils.messagebuilder.MessageBuilder;
import cryptoutils.hashutils.HashManager;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Random;

public class SecureEndpoint {
    private final static String AUTH_ALG = "HmacSHA256";
    private int sequenceCounter = 0;
    private static int AUTH_MAC_SIZE = 256;
    private static long TIME_TH = 1000;
    /*
    public SecureEndpoint(String encKey,String authKey,String authAlg) {
        this.authKey = authKey;
        this.authAlg = authAlg;
        this.encKey = encKey.getBytes();
        try {
            authMACSize = (Mac.getInstance(authAlg)).getMacLength();
        } catch (Exception ex) {
            System.err.println("[CONSTRUCTOR - "+Thread.currentThread().getName()+"]: "+ex.getMessage());
            System.exit(-1);
        }
    }
    */
    public static boolean secureSend(byte[] data,DataInputStream di,DataOutputStream ds,byte[] encKey, String authKey) {
        try{
            System.out.println("[SECURE SEND - "+Thread.currentThread().getName()+"]");
            byte[] timestampedMessage = MessageBuilder.insertTimestamp(data);
            byte[] hashedMessage = MessageBuilder.insertMAC(timestampedMessage,authKey,AUTH_ALG);
            byte[] encryptedMessage = CryptoManager.encryptCBC(hashedMessage, encKey, new Random().nextInt()); 
            System.out.println("[SEND - "+Thread.currentThread().getName()+"]: SENDING SIZE (bytes) "+encryptedMessage.length);            
            ds.writeInt(encryptedMessage.length);
            System.out.println("[SEND - "+Thread.currentThread().getName()+"]: SENDING PAYLOAD");                        
            ds.write(encryptedMessage);
            ds.flush();
            return true;
        } catch(Exception e) {
            System.err.println("[SEND - "+Thread.currentThread().getName()+"]: "+e.getMessage());
            System.exit(-1);            
            return false;
        }              
    }
    
    public static byte[] secureReceive(DataInputStream di,DataOutputStream ds,byte[] encKey, String authKey) {
        try {
            int nonce = (new SecureRandom()).nextInt();
            ds.writeInt(nonce); ds.flush();
            int len = di.readInt();
            if(len > 0) {
                byte[] buffer = new byte[len];
                long read = di.read(buffer);
                if(read != len) throw new Exception("Expected: "+len+ " received: "+read);
                byte[] decryptedMessage = CryptoManager.decryptCBC(buffer, encKey);
                byte[] messageHash = MessageBuilder.extractHash(decryptedMessage, AUTH_MAC_SIZE);  
                Instant timeStamp = MessageBuilder.getTimestamp(buffer);
                byte[] timestampedMessage = MessageBuilder.extractFirstBytes(decryptedMessage, decryptedMessage.length-AUTH_MAC_SIZE);
                byte[] plainText = MessageBuilder.extractFirstBytes(timestampedMessage,timestampedMessage.length-8);
                boolean verified = (HashManager.compareMAC(timestampedMessage, messageHash, authKey, AUTH_ALG) && verifyTimestamp(timeStamp));    
                return (verified)?plainText:null;
            } else {
                return null;
            }
        } catch(Exception e) {
            System.err.println("[RECEIVE- "+Thread.currentThread().getName()+"]: "+e.getMessage());
            System.exit(-1);            
            return null;
        }        
    }
    private static boolean verifyTimestamp(Instant timeStamp){
        return !(timeStamp.isAfter(timeStamp.plusMillis(TIME_TH))||timeStamp.isBefore(timeStamp.minusMillis(TIME_TH)));    
    }
}
