__Franz__ is a simple reactive-ish Scala wrapper around the Amazons SQS persitent message queue service.

#Initialization
First you will need an instance of the trait ```SQSClient```. The only currently available implementation is ```SimpleSQSClient```, which has three constructors

```scala
new SimpleSQSClient(
	credentialProvider: com.amazonaws.auth.AWSCredentialsProvider,
	region: com.amazonaws.regions.Regions,
	buffered: Boolean
)

SimpleSQSClient(
	credentials: com.amazonaws.auth.AWSCredentials,
	region: com.amazonaws.regions.Regions,
	buffered: Boolean=false
)

SimpleSQSClient(key: String, secret: String, region: com.amazonaws.regions.Regions)
```

(Warning: Be careful when using `buffered=true`. It can improve performance, but it's buggy. Use at your own risk.)

Let's use the third.

```scala
import com.amazonaws.regions.Regions
val sqs = SimpleSQSClient(<your aws access key>, <your aws secret key>, Regions.US_WEST_1)
```

We'll come back to how to actually get a queue from the client shortly.


#SQSQueue
The type you'll be using to actually interact with an SQS Queue is ```SQSQueue[T]```. It provides all the primitives for sending and receiving messages.

##Sending
```SQSQueue[T]``` provides one method for sending messages:

```scala
def send(msg: T)(implicit ec: ExecutionContext): Future[MessageId]
```

There is no current use for the returned ```MessageId```, but you can use the success of the Future as a send confimation.

If you need to pass one or more [SQS message attributes](http://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/SQSMessageAttributes.html) along with the message, provide a ```Map[String,String]``` to the optional messageAttributes.

```scala
def send(msg: T, messageAttributes: Option[Map[String, String])(implicit ec: ExecutionContext): Future[MessageId]
```

##Receiving

###Direct
```SQSQueue``` provides several methods for getting the next message in the queue

```scala
def next(implicit ec: ExecutionContext): Future[Option[SQSMessage[T]]]
def nextBatch(maxBatchSize: Int)(implicit ec: ExecutionContext): Future[Seq[SQSMessage[T]]]
def nextWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Option[SQSMessage[T]]]
def nextBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Seq[SQSMessage[T]]]
```
The returned ```SQSMessage[T]``` objects have the fields
```scala
val body: T //actual message payload
val attributes: Map[String,String] //raw attributes from com.amazonaws.services.sqs.model.Message
val consume: () => Unit //deletes the message from the queue
```
and the method
```scala
def consume[K](block: T => K): K
```
Which will call ```consume``` if no exception is thrown so you can do either
```scala
    processMyEvent(sqsMessage.body)
    sqsMessage.consume()
```
or
```scala
    sqsMessage.consume { body =>
        processMyEvent(body)
    }
```

The ```*WithLock``` methods lock (or rather, hide) the retrieved message(s) in the queue so that no other call will retrieve them during the lock timeout. You need to call ```consume``` on the message before the timeout expires in order to permanently remove it form the queue.

If the lock expires the message will again be available for retrieval, which is useful e.g. in case of an error when consume was never called.

The implementation uses 20 second long polls behind the scenes. If no message was available within that time a ```None``` or ```Seq.empty``` will be returned (depending on the method used).
Note that due to the distributed and eventually consistent nature of SQS it is sometimes possible to get an empty response even if there are some (but few) messages in the queue if you happen to poll an empty node. The best practice solution to that is continuos retries, i.e. you'll make 3 requests per mintue.

###Iteratees
For the more functionally inclined ```SQSQueue[T]``` also provides enumerators to be used with your favorite Iteratee

```scala
def enumerator(implicit ec: ExecutionContext): Enumerator[SQSMessage[T]]
def enumeratorWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Enumerator[SQSMessage[T]]
```

The semantics of retrievel and locking are identical to those of the ```next*``` methods.

#Getting a Queue

```SQSClient``` currently has three methods for getting a specific queue

```scala
def simple(queue: QueueName, createIfNotExists: Boolean=false): SQSQueue[String]
def json(queue: QueueName, createIfNotExists: Boolean=false): SQSQueue[JsValue]
def formatted[T](queue: QueueName, createIfNotExists: Boolean=false)(implicit format: Format[T]): SQSQueue[T]
```

Where ```Format[T]``` and ```JsValue``` are form ```play.api.libs.json```. ```QueueName``` is simply a typed wrapper around a string, which should be the full queue name (*not* the queue url).

#SQS Limitations

- Fairly high latency. Not really suitable for things that require immediate action.
- Message size is limited to ~64KB.
- FIFO not guaranteed for messages sent close together. (i.e. there is no strict ordering of messages)
- Multicasting is somewhat cumbersome (could be done through [SNS fanout](https://aws.amazon.com/blogs/aws/queues-and-notifications-now-best-friends/)).
- No replay. Once a message is consumed, it's gone.

#Installation

You can get Franz from maven central. The artifact is `franz_2.10` or `franz_2.11` and the group id is `com.kifi`.
The current version is `0.3.10`. For example, if you are using __sbt__, just add this to your dependencies:

```
"com.kifi" % "franz_2.11" % "0.3.13"
```

To add a dependency that matches your scala version (2.10.x or 2.11.x), use

```
"com.kifi" %% "franz" % "0.3.10"
```

All classes are in in `com.kifi.franz`.

#See Also

Kifi's Reactive Scala Wrapper for Amazon SQS [blog post](http://eng.kifi.com/reactive-scala-wrapper-for-amazon-sqs/)
