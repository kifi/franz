package com.kifi.franz

import play.api.libs.json.{JsValue, Format}
import scala.concurrent.Future

trait SQSClient {
  def simple(queue: QueueName, createIfNotExists: Boolean=false): SQSQueue[String]
  def json(queue: QueueName, createIfNotExists: Boolean=false): SQSQueue[JsValue]
  def formatted[T](queue: QueueName, createIfNotExists: Boolean=false)(implicit format: Format[T]): SQSQueue[T]
  def delete(queue: QueueName): Future[Boolean]
}

