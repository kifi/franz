package com.kifi.franz

import com.amazonaws.services.sqs.AmazonSQSAsync

import scala.language.implicitConversions

class SimpleSQSQueue(protected val sqs: AmazonSQSAsync, val queue: QueueName, protected val createIfNotExists: Boolean = false, endpointOverride:Option[String] = None) extends SQSQueue[String] {
  protected implicit def asString(s: String) = s
  protected implicit def fromString(s: String) = s
}