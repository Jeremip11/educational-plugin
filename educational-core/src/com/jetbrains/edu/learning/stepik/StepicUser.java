package com.jetbrains.edu.learning.stepik;

import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.edu.learning.stepik.alt.TokenInfo;
import org.jetbrains.annotations.NotNull;

public class StepicUser {
  private int id = -1;
  @SerializedName("first_name") private String myFirstName;
  @SerializedName("last_name") private String myLastName;
  private String myAccessToken;
  private String myRefreshToken;
  private long myExpiresIn;
  private boolean isGuest;

  private String myHyperskillAccessToken;
  private String myHyperskillRefreshToken;

  private StepicUser() {
    myFirstName = "";
    myLastName = "";
    myAccessToken = "";
    myRefreshToken = "";
    myExpiresIn = -1;
  }

  public long getExpiresIn() {
    return myExpiresIn;
  }

  public void setExpiresIn(long expiresIn) {
    myExpiresIn = expiresIn;
  }

  public static StepicUser createEmptyUser() {
    return new StepicUser();
  }

  public StepicUser(@NotNull StepikWrappers.TokenInfo tokenInfo) {
    setTokenInfo(tokenInfo);
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getFirstName() {
    return myFirstName;
  }

  public void setFirstName(final String firstName) {
    myFirstName = firstName;
  }

  public String getLastName() {
    return myLastName;
  }

  public void setLastName(final String lastName) {
    myLastName = lastName;
  }

  @NotNull
  public String getName() {
    return StringUtil.join(new String[]{myFirstName, myLastName}, " ");
  }

  @NotNull
  public String getAccessToken() {
    // for old project where authors were created with null tokens
    if (myAccessToken == null) {
      return "";
    }
    return myAccessToken;
  }

  public void setAccessToken(String accessToken) {
    this.myAccessToken = accessToken;
  }

  @NotNull
  public String getRefreshToken() {
    // for old project where authors were created with null tokens
    if (myRefreshToken == null) {
      return "";
    }
    return myRefreshToken;
  }

  public void setRefreshToken(String refreshToken) {
    this.myRefreshToken = refreshToken;
  }

  public void setTokenInfo(@NotNull final StepikWrappers.TokenInfo tokenInfo) {
    myAccessToken = tokenInfo.getAccessToken();
    myRefreshToken = tokenInfo.getRefreshToken();
    myExpiresIn = tokenInfo.getExpiresIn();
  }

  public void setHyperskillTokenInfo(@NotNull final TokenInfo tokenInfo) {
    myHyperskillAccessToken = tokenInfo.getAccessToken();
    myHyperskillRefreshToken = tokenInfo.getRefreshToken();
  }

  public boolean isGuest() {
    return isGuest;
  }

  public void setGuest(boolean guest) {
    isGuest = guest;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StepicUser user = (StepicUser)o;

    if (id != user.id) return false;
    if (isGuest != user.isGuest) return false;
    if (!myFirstName.equals(user.myFirstName)) return false;
    if (!myLastName.equals(user.myLastName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = id;
    result = 31 * result + myFirstName.hashCode();
    result = 31 * result + myLastName.hashCode();
    return result;
  }
}
