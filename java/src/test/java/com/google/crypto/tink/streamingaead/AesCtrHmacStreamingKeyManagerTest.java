// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.streamingaead;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.crypto.tink.StreamingAead;
import com.google.crypto.tink.TestUtil;
import com.google.crypto.tink.TestUtil.ByteBufferChannel;
import com.google.crypto.tink.proto.AesCtrHmacStreamingKey;
import com.google.crypto.tink.proto.AesCtrHmacStreamingKeyFormat;
import com.google.crypto.tink.proto.AesCtrHmacStreamingParams;
import com.google.crypto.tink.proto.HashType;
import com.google.crypto.tink.proto.HmacParams;
import com.google.crypto.tink.proto.KeyData;
import com.google.crypto.tink.subtle.Random;
import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.GeneralSecurityException;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for AesCtrHmacStreamingKeyManager. */
@RunWith(JUnit4.class)
public class AesCtrHmacStreamingKeyManagerTest {
  private static final int AES_KEY_SIZE = 16;
  private HmacParams hmacParams;
  private AesCtrHmacStreamingParams keyParams;
  private AesCtrHmacStreamingKeyManager keyManager;

  @Before
  public void setUp() throws GeneralSecurityException {
    hmacParams = HmacParams.newBuilder().setHash(HashType.SHA256).setTagSize(16).build();
    keyParams =
        AesCtrHmacStreamingParams.newBuilder()
            .setCiphertextSegmentSize(128)
            .setDerivedKeySize(AES_KEY_SIZE)
            .setHkdfHashType(HashType.SHA256)
            .setHmacParams(hmacParams)
            .build();
    keyManager = new AesCtrHmacStreamingKeyManager();
  }

  /** Tests encryption and decryption functionalities of {@code streamingAead}. */
  private void testEncryptionAndDecryption(StreamingAead streamingAead) throws Exception {
    byte[] aad = Random.randBytes(15);
    // Short plaintext.
    byte[] shortPlaintext = Random.randBytes(10);
    testEncryptionAndDecryption(streamingAead, shortPlaintext, aad);
    // Long plaintext.
    byte[] longPlaintext = Random.randBytes(1100);
    testEncryptionAndDecryption(streamingAead, longPlaintext, aad);
  }

  /**
   * Tests encryption and decryption functionalities of {@code streamingAead} using {@code
   * plaintext} and {@code aad}.
   */
  private void testEncryptionAndDecryption(
      StreamingAead streamingAead, byte[] plaintext, byte[] aad) throws Exception {
    // Encrypt plaintext.
    ByteArrayOutputStream ciphertext = new ByteArrayOutputStream();
    WritableByteChannel encChannel =
        streamingAead.newEncryptingChannel(Channels.newChannel(ciphertext), aad);
    encChannel.write(ByteBuffer.wrap(plaintext));
    encChannel.close();

    // Decrypt ciphertext.
    ByteBufferChannel ciphertextChannel = new ByteBufferChannel(ciphertext.toByteArray());
    SeekableByteChannel decChannel =
        streamingAead.newSeekableDecryptingChannel(ciphertextChannel, aad);
    ByteBuffer decrypted = ByteBuffer.allocate(plaintext.length);
    int readCount = decChannel.read(decrypted);

    // Compare results;
    assertEquals(plaintext.length, readCount);
    assertArrayEquals(plaintext, decrypted.array());
  }

  @Test
  public void testBasic() throws Exception {
    // Create primitive from a given key.
    AesCtrHmacStreamingKey key =
        AesCtrHmacStreamingKey.newBuilder()
            .setVersion(0)
            .setKeyValue(ByteString.copyFrom(Random.randBytes(20)))
            .setParams(keyParams)
            .build();
    StreamingAead streamingAead = keyManager.getPrimitive(key);
    testEncryptionAndDecryption(streamingAead);

    // Create a key from KeyFormat, and use the key.
    AesCtrHmacStreamingKeyFormat keyFormat =
        AesCtrHmacStreamingKeyFormat.newBuilder().setParams(keyParams).setKeySize(16).build();
    ByteString serializedKeyFormat = ByteString.copyFrom(keyFormat.toByteArray());
    key = (AesCtrHmacStreamingKey) keyManager.newKey(serializedKeyFormat);
    streamingAead = keyManager.getPrimitive(key);
    testEncryptionAndDecryption(streamingAead);
  }

  @Test
  public void testNewKeyMultipleTimes() throws Exception {
    AesCtrHmacStreamingKeyFormat keyFormat =
        AesCtrHmacStreamingKeyFormat.newBuilder().setParams(keyParams).setKeySize(16).build();
    ByteString serializedKeyFormat = ByteString.copyFrom(keyFormat.toByteArray());
    Set<String> keys = new TreeSet<String>();
    // Calls newKey multiple times and make sure that they generate different keys.
    int numTests = 27;
    for (int i = 0; i < numTests / 3; i++) {
      AesCtrHmacStreamingKey key = (AesCtrHmacStreamingKey) keyManager.newKey(keyFormat);
      keys.add(TestUtil.hexEncode(key.getKeyValue().toByteArray()));
      assertEquals(16, key.getKeyValue().toByteArray().length);

      key = (AesCtrHmacStreamingKey) keyManager.newKey(serializedKeyFormat);
      keys.add(TestUtil.hexEncode(key.getKeyValue().toByteArray()));
      assertEquals(16, key.getKeyValue().toByteArray().length);

      KeyData keyData = keyManager.newKeyData(serializedKeyFormat);
      key = AesCtrHmacStreamingKey.parseFrom(keyData.getValue());
      keys.add(TestUtil.hexEncode(key.getKeyValue().toByteArray()));
      assertEquals(16, key.getKeyValue().toByteArray().length);
    }
    assertEquals(numTests, keys.size());
  }

  @Test
  public void testNewKeyWithBadFormat() throws Exception {
    // key_size too small.
    AesCtrHmacStreamingKeyFormat keyFormat =
        AesCtrHmacStreamingKeyFormat.newBuilder().setParams(keyParams).setKeySize(15).build();
    ByteString serializedKeyFormat = ByteString.copyFrom(keyFormat.toByteArray());
    try {
      keyManager.newKey(keyFormat);
      fail("Bad format, should have thrown exception");
    } catch (GeneralSecurityException expected) {
      // Expected
    }
    try {
      keyManager.newKeyData(serializedKeyFormat);
      fail("Bad format, should have thrown exception");
    } catch (GeneralSecurityException expected) {
      // Expected
    }

    // Unsupported HKDF HashType.
    AesCtrHmacStreamingParams badKeyParams =
        AesCtrHmacStreamingParams.newBuilder()
            .setCiphertextSegmentSize(128)
            .setDerivedKeySize(AES_KEY_SIZE)
            .setHkdfHashType(HashType.SHA512)
            .build();
    keyFormat =
        AesCtrHmacStreamingKeyFormat.newBuilder().setParams(badKeyParams).setKeySize(16).build();
    serializedKeyFormat = ByteString.copyFrom(keyFormat.toByteArray());
    try {
      keyManager.newKey(keyFormat);
      fail("Bad format, should have thrown exception");
    } catch (GeneralSecurityException expected) {
      // Expected
    }
    try {
      keyManager.newKeyData(serializedKeyFormat);
      fail("Bad format, should have thrown exception");
    } catch (GeneralSecurityException expected) {
      // Expected
    }

    // derived_key_size too small.
    badKeyParams =
        AesCtrHmacStreamingParams.newBuilder()
            .setCiphertextSegmentSize(128)
            .setDerivedKeySize(10)
            .setHkdfHashType(HashType.SHA256)
            .build();
    keyFormat =
        AesCtrHmacStreamingKeyFormat.newBuilder().setParams(badKeyParams).setKeySize(16).build();
    serializedKeyFormat = ByteString.copyFrom(keyFormat.toByteArray());
    try {
      keyManager.newKey(keyFormat);
      fail("Bad format, should have thrown exception");
    } catch (GeneralSecurityException expected) {
      // Expected
    }
    try {
      keyManager.newKeyData(serializedKeyFormat);
      fail("Bad format, should have thrown exception");
    } catch (GeneralSecurityException expected) {
      // Expected
    }

    // ciphertext_segment_size too small.
    badKeyParams =
        AesCtrHmacStreamingParams.newBuilder()
            .setCiphertextSegmentSize(15)
            .setDerivedKeySize(AES_KEY_SIZE)
            .setHkdfHashType(HashType.SHA256)
            .build();
    keyFormat =
        AesCtrHmacStreamingKeyFormat.newBuilder().setParams(badKeyParams).setKeySize(16).build();
    serializedKeyFormat = ByteString.copyFrom(keyFormat.toByteArray());
    try {
      keyManager.newKey(keyFormat);
      fail("Bad format, should have thrown exception");
    } catch (GeneralSecurityException expected) {
      // Expected
    }
    try {
      keyManager.newKeyData(serializedKeyFormat);
      fail("Bad format, should have thrown exception");
    } catch (GeneralSecurityException expected) {
      // Expected
    }

    // All params good.
    AesCtrHmacStreamingParams goodKeyParams =
        AesCtrHmacStreamingParams.newBuilder()
            .setCiphertextSegmentSize(130)
            .setDerivedKeySize(AES_KEY_SIZE)
            .setHkdfHashType(HashType.SHA256)
            .setHmacParams(hmacParams)
            .build();
    keyFormat =
        AesCtrHmacStreamingKeyFormat.newBuilder().setParams(goodKeyParams).setKeySize(16).build();
    serializedKeyFormat = ByteString.copyFrom(keyFormat.toByteArray());
    AesCtrHmacStreamingKey unusedKey = (AesCtrHmacStreamingKey) keyManager.newKey(keyFormat);
    unusedKey = (AesCtrHmacStreamingKey) keyManager.newKey(serializedKeyFormat);
  }
}
