package com.twitter.teruteru128.bitmessage.keygen;

import java.math.BigInteger;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.Callable;

import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.math.ec.ECPoint;

public class Producer implements Callable<Void> {

    private Request request;
    private final String string;

    public Producer(Request request) {
        this.request = request;
        string = "Task-" + request.getTaskID();
    }

    /* static const */
    private static final ECPoint G = CustomNamedCurves.getByName("secp256k1").getG();
    private static final int PRIVATE_KEY_LENGTH = 32;
    private static final int PUBLIC_KEY_LENGTH = 65;
    private static final int SHA512_DIGEST_LENGTH = 64;
    private static final int RIPEMD160_DIGEST_LENGTH = 20;

    /**
     * TODO コマンドライン引数を実装
     * 
     * コマンド
     * generate
     * validate
     * version
     * 
     * generate
     *  コマンドラインオプション
     *  スレッド数
     *  要求する短さ
     *  キャッシュサイズ(鍵)
     *  出力先
     *   標準出力
     *   ファイル
     * 
     * validate
     *  address
     *  signingPrivateKeyWIF
     *  encryptionPrivateKeyWIF
     *
     * @return
     * @throws NoSuchAlgorithmException
     * @throws DigestException
     * @see https://qiita.com/Syo_pr/items/92b3cf7d7fc5dab4a3a7#%E8%A1%8C%E5%88%97%E8%A1%8C%E5%88%97%E7%A9%8D%E3%81%AE%E6%9C%80%E9%81%A9%E5%8C%96
     */
    @Override
    public Void call() throws NoSuchAlgorithmException, DigestException {
        /* random source */
        final SecureRandom random = new SecureRandom();

        /* instance const */
        final int taskId = request.getTaskID();
        final int keyCacheSize = request.getKeyCacheSize();
        final int requireNlz = request.getNlzToRequest();

        /* working area */
        byte[] potentialPublicEncryptionKey = null;
        // XXX 生の鍵データを一つの配列に詰め込むのは本当に早いのか？
        final byte[] privateKeys = new byte[keyCacheSize * PRIVATE_KEY_LENGTH];
        final byte[] iPublicKey = new byte[PUBLIC_KEY_LENGTH];
        final byte[] jPublicKey = new byte[PUBLIC_KEY_LENGTH];
        final byte[] publicKeys = new byte[keyCacheSize * PUBLIC_KEY_LENGTH];
        final byte[] cache64 = new byte[SHA512_DIGEST_LENGTH];
        int nlz = 0;

        /* hash objects */
        final MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
        final MessageDigest ripemd160 = MessageDigest.getInstance("RIPEMD160");

        /* consumer queues */
        final LinkedList<Response> enqueueTray = new LinkedList<>();
        // TODO 一つのでかいテーブルを全スレッド協調して計算する -> ？
        // テーブルサイズを20万ぐらいにしてiをスレッドで分割する？
        // スレッド0 : 0 <= i < 5万
        // スレッド1 : 5万 <= i < 10万
        // スレッド2 : 10万 <= i < 15万
        // スレッド3 : 15万 <= i < 20万
        // 変なことやらせずに1スレッドに巨大テーブル処理させたほうが早い？
        LocalDateTime privateKeyCacheGenStart;
        LocalDateTime privateKeyCacheGenFinish;
        LocalDateTime publicKeyCacheGenFinish;
        LocalDateTime cacheFinish;
        while (true) {
            privateKeyCacheGenStart = LocalDateTime.now();
            // 秘密鍵を生成する
            // System.err.printf("hoge (%d) %s%n", request.getTaskID(), LocalDateTime.now());
            random.nextBytes(privateKeys);
            // System.err.printf("huga (%d) %s%n", request.getTaskID(), LocalDateTime.now());
            // 秘密鍵から対応する公開鍵を導出する
            for (int i = 0; i < keyCacheSize; i++) {
                // 公開鍵の導出は重い処理のため、予め大量に導出しておいて組み合わせたほうが早い
                potentialPublicEncryptionKey = G.multiply(new BigInteger(1, privateKeys, i * PRIVATE_KEY_LENGTH, PRIVATE_KEY_LENGTH)).normalize().getEncoded(false);
                System.arraycopy(potentialPublicEncryptionKey, 0, publicKeys, i * PUBLIC_KEY_LENGTH, PUBLIC_KEY_LENGTH);
            }
            // System.err.printf("piyo (%d) %s%n", request.getTaskID(), LocalDateTime.now());
            for (int i = 0; i < keyCacheSize; i++) {
                System.arraycopy(publicKeys, i * PUBLIC_KEY_LENGTH, iPublicKey, 0, PUBLIC_KEY_LENGTH);
                // 同じ鍵を使う場合は別に計算する
                // ここから
                // ripeを計算する
                sha512.update(iPublicKey, 0, PUBLIC_KEY_LENGTH);
                sha512.update(iPublicKey, 0, PUBLIC_KEY_LENGTH);
                sha512.digest(cache64, 0, SHA512_DIGEST_LENGTH);
                ripemd160.update(cache64, 0, SHA512_DIGEST_LENGTH);
                ripemd160.digest(cache64, 0, RIPEMD160_DIGEST_LENGTH);
                // number of leading zeroを計算する
                for (nlz = 0; cache64[nlz] == 0 && nlz < RIPEMD160_DIGEST_LENGTH; nlz++) {
                }
                // 計算したnlz結果が要求値より良好なら
                if (nlz >= requireNlz) {
                    // responseインスタンスを生成してエンキュー
                    byte[] signingPrivateKey = Arrays.copyOfRange(privateKeys, i * PRIVATE_KEY_LENGTH, (i + 1) * PRIVATE_KEY_LENGTH);
                    KeyPair signingKeyPair = new KeyPair(signingPrivateKey, Arrays.copyOf(iPublicKey, PUBLIC_KEY_LENGTH));
                    byte[] encryptionPrivateKey = Arrays.copyOfRange(privateKeys, i * PRIVATE_KEY_LENGTH, (i + 1) * PRIVATE_KEY_LENGTH);
                    KeyPair encryptionKeyPair = new KeyPair(encryptionPrivateKey, Arrays.copyOf(iPublicKey, PUBLIC_KEY_LENGTH));
                    var response = new Response(signingKeyPair, encryptionKeyPair, Arrays.copyOf(cache64, RIPEMD160_DIGEST_LENGTH));
                    //System.err.printf("keypair found!(%d) %s%n", request.getTaskID(), LocalDateTime.now());
                    try {
                        Queues.getResponseQueue().put(response);
                    } catch (InterruptedException e) {
                        System.err.println("enqueue failed!");
                        e.printStackTrace();
                        // Save if failed
                        enqueueTray.add(response);
                    }
                }
                // ここまで
                for (int j = 0; j < i; j++) {
                    System.arraycopy(publicKeys, j * PUBLIC_KEY_LENGTH, jPublicKey, 0, PUBLIC_KEY_LENGTH);
                    // XXX 変数生成処理をやらせないために1メソッドにベタ打ちしてるんだが、スタック領域に変数を生成/削除するのってそれなりに重い処理なのか？
                    // staticメソッドはメソッドをインライン展開してくれるらしい
                    // ここから
                    // ripeを計算する
                    sha512.update(iPublicKey, 0, PUBLIC_KEY_LENGTH);
                    sha512.update(jPublicKey, 0, PUBLIC_KEY_LENGTH);
                    sha512.digest(cache64, 0, SHA512_DIGEST_LENGTH);
                    ripemd160.update(cache64, 0, SHA512_DIGEST_LENGTH);
                    ripemd160.digest(cache64, 0, RIPEMD160_DIGEST_LENGTH);
                    // number of leading zeroを計算する
                    for (nlz = 0; cache64[nlz] == 0 && nlz < RIPEMD160_DIGEST_LENGTH; nlz++) {
                    }
                    // 計算したnlz結果が要求値より良好なら
                    if (nlz >= requireNlz) {
                        // responseインスタンスを生成してエンキュー
                        byte[] signingPrivateKey = Arrays.copyOfRange(privateKeys, i * PRIVATE_KEY_LENGTH, (i + 1) * PRIVATE_KEY_LENGTH);
                        KeyPair signingKeyPair = new KeyPair(signingPrivateKey, Arrays.copyOf(iPublicKey, PUBLIC_KEY_LENGTH));
                        byte[] encryptionPrivateKey = Arrays.copyOfRange(privateKeys, j * PRIVATE_KEY_LENGTH, (j + 1) * PRIVATE_KEY_LENGTH);
                        KeyPair encryptionKeyPair = new KeyPair(encryptionPrivateKey, Arrays.copyOf(jPublicKey, PUBLIC_KEY_LENGTH));
                        var response = new Response(signingKeyPair, encryptionKeyPair, Arrays.copyOf(cache64, RIPEMD160_DIGEST_LENGTH));
                        //System.err.printf("keypair found!(%d) %s%n", request.getTaskID(), LocalDateTime.now());
                        try {
                            Queues.getResponseQueue().put(response);
                        } catch (InterruptedException e) {
                            System.err.println("enqueue failed!");
                            e.printStackTrace();
                            // Save if failed
                            enqueueTray.add(response);
                        }
                    }
                    // ここまで
                    // ここから
                    // ripeを計算する
                    sha512.update(jPublicKey, 0, PUBLIC_KEY_LENGTH);
                    sha512.update(iPublicKey, 0, PUBLIC_KEY_LENGTH);
                    sha512.digest(cache64, 0, SHA512_DIGEST_LENGTH);
                    ripemd160.update(cache64, 0, SHA512_DIGEST_LENGTH);
                    ripemd160.digest(cache64, 0, RIPEMD160_DIGEST_LENGTH);
                    // number of leading zeroを計算する
                    for (nlz = 0; cache64[nlz] == 0 && nlz < RIPEMD160_DIGEST_LENGTH; nlz++) {
                    }
                    // 計算したnlz結果が要求値より良好なら
                    if (nlz >= requireNlz) {
                        // responseインスタンスを生成してエンキュー
                        byte[] signingPrivateKey = Arrays.copyOfRange(privateKeys, j * PRIVATE_KEY_LENGTH, (j + 1) * PRIVATE_KEY_LENGTH);
                        KeyPair signingKeyPair = new KeyPair(signingPrivateKey, Arrays.copyOf(jPublicKey, PUBLIC_KEY_LENGTH));
                        byte[] encryptionPrivateKey = Arrays.copyOfRange(privateKeys, i * PRIVATE_KEY_LENGTH, (i + 1) * PRIVATE_KEY_LENGTH);
                        KeyPair encryptionKeyPair = new KeyPair(encryptionPrivateKey, Arrays.copyOf(iPublicKey, PUBLIC_KEY_LENGTH));
                        var response = new Response(signingKeyPair, encryptionKeyPair, Arrays.copyOf(cache64, 20));
                        //System.err.printf("keypair found!(%d) %s%n", request.getTaskID(), LocalDateTime.now());
                        try {
                            Queues.getResponseQueue().put(response);
                        } catch (InterruptedException e) {
                            System.err.println("enqueue failed!");
                            e.printStackTrace();
                            enqueueTray.add(response);
                        }
                    }
                    // ここまで
                }
            }
            if (!enqueueTray.isEmpty()) {
                ListIterator<Response> responses = enqueueTray.listIterator();
                while (responses.hasNext()) {
                    Response response = responses.next();
                    try {
                        Queues.getResponseQueue().put(response);
                        // remove after enqueue
                        responses.remove();
                    } catch (InterruptedException e) {
                        // skip
                    }
                }
            }
            System.err.printf("hogera (%d) %s%n", request.getTaskID(), LocalDateTime.now());
        }
    }

    @Override
    public String toString() {
        return string;
    }
}
