name := "My Project"
 
version := "1.0"
 
scalaVersion := "2.10.1"
 
resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "FuseSource Community Snapshot Repository" at "http://repo.fusesource.com/nexus/content/groups/public-snapshots"

resolvers += "spray repo" at "http://nightlies.spray.io"

resolvers += "org.fusesource.lmdbjni" at "file://Users/corydobson/Documents/java_workspace/My Project"

resolvers += "spray" at "http://repo.spray.io/"


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-contrib" % "2.2.0",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.0",
  "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
  "org.fusesource.lmdbjni" % "lmdbjni-all" % "99-master-SNAPSHOT",
  "io.spray" % "spray-can" % "1.2-20130710",
  "io.spray" %%  "spray-json" % "1.2.5")

atmosSettings

fork := true