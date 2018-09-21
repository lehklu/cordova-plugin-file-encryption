package com.prsuhas;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.cordova.LOG;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaResourceApi;

import org.json.JSONArray;
import org.json.JSONException;

import android.net.Uri;
import android.content.Context;

import com.facebook.android.crypto.keychain.AndroidConceal;
import com.facebook.crypto.Crypto;
import com.facebook.crypto.CryptoConfig;
import com.facebook.crypto.Entity;
import com.facebook.crypto.exception.CryptoInitializationException;
import com.facebook.crypto.exception.KeyChainException;
import com.facebook.android.crypto.keychain.SharedPrefsBackedKeyChain;
import com.facebook.crypto.keychain.KeyChain;
import com.facebook.soloader.SoLoader;

/* PassphraseKeyChain */
import com.facebook.crypto.MacConfig;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
/**/

/**
 * This class encrypts and decrypts files using the Conceal encryption lib
 */
public class FileEncryption extends CordovaPlugin {

  private static final String TAG = "FileEncryption";

  public static final String ENCRYPT_ACTION = "encrypt";
  public static final String DECRYPT_ACTION = "decrypt";

  private Context CONTEXT;
  private Crypto CRYPTO;
  private Entity ENTITY;

  private OutputStream OUTPUT_STREAM;
  private InputStream INPUT_STREAM;

  private String FILE_NAME;
  private Uri SOURCE_URI;
  private File SOURCE_FILE;
  private File TEMP_FILE;

	private MessageDigest MESSAGEDIGEST;

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
          throws JSONException {
    if (action.equals(ENCRYPT_ACTION) || action.equals(DECRYPT_ACTION)) {
      CordovaResourceApi resourceApi = webView.getResourceApi();

      String path = args.getString(0);
      String pass = args.getString(1);
      boolean passIsKey = Boolean.parseBoolean(args.getString(2));

      Uri normalizedPath = resourceApi.remapUri(Uri.parse(path));

      LOG.d(TAG, "normalizedPath: "+ normalizedPath.getPath().toString());

      this.cryptOp(normalizedPath.toString(), pass, action, passIsKey, callbackContext);

      return true;
    }

    return false;
  }

  private void cryptOp(String path, String password, String action, boolean passIsKey, CallbackContext callbackContext) {

    try {
			MESSAGEDIGEST = MESSAGEDIGEST!=null?MESSAGEDIGEST:MessageDigest.getInstance("SHA-256");

    	// init crypto variables
    	this.initCrypto(path, password, passIsKey, callbackContext);


    	// create output stream which encrypts the data as
    	// it is written to it and writes out to the file
      if (action.equals(ENCRYPT_ACTION)) {
        // create encrypted output stream
        OutputStream encryptedOutputStream = CRYPTO.getCipherOutputStream(OUTPUT_STREAM, ENTITY);
        // write to temp file
        this.writeFile(INPUT_STREAM, encryptedOutputStream, callbackContext);
      } else if (action.equals(DECRYPT_ACTION)) {
        // create decrypted input stream
        InputStream decryptedInputStream = CRYPTO.getCipherInputStream(INPUT_STREAM, ENTITY);
        // write to temp file
        this.writeFile(decryptedInputStream, OUTPUT_STREAM, callbackContext);
      }

      // delete original file after write
      boolean deleted = SOURCE_FILE.delete();
      if (deleted) {
        File src = TEMP_FILE;
        File dst = new File(SOURCE_URI.getPath());

        this.copyFile(src, dst);

        callbackContext.success(dst.getPath());
      } else {
        callbackContext.error("cryptOp source file not deleted");
      }
    } catch (IOException e) {
      LOG.d(TAG, "initCrypto IOException: " + e.getMessage());
      callbackContext.error(e.getMessage());
    } catch (CryptoInitializationException e) {
      LOG.d(TAG, "initCrypto CryptoInitializationException: " + e.getMessage());
      callbackContext.error(e.getMessage());
    } catch (KeyChainException e) {
      LOG.d(TAG, "initCrypto KeyChainException: " + e.getMessage());
      callbackContext.error(e.getMessage());
    } catch (NoSuchAlgorithmException e) {
      LOG.d(TAG, "initCrypto NoSuchAlgorithmException: " + e.getMessage());
      callbackContext.error(e.getMessage());
    } catch (Exception e) {
      LOG.d(TAG, "initCrypto Exception: " + e.getMessage());
      callbackContext.error(e.getMessage());
    }
  }

  private void initCrypto(String path, String password, boolean passIsKey, CallbackContext callbackContext) {
    if (path != null && path.length() > 0 && password != null && password.length() > 0) {
      SOURCE_URI  = Uri.parse(path);
      FILE_NAME = SOURCE_URI.getLastPathSegment();

      CONTEXT = cordova.getActivity().getApplicationContext();
      SoLoader.init(CONTEXT, false);

      SOURCE_FILE = new File(SOURCE_URI.getPath());

      // explicitely create 256-bit key chain
      KeyChain keyChain = passIsKey?
      	new PassphraseKeyChain(CONTEXT, CryptoConfig.KEY_256, MESSAGEDIGEST.digest(password.getBytes(StandardCharsets.UTF_8))):
      	new SharedPrefsBackedKeyChain(CONTEXT, CryptoConfig.KEY_256);
      // create the default crypto (expects 256-bit key) and initialize crypto object
      CRYPTO = AndroidConceal.get().createDefaultCrypto(keyChain);

      ENTITY = Entity.create(password);

      // check for whether crypto is available
      if (!CRYPTO.isAvailable()) {
        callbackContext.error("initCrypto CRYPTO is not available");
        return;
      }

      try {
        // initialize temp file
        TEMP_FILE = File.createTempFile(FILE_NAME, null, CONTEXT.getExternalCacheDir());
        // initialize output stream for temp file
        OUTPUT_STREAM = new BufferedOutputStream(new FileOutputStream(TEMP_FILE));
        // create input stream from source file
        INPUT_STREAM = new FileInputStream(SOURCE_FILE);
      } catch (FileNotFoundException e) {
        LOG.d(TAG, "initCrypto FileNotFoundException: " + e.toString());
        callbackContext.error(e.getMessage());
        e.printStackTrace();
      } catch (IOException e) {
        LOG.d(TAG, "initCrypto IOException: " + e.toString());
        callbackContext.error(e.getMessage());
        e.printStackTrace();
      }
    } else {
      LOG.d(TAG, "initCrypto error ");
      callbackContext.error(2);
    }
  }

  private void writeFile(InputStream inputStream, OutputStream outputStream, CallbackContext callbackContext) {
    try {
      // create new byte object with source file length
      byte[] data = new byte[(int) SOURCE_FILE.length()];

      // read contents of source file byte by byte
      int buffer = 0;
      while ((buffer = inputStream.read(data)) > 0) {
        // write contents to encrypted output stream
        outputStream.write(data, 0, buffer);
        outputStream.flush();
      }

      LOG.d(TAG, "writeFile called ");

      // close output stream
      outputStream.close();
      inputStream.close();
    } catch (IOException e) {
      LOG.d(TAG, "writeFile error: " + e.toString());
      callbackContext.error(e.getMessage());
      e.printStackTrace();
    }
  }

  public void copyFile(File source, File dest) throws IOException {
      InputStream in = new FileInputStream(source);
      OutputStream out = new FileOutputStream(dest);

      // Transfer bytes from in to out
      byte[] buf = new byte[1024];
      int len;
      while ((len = in.read(buf)) > 0) {
          out.write(buf, 0, len);
      }
      in.close();
      out.close();

      LOG.d(TAG, "copyFile called ");
  }
}

/**
 * An implementation of a keychain that is based on a passphrase.
 */
class PassphraseKeyChain implements KeyChain {

	private byte[] mPassphraseBytes;

  private final CryptoConfig mCryptoConfig;

  protected byte[] mCipherKey;
  protected boolean mIsCipherKeySet;

  protected byte[] mMacKey;
  protected boolean mIsMacKeySet;

  public PassphraseKeyChain(Context context, CryptoConfig config, byte[] passphraseBytes) {
    mCryptoConfig = config;

		mPassphraseBytes = passphraseBytes;
  }

  @Override
  public synchronized byte[] getCipherKey() throws KeyChainException {

    if(!mIsCipherKeySet)
    {
			mCipherKey = generateKey(mCryptoConfig.keyLength);
    }

    mIsCipherKeySet = true;
    return mCipherKey;
  }

  @Override
  public byte[] getMacKey() throws KeyChainException {

    if(!mIsMacKeySet)
    {
			mMacKey = generateKey(MacConfig.DEFAULT.keyLength);
    }

    mIsMacKeySet = true;
    return mMacKey;
  }

  @Override
  public byte[] getNewIV() throws KeyChainException {

    byte[] iv = new byte[mCryptoConfig.ivLength];

		fillKey(iv);

    return iv;
  }

  @Override
  public synchronized void destroyKeys() {

		Arrays.fill(mPassphraseBytes, (byte) 0);
		mPassphraseBytes=null;

    mIsCipherKeySet = false;
    mIsMacKeySet = false;
    if (mCipherKey != null) {
      Arrays.fill(mCipherKey, (byte) 0);
    }
    if (mMacKey != null) {
      Arrays.fill(mMacKey, (byte) 0);
    }
    mCipherKey = null;
    mMacKey = null;
  }

  private byte[] generateKey(int length) throws KeyChainException {

    byte[] key = new byte[length];

    fillKey(key);

    return key;
  }

  private void fillKey(byte[] target) {

		for(int i=0, len=target.length; i<len; i++)
		{
   		target[i]=mPassphraseBytes[i%mPassphraseBytes.length];
		}
  }
}
