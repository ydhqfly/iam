package it.infn.mw.iam.api.account;

public class InvalidPasswordResetTokenError extends RuntimeException {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  public InvalidPasswordResetTokenError(String message) {
    super(message);
  }

}
