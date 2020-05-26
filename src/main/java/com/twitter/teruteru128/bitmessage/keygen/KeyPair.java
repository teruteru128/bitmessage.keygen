package com.twitter.teruteru128.bitmessage.keygen;

import java.io.Serializable;

public class KeyPair implements Cloneable, Serializable {

    private static final long serialVersionUID = 1;

    private byte[] privateKey;
    private byte[] publicKey;

    public KeyPair(byte[] privateKey, byte[] publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    /**
     * @return the privateKey
     */
    public byte[] getPrivateKey() {
        return privateKey;
    }

    /**
     * @return the publicKey
     */
    public byte[] getPublicKey() {
        return publicKey;
    }

    @Override
    public KeyPair clone() {
        KeyPair pair = null;
        try {
            pair = (KeyPair) super.clone();
        } catch (CloneNotSupportedException e) {
        }
        return pair;
    }
}
