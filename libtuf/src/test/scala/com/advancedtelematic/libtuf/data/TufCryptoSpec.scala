package com.advancedtelematic.libtuf.data

import cats.data.Validated.Valid
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.TufDataType.{
  Ed25519TufKey,
  Ed25519TufKeyPair,
  Ed25519TufPrivateKey,
  KeyType,
  SignedPayload
}
import io.circe.Json

class TufCryptoSpec extends LibtufSpec {
  val json = Json.obj()

  val key01 = TufCrypto.generateKeyPair(KeyType.default, KeyType.default.crypto.defaultKeySize)
  val key02 = TufCrypto.generateKeyPair(KeyType.default, KeyType.default.crypto.defaultKeySize)

  val sig01 = TufCrypto.signPayload(key01.privkey, json)
  val sig02 = TufCrypto.signPayload(key02.privkey, json)

  test(
    "1 valid signature is enough with threshold = 1 and the public key for one of the signatures is missing"
  ) {
    val signedPayload = SignedPayload(
      List(sig01.toClient(key01.pubkey.id), sig02.toClient(key02.pubkey.id)),
      json,
      json
    )

    val keyMap = Map(key01.pubkey.id -> key01.pubkey)

    TufCrypto.payloadSignatureIsValid(keyMap, threshold = 1, signedPayload) shouldBe a[Valid[_]]
  }

  test("2 valid signatures are required when threshold = 2") {
    val signedPayload = SignedPayload(
      List(sig01.toClient(key01.pubkey.id), sig02.toClient(key02.pubkey.id)),
      json,
      json
    )

    val keyMap = List(key01.pubkey, key02.pubkey).map(k => k.id -> k).toMap

    TufCrypto.payloadSignatureIsValid(keyMap, threshold = 2, signedPayload) shouldBe a[Valid[_]]
  }

  test("Ed25519 encoding/decoding") {
    import TufCrypto.ed25519Crypto._

    val keyPair = generateKeyPair().asInstanceOf[Ed25519TufKeyPair]
    val priv = keyPair.privkey
    val pub = keyPair.pubkey

    parsePublic(encode(pub)).get shouldBe pub
    parsePrivate(encode(priv)).get shouldBe priv
  }

}
