package de.quaddy_services.deadlinereminder.extern;

import java.io.Serializable;
import java.util.Date;

public class PersistentCredentialInfo implements Serializable {
	  /** Access token issued by the authorization server. */
	  private String accessToken;
	private Long expirationTimeMilliseconds;
	private String refreshToken;

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String aAccessToken) {
		accessToken = aAccessToken;
	}

	public void setExpirationTimeMilliseconds(Long aExpirationTimeMilliseconds) {
		expirationTimeMilliseconds = aExpirationTimeMilliseconds;
		
	}

	public void setRefreshToken(String aRefreshToken) {
		refreshToken=aRefreshToken;
		
	}

	public Long getExpirationTimeMilliseconds() {
		return expirationTimeMilliseconds;
	}

	public String getRefreshToken() {
		return refreshToken;
	}

	@Override
	public String toString() {
		Date tempDate;
		if (expirationTimeMilliseconds != null) {
			tempDate = new Date(expirationTimeMilliseconds.longValue());
		} else {
			tempDate = null;
		}
		return "PersistentCredentialInfo [accessToken=" + accessToken + ", expirationTimeMilliseconds="
				+ expirationTimeMilliseconds + "="+tempDate+", refreshToken=" + refreshToken + "]";
	}
}
