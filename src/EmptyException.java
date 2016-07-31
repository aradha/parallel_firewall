
public class EmptyException extends Exception {
    public EmptyException() { super(); }
    public EmptyException(String message) { super(message); }
    public EmptyException(String message, Throwable cause) { super(message, cause); }
    public EmptyException(Throwable cause) { super(cause); }

}
