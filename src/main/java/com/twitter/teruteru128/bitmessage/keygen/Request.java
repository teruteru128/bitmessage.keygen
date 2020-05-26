package com.twitter.teruteru128.bitmessage.keygen;

// Number of Leading Zero
public class Request {

    private int nlzToRequest;
    private int taskID;
    /**
     * 65,536 key (pairs) as default cache size,
     * 4,294,967,296 patterns as combination of signature key and encryption key.
     * The key combination pattern is proportional to the square of the cache size.
     */
    private int keyCacheSize = 65536;

    public Request(int nlzToRequest, int taskID) {
        this.nlzToRequest = nlzToRequest;
        this.taskID = taskID;
    }

    /**
     * @return the requireNlz
     */
    public int getNlzToRequest() {
        return nlzToRequest;
    }

    /**
     * @param nlzToRequest the nlzToRequest to set
     */
    public void setNlzToRequest(int nlzToRequest) {
        this.nlzToRequest = nlzToRequest;
    }

    /**
     * @return the taskID
     */
    public int getTaskID() {
        return taskID;
    }

    /**
     * @param taskID the taskID to set
     */
    public void setTaskID(int taskID) {
        this.taskID = taskID;
    }

    /**
     * @return the keyCacheSize
     */
    public int getKeyCacheSize() {
        return keyCacheSize;
    }

    /**
     * @param keyCacheSize the keyCacheSize to set
     */
    public void setKeyCacheSize(int keyCacheSize) {
        this.keyCacheSize = keyCacheSize;
    }
}
