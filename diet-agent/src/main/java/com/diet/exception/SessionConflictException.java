package com.diet.exception;

/** Raised when another request or application replica owns the same conversation work. */
public class SessionConflictException extends DietException {
    public SessionConflictException(String message) {
        super(message);
    }
}
