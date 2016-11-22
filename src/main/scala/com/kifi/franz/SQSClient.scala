package com.kifi.franz

import scala.concurrent.{Future, ExecutionContext}

trait SQSClient {
  def simple(queue: QueueName, createIfNotExists: Boolean=false, endpointOverride:Option[String] = None): SQSQueue[String]
  def delete(queue: QueueName): Future[Boolean]
  def deleteByPrefix(queuePrefix: String)(implicit executor: ExecutionContext): Future[Int]
  def shutdown()
}

