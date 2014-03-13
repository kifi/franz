package com.kifi.franz


trait SQSClient {
  def simple(queue: QueueName, createIfNotExists: Boolean=false): SQSQueue
  def formatted[T](queue: QueueName, createIfNotExists: Boolean=false): FormattedSQSQueue[T]
}
