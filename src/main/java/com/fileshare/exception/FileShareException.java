package com.fileshare.exception;

public class FileShareException extends RuntimeException {
    public FileShareException(String message) { super(message); }
    public FileShareException(String message, Throwable cause) { super(message, cause); }
}
