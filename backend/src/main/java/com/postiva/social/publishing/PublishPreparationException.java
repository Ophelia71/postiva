package com.postiva.social.publishing;

/**
 * Preparation failed before calling the endpoint that makes a post public.
 * Do not wrap exceptions from the final publish request: its result may be unknown.
 */
public final class PublishPreparationException extends RuntimeException {
    public PublishPreparationException(RuntimeException cause) {
        super(cause.getMessage(), cause);
    }
}
