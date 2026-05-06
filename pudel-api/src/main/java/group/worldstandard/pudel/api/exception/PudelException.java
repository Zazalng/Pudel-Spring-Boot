package group.worldstandard.pudel.api.exception;

public class PudelException extends RuntimeException {
    enum Type{
        AGENT, // Agent Implement Exception
        AUDIO, // Audio Implement Exception
        COMMAND, // Command Implement Exception
        DATABASE, // Database Relate
        EVENT,
        INTERACTION,
    }

    public PudelException(String message) {
        super(message);
    }

    public PudelException(Throwable throwable) {
        super(throwable);
    }

    public PudelException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
