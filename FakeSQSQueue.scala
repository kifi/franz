package com.kifi.franz

import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsValue


import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, ExecutionContext}


class FakeSQSQueue extends SQSQueue {


  def send(msg: String)(implicit ec: ExecutionContext): Future[MessageId] = Future.successful(MessageId(""))
  def send(msg: JsValue)(implicit ec: ExecutionContext): Future[MessageId] = Future.successful(MessageId(""))

  def nextString(implicit ec: ExecutionContext): Future[Option[SQSStringMessage]] = Future.successful(None)
  def nextStringWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Option[SQSStringMessage]] = Future.successful(None)
  def nextStringBatch(maxBatchSize: Int)(implicit ec: ExecutionContext): Future[Seq[SQSStringMessage]] = Future.successful(Seq.empty)
  def nextStringBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Seq[SQSStringMessage]] = Future.successful(Seq.empty)

  def nextJson(implicit ec: ExecutionContext): Future[Option[SQSJsonMessage]] = Future.successful(None)
  def nextJsonWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Option[SQSJsonMessage]] = Future.successful(None)
  def nextJsonBatch(maxBatchSize: Int)(implicit ec: ExecutionContext): Future[Seq[SQSJsonMessage]] = Future.successful(Seq.empty)
  def nextJsonBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Seq[SQSJsonMessage]] = Future.successful(Seq.empty)

  def stringEnumerator(implicit ec: ExecutionContext) : Enumerator[SQSStringMessage] = Enumerator.empty
  def stringEnumeratorWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Enumerator[SQSStringMessage] = Enumerator.empty

  def jsonEnumerator(implicit ec: ExecutionContext): Enumerator[SQSJsonMessage] = Enumerator.empty
  def jsonEnumeratorWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Enumerator[SQSJsonMessage] = Enumerator.empty
}
