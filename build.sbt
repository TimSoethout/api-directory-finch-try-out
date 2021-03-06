name := "lonely-planet-scala"
version := "0.0.0"
scalaVersion := "2.11.7"

// scalacOptions += "-Xlog-implicits"

resolvers += Resolver.sonatypeRepo("snapshots")

resolvers += "TM" at "http://maven.twttr.com"

libraryDependencies ++= Seq(
  "com.github.finagle" %% "finch-core" % "0.9.2",
  "com.github.finagle" %% "finch-circe" % "0.9.2",
  "io.circe" %% "circe-generic" % "0.2.1",
  "com.twitter" %% "twitter-server" % "1.12.0",
  "com.twitter" %% "finagle-stats" % "6.27.0",
//  "com.cj" %% "kafka-rx" % "0.2.0",
//  "com.github.okapies" % "finagle-kafka_2.10" % "0.2.0",
  "com.softwaremill" %% "reactive-kafka" % "0.7.0",
  "info.batey.kafka" % "kafka-unit" % "0.2"
)

initialCommands in console :=
  """
    |import io.finch.request._
    |import io.finch.response._
    |import io.finch.route._
    |import io.finch.circe._
    |import io.circe.generic.auto._
    |import com.twitter.finagle.Httpx
    |import com.twitter.finagle.Service
    |import com.twitter.finagle.httpx.{Request, Response}
    |import com.twitter.util.{Future, Await}
  """.stripMargin
