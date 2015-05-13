organization := "com.kifi"

name := "franz"

version := "0.3.10"

crossScalaVersions := Seq("2.10.4", "2.11.5")

scalaVersion := "2.10.4"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "com.amazonaws" % "aws-java-sdk" % "1.9.30"

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.3.7"

libraryDependencies += "com.typesafe.play" %% "play-iteratees" % "2.3.7"

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>https://github.com/FortyTwoEng/franz</url>
  <licenses>
    <license>
      <name>MIT</name>
      <url>http://opensource.org/licenses/MIT</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:FortyTwoEng/franz.git</url>
    <connection>scm:git:git@github.com:FortyTwoEng/franz.git</connection>
  </scm>
  <developers>
    <developer>
      <id>stkem</id>
      <name>Stephen Kemmerling</name>
      <url>https://github.com/stkem</url>
    </developer>
  </developers>)
