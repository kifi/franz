package com.kifi.franz

import com.amazonaws.services.sqs.AmazonSQSAsync

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future
import scala.language.implicitConversions


trait FakeSQSQueue[T] extends SQSQueue[T] {

  protected val sqs: AmazonSQSAsync = null
  protected val createIfNotExists: Boolean = false
  val queue: QueueName = QueueName("fake")
  protected implicit def asString(obj: T): String = null
  protected implicit def fromString(s: String): T = null

  override def initQueueUrl(): String = ""

  override def send(msg: T): Future[MessageId] = Future.successful(MessageId(""))

  override protected def nextBatchRequestWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration): Future[Seq[SQSMessage[T]]] = Future.successful(Seq.empty)

}
