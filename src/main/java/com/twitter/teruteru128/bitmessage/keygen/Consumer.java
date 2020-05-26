package com.twitter.teruteru128.bitmessage.keygen;

import java.util.concurrent.BlockingQueue;

public class Consumer implements Runnable {


    public Consumer() {
    }

    @Override
    public void run() {
        BlockingQueue<Response> responses = Queues.getResponseQueue();
        while (true) {
            try {
                BMAddressGenerator.exportAddressToStdout(responses.take());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
