package com.kifi.franz


trait SQSClient {
  def apply(queue: QueueName) : SQSQueue
}
