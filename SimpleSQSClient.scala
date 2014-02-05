package com.kifi.franz

import com.amazonaws.auth.{AWSCredentialsProvider, AWSCredentials}
import com.amazonaws.regions.{Regions, Region}
import com.amazonaws.services.sqs.AmazonSQSClient


class SimpleSQSClient(credentialProvider: AWSCredentialsProvider, region: Regions) extends SQSClient {

  val sqs = new AmazonSQSClient(credentialProvider)
  sqs.setRegion(Region.getRegion(region))

  def apply(queue: QueueName) : SQSQueue = {
    new SimpleSQSQueue(sqs, queue)
  }

}


object SimpleSQSClient {

  def apply(credentials: AWSCredentials, region: Regions) : SQSClient = {
    val credentialProvider = new AWSCredentialsProvider {
      def getCredentials() = credentials
      def refresh() = {}
    }
    new SimpleSQSClient(credentialProvider, region)
  }

  def apply(key: String, secret: String, region: Regions) : SQSClient = {
    val credentials = new AWSCredentials {
      def getAWSAccessKeyId() = key
      def getAWSSecretKey() = secret
    }
    this(credentials, region)
  }

}
