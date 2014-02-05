package stkem.franz


trait SQSClient {
  def apply(queue: QueueName) : SQSQueue
}
