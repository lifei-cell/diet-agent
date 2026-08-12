package com.diet.exception;

/** Redis coordination is mandatory for chat writes in a multi-replica deployment. */
public class SessionCoordinatorUnavailableException extends DietException {
    public SessionCoordinatorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
