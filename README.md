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
val queue = sqs(QueueName(<your queue name>))
```


#Sending
```SQSQueue``` provides two methods for sending messages:

```scala
def send(msg: String)(implicit ec: ExecutionContext): Future[MessageId]
def send(msg: play.api.libs.json.JsValue)(implicit ec: ExecutionContext): Future[MessageId]
```

There is no current use for the returned ```MessageId```, but you can use the success of the Future as a send confimation.


#Receiving

###Direct
```SQSQueue``` provides several methods for getting the next message in the queue

```scala
def nextString(implicit ec: ExecutionContext): Future[SQSStringMessage]
def nextStringWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[SQSStringMessage]
def nextStringBatch(maxBatchSize: Int)(implicit ec: ExecutionContext): Future[Seq[SQSStringMessage]]
def nextStringBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Seq[SQSStringMessage]]

def nextJson(implicit ec: ExecutionContext): Future[SQSJsonMessage]
def nextJsonWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[SQSJsonMessage]
def nextJsonBatch(maxBatchSize: Int)(implicit ec: ExecutionContext): Future[Seq[SQSJsonMessage]]
def nextJsonBatchWithLock(maxBatchSize: Int, lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Seq[SQSJsonMessage]]
```

The returned ```SQS*Message``` objects have one field ```body```  - which is either a ```String``` or ```play.api.libs.json.JsValue``` depending on the type - and one method ```consume : () => Unit``` which deletes the message from the queue (and thus prevents others from reading it).
 
The ```*WithLock``` methods lock (or rather, hide) the retrieved message(s) in the queue so that no other call will retrieve them during the lock timeout. You need to call ```consume``` on the message before the timeout expires in order to permanently remove it form the queue.

If the lock expires the message will again be available for retrieval, which is useful e.g. in case of an error when cosume was never called.


A ```QueueReadTimeoutException``` is thrown if a request is pending for a very long time (~24h, currently).

###Iteratees
For the more functionally inclined ```SQSQueue``` also provides enumerators to be used with your favorite Iteratee
```scala
def stringEnumerator(implicit ec: ExecutionContext) : Enumerator[SQSStringMessage]
def stringEnumeratorWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Enumerator[SQSStringMessage]

def jsonEnumerator(implicit ec: ExecutionContext): Enumerator[SQSJsonMessage]
def jsonEnumeratorWithLock(lockTimeout: FiniteDuration)(implicit ec: ExecutionContext): Enumerator[SQSJsonMessage]
```

The semantics of retrievel and locking are identical to those of the ```next*``` methods.


#Limitations

- Fairly high latency. Not really suitable for things that require immediate action.
- Message size is limited to ~64KB.
- FIFO not guaranteed for messages sent close together. (i.e. there is no strict ordering of messages)
- Multicasting is really cumbersome.
- No replay. Once a message is consumed, it's gone.
