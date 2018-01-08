package com.opendigitaleducation.launcher.resolvers;

public class NotFoundServiceException extends RuntimeException {

    public NotFoundServiceException() {
        super();
    }

    public NotFoundServiceException(String message) {
        super(message);
    }

}
