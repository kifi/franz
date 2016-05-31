package com.kifi.franz

import play.api.libs.iteratee.Enumerator

import scala.concurrent.{Future, ExecutionContext, Promise}
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.collection.JavaConverters._
import scala.language.implicitConversions

import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model._
import com.amazonaws.handlers.AsyncHandler

case class QueueName(name: String)

case class MessageId(id: String)

case class SQSMessage[T](
  id: MessageId,
  body: T,
  consume: () => Unit,
  setVisibilityTimeout: (FiniteDuration) => Unit,
  attributes: Map[String,String],
  messageAttributes: Map[String, MessageAttributeValue]) {

  def consume[K](block: T => K): K = {
    val returnValue = block(body)
    consume()
    returnValue
  }
}

trait SQSQueue[T]{

  val queue: QueueName

  protected val sqs: AmazonSQSAsync
  protected val createIfNotExists: Boolean
  protected implicit def asString(obj: T): String
  protected implicit def fromString(s: String): T

  protected val queueUrl: String = initQueueUrl()
  protected def initQueueUrl() = {
    try {
      sqs.getQueueUrl(new GetQueueUrlRequest(queue.name)).getQueueUrl
    } catch {
      case t: com.amazonaws.services.sqs.model.QueueDoesNotExistException if createIfNotExists => sqs.createQueue(new CreateQueueRequest(queue.name)).getQueueUrl
      case t: Throwable => throw t
    }
  }

  protected def stringMessageAttribute( attributeValue: String ): MessageAttributeValue = {
    val attr = new MessageAttributeValue()
    attr.setDataType("String")
    attr.setStringValue(attributeValue)
    attr
  }

  def send(msg: T ): Future[MessageId] = {
    send (msg, None)
  }

  def send(msg: T, delay:Int): Future[MessageId] = {
    send (msg, None, Some(delay))
  }

  def send(msg: T, messageAttributes: Option[Map[String, String]] = None, delay:Option[Int] = None): Future[MessageId] = {
    val request = new SendMessageRequest
    request.setMessageBody(msg)
    request.setQueueUrl(queueUrl)
    delay.map{ d =>
      request.setDelaySeconds(d)
    }
    // foreach on an Option unfolds Some, and skips if None
    messageAttributes.foreach { ma =>
      ma.foreach { case (k,v) =>
        request.addMessageAttributesEntry(k, stringMessageAttribute(v))
      }
    }

    val p = Promise[MessageId]()
    sqs.sendMessageAsync(request, new AsyncHandler[SendMessageRequest,SendMessageResult]{
      def onError(exception: Exception) = p.failure(exception)
      def onSuccess(req: SendMessageRequest, res: SendMessageResult) = p.success(MessageId(res.getMessageId))
    })
    p.future
  }

  def sendBatch(msg: Seq[(T, Option[Map[String, String]])], delay: Option[Int] = None): Future[(Seq[MessageId],Seq[MessageId])] = {
    if ( msg.size > 10) {
      throw new IllegalArgumentException("sendBatch can not take more then 10 items")
    }
    val request = new SendMessageBatchRequest()
    request.setQueueUrl(queueUrl)
    val entries = msg.map { case (message, attributes) =>
      val entry = new SendMessageBatchRequestEntry()
      delay.foreach(entry.setDelaySeconds(_))
      attributes.foreach(m => m.foreach { case (k, v) =>
        entry.addMessageAttributesEntry(k, stringMessageAttribute(v))
      })
      entry.setMessageBody(message)
      entry
    }
    request.setEntries(entries.asJavaCollection)
    val p = Promise[(Seq[MessageId], Seq[MessageId])]()
    sqs.sendMessageBatchAsync(request, new AsyncHandler[SendMessageBatchRequest,SendMessageBatchResult]{
      def onError(exception: Exception) = p.failure(exception)
      def onSuccess(req: SendMessageBatchRequest, res: SendMessageBatchResult) = p.success((res.getSuccessful.asScala.map(m => MessageId(m.getMessageId)), res.getFailed.asScala.map(m => MessageId(m.getId))))
    })
    p.future

  }

   def attributes(attributeNames:Seq[String]):Future[Map[String,String]]={
    val request = new GetQueueAttributesRequest()
    request.setQueueUrl(queueUrl)
    import scala.collection.JavaConversions._
    request.setAttributeNames(attributeNames)

    val p = Promise[Map[String,String]]()
    sqs.getQueueAttributesAsync(request, new AsyncHandler[GetQueueAttributesRequest, GetQueueAttributesResult]{
      def onError(exception: Exception) = p.failure(exception)
      def onSuccess(req: GetQueueAttributesRequest, response: GetQueueAttributesResult) = {
        try {
          val rawMessages = response.getAttributes
          p.success(rawMessages.asScala.toMap)
        } catch {
          case t: Throwable => p.failure(t)
        }
      }
    })
    p.future
  }

  protected def nextBatchRequestWithLock(requestMaxBatchSize: Int, lockTimeout: FiniteDuration): Future[Seq[SQSMessage[T]]] = {
    val request = new ReceiveMessageRequest
    request.setMaxNumberOfMessages(requestMaxBatchSize)
    request.setVisibilityTimeout(lockTimeout.toSeconds.toInt)
    request.setWaitTimeSeconds(10)
    request.setQueueUrl(queueUrl)
    request.withMessageAttributeNames("All")
    request.withAttributeNames("All")

    val p = Promise[Seq[SQSMessage[T]]]()
    sqs.receiveMessageAsync(request, new AsyncHandler[ReceiveMessageRequest, ReceiveMessageResult]{
      def onError(exception: Exception) = p.failure(exception)
      def onSuccess(req: ReceiveMessageRequest, response: ReceiveMessageResult) = {
        try {
          val rawMessages = response.getMessages
          p.success(rawMessages.asScala.map { rawMessage =>
            SQSMessage[T](
              id = MessageId(rawMessage.getMessageId),
              body = rawMessage.getBody,
              consume = {() =>
                val request = new DeleteMessageRequest
                request.setQueueUrl(queueUrl)
                request.setReceiptHandle(rawMessage.getReceiptHandle)
                sqs.deleteMessageAsync(request)
              },
              setVisibilityTimeout = {(timeout: FiniteDuration) =>
                val request = (new ChangeMessageVisibilityRequest)
                  .withQueueUrl(queueUrl)
                  .withReceiptHandle(rawMessage.getReceiptHandle)
                  .withVisibilityTimeout(timeout.toSeconds.toInt)
                sqs.changeMessageVisibilityAsync(request)
              },
              attributes = rawMessage.getAttributes.asScala.toMap,
              messageAttributes = rawMessage.getMessageAttributes.asScala.toMap)
          })
        } catch {
          case t: Throwable => p.failure(t)
        }
      }
    })
    p.future
  }


  def nextBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Seq[SQSMessage[T]]] = {
    val maxBatchSizePerRequest = 10
    val requiredBatchRequests = Seq.fill(maxBatchSize / maxBatchSizePerRequest)(maxBatchSizePerRequest) :+ (maxBatchSize % maxBatchSizePerRequest)
    val futureBatches = requiredBatchRequests.collect {
      case requestMaxBatchSize if requestMaxBatchSize > 0 => nextBatchRequestWithLock(requestMaxBatchSize, lockTimeout)
    }
    Future.sequence(futureBatches).map { batches =>
      val messages =  batches.flatten
      val distinctMessages = messages.map { message => message.id -> message }.toMap.values
      distinctMessages.toSeq
    }
  }

  def next(implicit ec: ExecutionContext): Future[Option[SQSMessage[T]]] = nextBatchRequestWithLock(1, new FiniteDuration(0, SECONDS)).map(_.headOption)
  def nextWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Option[SQSMessage[T]]] = nextBatchRequestWithLock(1, lockTimeout).map(_.headOption)
  def nextBatch(maxBatchSize: Int)(implicit ec: ExecutionContext): Future[Seq[SQSMessage[T]]] = nextBatchWithLock(maxBatchSize, new FiniteDuration(0, SECONDS))
  def enumerator(implicit ec: ExecutionContext): Enumerator[SQSMessage[T]] = Enumerator.repeatM[SQSMessage[T]]{ loopFuture(next) }
  def enumeratorWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Enumerator[SQSMessage[T]] = Enumerator.repeatM[SQSMessage[T]]{ loopFuture(nextWithLock(lockTimeout)) }
  def batchEnumerator(maxBatchSize:Int)(implicit ec: ExecutionContext): Enumerator[Seq[SQSMessage[T]]] = Enumerator.repeatM[Seq[SQSMessage[T]]]{ loopFutureBatch(nextBatch(maxBatchSize)) }
  def batchEnumeratorWithLock(maxBatchSize:Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Enumerator[Seq[SQSMessage[T]]] = Enumerator.repeatM[Seq[SQSMessage[T]]]{ loopFutureBatch(nextBatchWithLock(maxBatchSize, lockTimeout)) }

  private def loopFuture[A](f: => Future[Option[A]], promise: Promise[A] = Promise[A]())(implicit ec: ExecutionContext): Future[A] = {
    f.onComplete {
      case util.Success(Some(res)) => promise.success(res)
      case util.Success(None) => loopFuture(f, promise)
      case util.Failure(ex) => promise.failure(ex)
    }
    promise.future
  }

  private def loopFutureBatch[A](f: => Future[Seq[A]], promise: Promise[Seq[A]] = Promise[Seq[A]]())(implicit ec: ExecutionContext): Future[Seq[A]] = {
    f.onComplete {
      case util.Success(res) if res.nonEmpty => promise.success(res)
      case util.Success(res) if res.isEmpty => loopFutureBatch(f, promise)
      case util.Failure(ex) => promise.failure(ex)
    }
    promise.future
  }

}

