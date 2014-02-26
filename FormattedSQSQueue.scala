package com.kifi.franz


import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{Format, Json, Writes, Reads}

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.{FiniteDuration, SECONDS}



trait FormattedSQSQueue[T]  {
  def send(msg: T)(implicit ec: ExecutionContext, f: Writes[T]): Future[MessageId]

  def next(implicit ec: ExecutionContext, f: Reads[T]): Future[Option[SQSMessage[T]]]
  def nextWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext, f: Reads[T]): Future[Option[SQSMessage[T]]]
  def nextBatch(maxBatchSize: Int)(implicit ec: ExecutionContext, f: Reads[T]): Future[Seq[SQSMessage[T]]]
  def nextBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext, f: Reads[T]): Future[Seq[SQSMessage[T]]]

  def enumerator(implicit ec: ExecutionContext, f: Reads[T]): Enumerator[SQSMessage[T]]
  def enumeratorWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext, f: Reads[T]): Enumerator[SQSMessage[T]]
}


class SimpleFormattedSQSQueue[T](baseQueue: SQSQueue) extends FormattedSQSQueue[T] {

  def send(msg: T)(implicit ec: ExecutionContext, f: Writes[T]): Future[MessageId] = baseQueue.send(Json.toJson(msg))

  def next(implicit ec: ExecutionContext, f: Reads[T]): Future[Option[SQSMessage[T]]] = nextBatchWithLock(1, new FiniteDuration(0, SECONDS)).map(_.headOption)

  def nextWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext, f: Reads[T]): Future[Option[SQSMessage[T]]] = nextBatchWithLock(1, lockTimeout).map(_.headOption)

  def nextBatch(maxBatchSize: Int)(implicit ec: ExecutionContext, f: Reads[T]): Future[Seq[SQSMessage[T]]] = nextBatchWithLock(1, new FiniteDuration(0, SECONDS))

  def nextBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext, f: Reads[T]): Future[Seq[SQSMessage[T]]] = {
    baseQueue.nextJsonBatchWithLock(maxBatchSize, lockTimeout).map(_.map{ jsonMessage =>
      new SQSMessage[T] {
        val body = jsonMessage.body.as[T]
        val consume = jsonMessage.consume
        val attributes = jsonMessage.attributes
      }
    })
  }

  def enumerator(implicit ec: ExecutionContext, f: Reads[T]) : Enumerator[SQSMessage[T]] = Enumerator.fromCallback1[SQSMessage[T]]{ (_) =>
    next
  }
  def enumeratorWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext, f: Reads[T]): Enumerator[SQSMessage[T]] = Enumerator.fromCallback1[SQSMessage[T]]{ (_) =>
    nextWithLock(lockTimeout)
  }

}
