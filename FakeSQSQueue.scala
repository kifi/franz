package com.kifi.franz

import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsValue

import com.amazonaws.services.sqs.AmazonSQSAsync

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, ExecutionContext}
import scala.language.implicitConversions


trait FakeSQSQueue[T] extends SQSQueue[T] {

  protected val sqs: AmazonSQSAsync = null
  protected val createIfNotExists: Boolean = false
  val queue: QueueName = QueueName("fake")
  protected implicit def asString(obj: T): String = null
  protected implicit def fromString(s: String): T = null

  override def initQueueUrl(): String = ""

  override def send(msg: T)(implicit ec: ExecutionContext): Future[MessageId] = Future.successful(MessageId(""))

  override def nextBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Seq[SQSMessage[T]]] = Future.successful(Seq.empty)

}
