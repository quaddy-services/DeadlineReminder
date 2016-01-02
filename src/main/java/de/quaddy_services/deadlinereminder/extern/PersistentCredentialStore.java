package de.quaddy_services.deadlinereminder.extern;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.CredentialStore;

public class PersistentCredentialStore implements CredentialStore {
	private static final Logger LOGGER = LoggerFactory.getLogger(PersistentCredentialStore.class);

	@Override
	public void delete(String aUserId, Credential aCredential) {
		delete(aUserId);
	}

	public boolean delete(String aUserId) {
		File tempFile = getStoreFile(aUserId);
		return tempFile.delete();
	}

	@Override
	public boolean load(String aUserId, Credential aCredential) {
		File tempFile = getStoreFile(aUserId);
		if (tempFile.exists()) {
			try {
				ObjectInputStream tempIn = new ObjectInputStream(new FileInputStream(tempFile));
				PersistentCredentialInfo tempInfo = (PersistentCredentialInfo) tempIn.readObject();
				aCredential.setAccessToken(tempInfo.getAccessToken());
				aCredential.setRefreshToken(tempInfo.getRefreshToken());
				aCredential.setExpirationTimeMilliseconds(tempInfo.getExpirationTimeMilliseconds());
				tempIn.close();
				LOGGER.info("Loaded: " + tempInfo);
				return true;
			} catch (Exception e) {
				LOGGER.error("Ignore", e);
			}
		}
		return false;
	}

	@Override
	public void store(String aUserId, Credential aCredential) {
		File tempFile = getStoreFile(aUserId);
		PersistentCredentialInfo tempInfo = new PersistentCredentialInfo();
		tempInfo.setAccessToken(aCredential.getAccessToken());
		tempInfo.setExpirationTimeMilliseconds(aCredential.getExpirationTimeMilliseconds());
		tempInfo.setRefreshToken(aCredential.getRefreshToken());
		LOGGER.info("Store:" + tempInfo);
		try {
			ObjectOutputStream tempOut = new ObjectOutputStream(new FileOutputStream(tempFile));
			tempOut.writeObject(tempInfo);
			tempOut.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private File getStoreFile(String aUserId) {
		File tempDir = new File(System.getProperty("user.home", ".") + "/DeadlineReminder");
		tempDir.mkdirs();
		return new File(tempDir.getAbsolutePath() + "/" + PersistentCredentialInfo.class.getName() + "-" + aUserId
				+ ".info");
	}

}
