__Franz__ is a simple reactive-ish Scala wrapper around Amazons SQS persitent message queue.

#Initialization
First you will need an instance of the trait ```SQSClient```. The only currently available implementation is ```SimpleSQSClient```, which has three constructors

```scala
new SimpleSQSClient(
	credentialProvider: com.amazonaws.auth.AWSCredentialsProvider, 
	region: com.amazonaws.regions.Regions
)

SimpleSQSClient(credentials: com.amazonaws.auth.AWSCredentials, region: com.amazonaws.regions.Regions)

SimpleSQSClient(key: String, secret: String, region: com.amazonaws.regions.Regions)
```

Let's use the third.

```scala
import com.amazonaws.regions.Regions
val sqs = SimpleSQSClient(<your aws access key>, <your aws secret key>, Regions.US_WEST_1)
```

Now that we have an instance of ```SQSClient``` we can get ourselves an instance of ```SQSQueue``` like so

```scala
val queue = sqs.simple(QueueName(<your queue name>))
```


#SQSQueue 


##Sending
```SQSQueue``` provides two methods for sending messages:

```scala
def send(msg: String)(implicit ec: ExecutionContext): Future[MessageId]
def send(msg: play.api.libs.json.JsValue)(implicit ec: ExecutionContext): Future[MessageId]
```

There is no current use for the returned ```MessageId```, but you can use the success of the Future as a send confimation.


##Receiving

###Direct
```SQSQueue``` provides several methods for getting the next message in the queue

```scala
def nextString(implicit ec: ExecutionContext): Future[Option[SQSStringMessage]]
def nextStringWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Option[SQSStringMessage]]
def nextStringBatch(maxBatchSize: Int)(implicit ec: ExecutionContext): Future[Seq[SQSStringMessage]]
def nextStringBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Seq[SQSStringMessage]]

def nextJson(implicit ec: ExecutionContext): Future[Option[SQSJsonMessage]]
def nextJsonWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Option[SQSJsonMessage]]
def nextJsonBatch(maxBatchSize: Int)(implicit ec: ExecutionContext): Future[Seq[SQSJsonMessage]]
def nextJsonBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Seq[SQSJsonMessage]]
```

The returned ```SQS*Message``` object are instances of ```SQSMessage[String]``` and ```SQSMessage[play.api.libs.json.JsValue]``` respectively. ```SQSMessage[T]``` has the fields
```scala
val body: T //actual message payload
val attributes: Map[String,String] //raw attributes from com.amazonaws.services.sqs.model.Message
val consume: () => Unit //deletes the message from the queue
```
 
The ```*WithLock``` methods lock (or rather, hide) the retrieved message(s) in the queue so that no other call will retrieve them during the lock timeout. You need to call ```consume``` on the message before the timeout expires in order to permanently remove it form the queue.

If the lock expires the message will again be available for retrieval, which is useful e.g. in case of an error when cosume was never called.

A call to retrieve a message (batch) will long poll for 10 seconds. If there is nothing to fetch an empty sequence or a ```None``` will be returned, depending on the method.

###Iteratees
For the more functionally inclined ```SQSQueue``` also provides enumerators to be used with your favorite Iteratee
```scala
def stringEnumerator(implicit ec: ExecutionContext) : Enumerator[SQSStringMessage]
def stringEnumeratorWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Enumerator[SQSStringMessage]

def jsonEnumerator(implicit ec: ExecutionContext): Enumerator[SQSJsonMessage]
def jsonEnumeratorWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Enumerator[SQSJsonMessage]
```

The semantics of retrievel and locking are identical to those of the ```next*``` methods.

#FormattedSQSQueue
In addition to ```SQSQueue``` __Franz__ also has a ```FormattedSQSQueue[T]```, which has the signature 

```scala
def send(msg: T)(implicit ec: ExecutionContext, f: Writes[T]): Future[MessageId]
def next(implicit ec: ExecutionContext, f: Reads[T]): Future[Option[SQSMessage[T]]]
def nextWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext, f: Reads[T]): Future[Option[SQSMessage[T]]]
def nextBatch(maxBatchSize: Int)(implicit ec: ExecutionContext, f: Reads[T]): Future[Seq[SQSMessage[T]]]
def nextBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext, f: Reads[T]): Future[Seq[SQSMessage[T]]]
def enumerator(implicit ec: ExecutionContext, f: Reads[T]): Enumerator[SQSMessage[T]]
def enumeratorWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext, f: Reads[T]): Enumerator[SQSMessage[T]]
``` 

The ```Reads[T]``` and ```Writes[T]``` are from ```play.api.libs.json``` and this basically allows you to have a queue for any type with a play style implicit json formatter. The semantics of the methods here are identical to the ```*Json*``` methods on ```SQSQueue```, except that serialization/deserilization is taken care of for you.


#Limitations

- Fairly high latency. Not really suitable for things that require immediate action.
- Message size is limited to ~64KB.
- FIFO not guaranteed for messages sent close together. (i.e. there is no strict ordering of messages)
- Multicasting is really cumbersome.
- No replay. Once a message is consumed, it's gone.
