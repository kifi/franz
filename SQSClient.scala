package com.fortytwo.franz


trait SQSClient {
  def apply(queue: QueueName) : SQSQueue
}
