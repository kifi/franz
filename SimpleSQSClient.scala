package com.kifi.franz

import com.amazonaws.auth.{AWSCredentialsProvider, AWSCredentials}
import com.amazonaws.regions.{Regions, Region}
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient


class SimpleSQSClient(credentialProvider: AWSCredentialsProvider, region: Regions, buffered: Boolean) extends SQSClient {

  val _sqs = new AmazonSQSAsyncClient(credentialProvider)
  val sqs = if (buffered) new AmazonSQSBufferedAsyncClient(_sqs) else _sqs;
  sqs.setRegion(Region.getRegion(region))

  def apply(queue: QueueName): SQSQueue = {
    new SimpleSQSQueue(sqs, queue)
  }

  def simple(queue: QueueName): SQSQueue = {
    new SimpleSQSQueue(sqs, queue)
  }

  def formatted[T](queue: QueueName): FormattedSQSQueue[T] = {
    new SimpleFormattedSQSQueue[T](simple(queue))
  }

}


object SimpleSQSClient {

  def apply(credentials: AWSCredentials, region: Regions, buffered: Boolean = true) : SQSClient = {
    val credentialProvider = new AWSCredentialsProvider {
      def getCredentials() = credentials
      def refresh() = {}
    }
    new SimpleSQSClient(credentialProvider, region, buffered);
  }

  def apply(key: String, secret: String, region: Regions) : SQSClient = {
    val credentials = new AWSCredentials {
      def getAWSAccessKeyId() = key
      def getAWSSecretKey() = secret
    }
    this(credentials, region, true)
  }

}
