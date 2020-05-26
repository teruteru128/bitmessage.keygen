package com.twitter.teruteru128.bitmessage.keygen;

import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import com.twitter.teruteru128.Base58;
import com.twitter.teruteru128.bitmessage.Structs;

/**
 * @see https://github.com/Bitmessage/PyBitmessage/blob/6f35da4096770a668c4944c3024cd7ddb34be092/src/addresses.py
 */
public final class BMAddress {
    private BMAddress() {
    }

    public static final String encodeAddress(int version, int stream, byte[] ripe, int max) {
        max = Math.max(Math.min(max, 20), 1);
        if (version >= 2 && version < 4) {
            if (ripe.length != 20) {
                throw new IllegalArgumentException(
                        "Programming error in encodeAddress: The length of a given ripe hash was not 20.");
            }
            int i = 0;
            for (; ripe[i] == 0 && i < max; i++) {
                // COUNTING
            }
            ripe = Arrays.copyOfRange(ripe, i, 20);
        } else if (version == 4) {
            if (ripe.length != 20) {
                throw new IllegalArgumentException(
                        "Programming error in encodeAddress: The length of a given ripe hash was not 20.");
            }
            int i = 0;
            for (; ripe[i] == 0 && i < max; i++) {
                // COUNTING
            }
            ripe = Arrays.copyOfRange(ripe, i, 20);
        }
        byte[] variantVersion = Structs.encodeVarint(version);
        byte[] variantStream = Structs.encodeVarint(stream);
        byte[] storedBinaryData = new byte[variantVersion.length + variantStream.length + ripe.length + 4];
        System.arraycopy(variantVersion, 0, storedBinaryData, 0, variantVersion.length);
        System.arraycopy(variantStream, 0, storedBinaryData, variantVersion.length, variantStream.length);
        System.arraycopy(ripe, 0, storedBinaryData, variantVersion.length + variantStream.length, ripe.length);
        try {
            MessageDigest sha512 = MessageDigest.getInstance("sha-512");
            byte[] cache64 = new byte[64];
            sha512.update(storedBinaryData, 0, storedBinaryData.length - 4);
            sha512.digest(cache64, 0, 64);
            sha512.update(cache64, 0, 64);
            sha512.digest(cache64, 0, 64);
            System.arraycopy(cache64, 0, storedBinaryData, storedBinaryData.length - 4, 4);
            return "BM-" + Base58.encode(storedBinaryData);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (DigestException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @see https://github.com/Bitmessage/PyBitmessage/blob/6f35da4096770a668c4944c3024cd7ddb34be092/src/addresses.py#L143
     * @return
     */
    public static final String encodeAddress(int version, int stream, byte[] ripe) {
        return encodeAddress(version, stream, ripe, 20);
    }
}
