package com.kifi.franz

import com.amazonaws.services.sqs.AmazonSQSAsync

import scala.language.implicitConversions
import play.api.libs.json.{JsValue, Json, JsUndefined}

import scala.util.{Failure, Success, Try}



class SimpleSQSQueue(protected val sqs: AmazonSQSAsync, val queue: QueueName, protected val createIfNotExists: Boolean = false) extends SQSQueue[String] {
  protected implicit def asString(s: String) = s
  protected implicit def fromString(s: String) = s
}

class JsonSQSQueue(protected val sqs: AmazonSQSAsync, val queue: QueueName, protected val createIfNotExists: Boolean = false) extends SQSQueue[JsValue] {
  protected implicit def asString(json: JsValue) = Json.stringify(json)
  protected implicit def fromString(s: String) = {
    Try(Json.parse(s)) match {
      case Success(json) => json
      case Failure(e) => JsUndefined(e.getMessage)
    }
  }
}
