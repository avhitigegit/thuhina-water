package lk.thuhina.water.whatsapp.client;

/** Meta refused a request or could not be reached; the message is the text written to the message log. */
public class WhatsAppException extends RuntimeException {

    public WhatsAppException(String message) {
        super(message);
    }

    public WhatsAppException(String message, Throwable cause) {
        super(message, cause);
    }
}
