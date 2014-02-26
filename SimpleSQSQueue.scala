package com.kifi.franz

import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.{
  SendMessageRequest,
  GetQueueUrlRequest,
  ReceiveMessageRequest,
  DeleteMessageRequest,
  SendMessageResult,
  ReceiveMessageResult
}
import com.amazonaws.handlers.AsyncHandler

import play.api.libs.json.{JsValue, Json}
import play.api.libs.iteratee.Enumerator

import scala.concurrent.{Future, ExecutionContext, Promise}
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.collection.JavaConverters._






class SimpleSQSQueue(sqs: AmazonSQSAsync, queue: QueueName) extends SQSQueue {

  val queueUrl: String = sqs.getQueueUrl(new GetQueueUrlRequest(queue.name)).getQueueUrl

  def send(msg: String)(implicit ec: ExecutionContext): Future[MessageId] = {
    val request = new SendMessageRequest
    request.setMessageBody(msg)
    request.setQueueUrl(queueUrl)
    val p = Promise[MessageId]
    sqs.sendMessageAsync(request, new AsyncHandler[SendMessageRequest,SendMessageResult]{
      def onError(exception: Exception) = p.failure(exception)
      def onSuccess(req: SendMessageRequest, res: SendMessageResult) = p.success(MessageId(res.getMessageId))
    })
    p.future
  }

  def send(msg: JsValue)(implicit ec: ExecutionContext): Future[MessageId] = send(Json.stringify(msg))


  def nextStringBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Seq[SQSStringMessage]] = {
    val request = new ReceiveMessageRequest
    request.setMaxNumberOfMessages(1)
    request.setVisibilityTimeout(lockTimeout.toSeconds.toInt)
    request.setWaitTimeSeconds(10)
    request.setQueueUrl(queueUrl)
    val p = Promise[Seq[SQSStringMessage]]
    sqs.receiveMessageAsync(request, new AsyncHandler[ReceiveMessageRequest, ReceiveMessageResult]{
      def onError(exception: Exception) = p.failure(exception)
      def onSuccess(req: ReceiveMessageRequest, response: ReceiveMessageResult) = {
        val rawMessages = response.getMessages()
        p.success(rawMessages.asScala.map { rawMessage =>
          SQSStringMessage(rawMessage.getBody(), {() =>
            val request = new DeleteMessageRequest
            request.setQueueUrl(queueUrl)
            request.setReceiptHandle(rawMessage.getReceiptHandle)
            sqs.deleteMessageAsync(request)
          }, rawMessage.getAttributes().asScala.toMap)
        })
      }
    })
    p.future
  }

  def nextStringBatch(maxBatchSize: Int)(implicit ec: ExecutionContext): Future[Seq[SQSStringMessage]] = nextStringBatchWithLock(maxBatchSize, new FiniteDuration(0, SECONDS))

  def nextStringWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Option[SQSStringMessage]] = nextStringBatchWithLock(1, lockTimeout).map(_.headOption)

  def nextString(implicit ec: ExecutionContext): Future[Option[SQSStringMessage]] = nextStringBatch(1).map(_.headOption)


  def nextJsonBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Seq[SQSJsonMessage]] = {
    nextStringBatchWithLock(maxBatchSize, lockTimeout).map{ stringMessageSeq =>
      stringMessageSeq.map{ stringMessage =>
        SQSJsonMessage(Json.parse(stringMessage.body), stringMessage.consume, stringMessage.attributes)
      }
    }
  }

  def nextJsonBatch(maxBatchSize: Int)(implicit ec: ExecutionContext): Future[Seq[SQSJsonMessage]] = nextJsonBatchWithLock(maxBatchSize, new FiniteDuration(0, SECONDS))

  def nextJsonWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Option[SQSJsonMessage]] = nextJsonBatchWithLock(1, lockTimeout).map(_.headOption)

  def nextJson(implicit ec: ExecutionContext): Future[Option[SQSJsonMessage]] = nextJsonBatch(1).map(_.headOption)


  def stringEnumerator(implicit ec: ExecutionContext) : Enumerator[SQSStringMessage] = Enumerator.fromCallback1[SQSStringMessage]{ (_) =>
    nextString
  }

  def stringEnumeratorWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Enumerator[SQSStringMessage] = Enumerator.fromCallback1[SQSStringMessage]{ (_) =>
    nextStringWithLock(lockTimeout)
  }

  def jsonEnumerator(implicit ec: ExecutionContext): Enumerator[SQSJsonMessage] = Enumerator.fromCallback1[SQSJsonMessage]{ (_) =>
    nextJson
  }

  def jsonEnumeratorWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Enumerator[SQSJsonMessage] = Enumerator.fromCallback1[SQSJsonMessage]{ (_) =>
    nextJsonWithLock(lockTimeout)
  }


}
