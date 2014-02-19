package com.kifi.franz


trait SQSClient {
  def apply(queue: QueueName) : SQSQueue
  def simple(queue: QueueName): SQSQueue
  def formatted[T](queue: QueueName): FormattedSQSQueue[T]
}
