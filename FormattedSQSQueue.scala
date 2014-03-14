package com.kifi.franz

import com.amazonaws.services.sqs.AmazonSQSAsync

import play.api.libs.json.{Format, Json}

import scala.language.implicitConversions


class FormattedSQSQueue[T](
    protected val sqs: AmazonSQSAsync,
    protected val queue: QueueName,
    protected val createIfNotExists: Boolean = false,
    format: Format[T]
  ) extends SQSQueue[T] {

  protected implicit def asString(obj: T) = Json.stringify(Json.toJson(obj)(format))
  protected implicit def fromString(s: String) = Json.parse(s).as[T](format)
}



