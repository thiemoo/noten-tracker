package de.notentracker.persistence;

/** Unchecked-Hülle um {@link java.sql.SQLException}, damit UI- und Service-Code nicht überall try/catch braucht. */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
