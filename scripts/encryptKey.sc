#!/usr/bin/env amm

// Ammonite script to encrypt private role keys for the key server database,
// needs the environment variables DB_ENCRYPTION_PASSWORD and DB_ENCRYPTION_SALT from key server,
// see https://confluence.in.here.com/pages/viewpage.action?pageId=972552231.

import $ivy.`org.bouncycastle:bcprov-jdk18on:1.66`

import java.security.Security
import java.util.Base64
import javax.crypto.{Cipher, SecretKeyFactory}
import javax.crypto.spec.{PBEKeySpec, PBEParameterSpec}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import scala.io.Source

// from libats-crypto
class SlickCrypto(salt: Array[Byte], password: String) {
  private lazy val pbeParameterSpec = new PBEParameterSpec(salt, 1000)
  private val BC = BouncyCastleProvider.PROVIDER_NAME
  private lazy val CIPHER_ALGORITHM = "AES/GCM/NoPadding"

  private lazy val pbeKey = {
    val pbeKeySpec = new PBEKeySpec(password.toCharArray)
    val keyFac = SecretKeyFactory.getInstance("PBEWithSHA256And256BitAES-CBC-BC", BC)
    keyFac.generateSecret(pbeKeySpec)
  }

  def decrypt(str: String): String = {
    val bytes = Base64.getDecoder.decode(str)

    val encryptionCipher = Cipher.getInstance(CIPHER_ALGORITHM, BC)
    encryptionCipher.init(Cipher.DECRYPT_MODE, pbeKey, pbeParameterSpec)

    val plainTextBytes = encryptionCipher.doFinal(bytes)

    new String(plainTextBytes)
  }

  def encrypt(plainText: String): String = {
    val encryptionCipher = Cipher.getInstance(CIPHER_ALGORITHM, BC)
    encryptionCipher.init(Cipher.ENCRYPT_MODE, pbeKey, pbeParameterSpec)

    val ciphered = encryptionCipher.doFinal(plainText.getBytes())

    Base64.getEncoder.encodeToString(ciphered)
  }

}

Security.addProvider(new BouncyCastleProvider)

val password = sys.env("DB_ENCRYPTION_PASSWORD")
val saltBase64String = sys.env("DB_ENCRYPTION_SALT")

val saltBytes = Base64.getDecoder.decode(saltBase64String.getBytes)
val crypto = new SlickCrypto(saltBytes, password)

@main
def main(secretKeyFilename: String) =
  println(crypto.encrypt(Source.fromFile(secretKeyFilename).getLines.mkString))
