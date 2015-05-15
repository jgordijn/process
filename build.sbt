import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._

lazy val root = (project in file(".")).
  settings(
    organization := "processframework",
    name := "process",
    scalaVersion := "2.11.6",
    publishMavenStyle := true,
    pomExtra := (
      <url>https://github.com/jgordijn/process</url>
            <licenses>
          <license>
          <name>Apache 2.0</name>
          <url>http://opensource.org/licenses/Apache-2.0</url>
            <distribution>repo</distribution>
          </license>
          </licenses>
          <scm>
        <url>git@github.com:jgordijn/process.git</url>
        <connection>scm:git:git@github.com:jgordijn/process.git</connection>
          </scm>
          <developers>
          <developer>
          <id>jgordijn</id>
          <name>Jeroen Gordijn</name>
            </developer>
          </developers>)
  )
  .settings(bintrayPublishSettings:_*)

licenses += ("Apache-2.0", url("http://opensource.org/licenses/Apache-2.0"))

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += Resolver.jcenterRepo

releaseSettings

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 90)
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(PreserveDanglingCloseParenthesis, true)
    .setPreference(RewriteArrowSymbols, true)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
  "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.9",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9" % "test",
  "com.typesafe.akka" %% "akka-contrib" % "2.3.9",
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"
)
