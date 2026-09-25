package org.opendiplom.sheets;

/**
 * An uploaded file cannot be read as the expected workbook.
 * The message is written for the operator and names the file and the place.
 */
public final class WorkbookException extends Exception {
    private static final long serialVersionUID = 1L;

    public WorkbookException(final String message) {
        super(message);
    }

    public WorkbookException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
