package com.kifi.franz

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.{SendMessageRequest, GetQueueUrlRequest, ReceiveMessageRequest, DeleteMessageRequest}

import play.api.libs.json.{JsValue, Json}
import play.api.libs.iteratee.Enumerator

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.collection.JavaConverters._






class SimpleSQSQueue(sqs: AmazonSQS, queue: QueueName) extends SQSQueue {

  val queueUrl: String = sqs.getQueueUrl(new GetQueueUrlRequest(queue.name)).getQueueUrl

  def send(msg: String)(implicit ec: ExecutionContext): Future[MessageId] = Future {
    val request = new SendMessageRequest
    request.setMessageBody(msg)
    request.setQueueUrl(queueUrl)
    MessageId(sqs.sendMessage(request).getMessageId)
  }

  def send(msg: JsValue)(implicit ec: ExecutionContext): Future[MessageId] = send(Json.stringify(msg))


  def nextStringBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Seq[SQSStringMessage]] = Future {
    val request = new ReceiveMessageRequest
    request.setMaxNumberOfMessages(1)
    request.setVisibilityTimeout(lockTimeout.toSeconds.toInt)
    request.setWaitTimeSeconds(86400)
    request.setQueueUrl(queueUrl)
    val response = sqs.receiveMessage(request)
    val rawMessages = response.getMessages()
    if (rawMessages.isEmpty) throw QueueReadTimeoutException
    rawMessages.asScala.map { rawMessage =>
      SQSStringMessage(rawMessage.getBody(), {() =>
        val request = new DeleteMessageRequest
        request.setQueueUrl(queueUrl)
        request.setReceiptHandle(rawMessage.getReceiptHandle)
        sqs.deleteMessage(request)
      })
    }
  }

  def nextStringBatch(maxBatchSize: Int)(implicit ec: ExecutionContext): Future[Seq[SQSStringMessage]] = nextStringBatchWithLock(maxBatchSize, new FiniteDuration(0, SECONDS))

  def nextStringWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[SQSStringMessage] = nextStringBatchWithLock(1, lockTimeout).map(_(0))

  def nextString(implicit ec: ExecutionContext): Future[SQSStringMessage] = nextStringBatch(1).map(_(0))


  def nextJsonBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Seq[SQSJsonMessage]] = {
    nextStringBatchWithLock(maxBatchSize, lockTimeout).map{ stringMessageSeq =>
      stringMessageSeq.map{ stringMessage =>
        SQSJsonMessage(Json.parse(stringMessage.body), stringMessage.consume)
      }
    }
  }

  def nextJsonBatch(maxBatchSize: Int)(implicit ec: ExecutionContext): Future[Seq[SQSJsonMessage]] = nextJsonBatchWithLock(maxBatchSize, new FiniteDuration(0, SECONDS))

  def nextJsonWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[SQSJsonMessage] = nextJsonBatchWithLock(1, lockTimeout).map(_(0))

  def nextJson(implicit ec: ExecutionContext): Future[SQSJsonMessage] = nextJsonBatch(1).map(_(0))


  def stringEnumerator(implicit ec: ExecutionContext) : Enumerator[SQSStringMessage] = Enumerator.fromCallback1[SQSStringMessage]{ (_) =>
    nextString.map(Some(_))
  }

  def stringEnumeratorWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Enumerator[SQSStringMessage] = Enumerator.fromCallback1[SQSStringMessage]{ (_) =>
    nextStringWithLock(lockTimeout).map(Some(_))
  }

  def jsonEnumerator(implicit ec: ExecutionContext): Enumerator[SQSJsonMessage] = Enumerator.fromCallback1[SQSJsonMessage]{ (_) =>
    nextJson.map(Some(_))
  }

  def jsonEnumeratorWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Enumerator[SQSJsonMessage] = Enumerator.fromCallback1[SQSJsonMessage]{ (_) =>
    nextJsonWithLock(lockTimeout).map(Some(_))
  }


}
