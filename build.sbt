
name := "franz"

version := "0.1.0"

scalaVersion := "2.10.3"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test"

libraryDependencies += "com.amazonaws" % "aws-java-sdk" % "1.6.12"

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.2.1"

libraryDependencies += "com.typesafe.play" %% "play-iteratees" % "2.2.1"
