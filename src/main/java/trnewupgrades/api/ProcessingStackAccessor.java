package trnewupgrades.api;

public interface ProcessingStackAccessor {
    boolean isProcessingStack();
    void setProcessingStack(boolean value);

    default void processStack() {
        setProcessingStack(true);
    }

    default void resetProcessingStack() {
        setProcessingStack(false);
    }
}
