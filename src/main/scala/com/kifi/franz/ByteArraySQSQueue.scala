package com.kifi.franz

import com.amazonaws.services.sqs.AmazonSQSAsync
import scala.language.implicitConversions

class ByteArraySQSQueue(protected val sqs: AmazonSQSAsync, val queue: QueueName, protected val createIfNotExists: Boolean = false ) extends SQSQueue[Array[Byte]] {

  private lazy val base64Encoder = java.util.Base64.getEncoder
  private lazy val base64Decoder = java.util.Base64.getDecoder

  protected implicit def asString(data: Array[Byte]): String = new String(base64Encoder.encode(data))

  protected implicit def fromString(data: String): Array[Byte] = base64Decoder.decode(data)
}
