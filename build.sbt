import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._

lazy val root = (project in file(".")).
  settings(
    organization := "processframework",
    name := "process",
    scalaVersion := "2.11.8",
    publishMavenStyle := true,
    pomExtra := <url>https://github.com/jgordijn/process</url>
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
      </developers>
  )

licenses += ("Apache-2.0", url("http://opensource.org/licenses/Apache-2.0"))

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += Resolver.jcenterRepo

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 90)
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(DanglingCloseParenthesis, Preserve)
    .setPreference(RewriteArrowSymbols, true)

val akkaVersion       = "2.4.9"
val scalaTestVersion  = "3.0.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka"   %%    "akka-actor"                % akkaVersion,
  "com.typesafe.akka"   %%    "akka-persistence" 	        % akkaVersion,
  "com.typesafe.akka"   %%    "akka-contrib"              % akkaVersion,
  "com.typesafe.akka"   %%    "akka-testkit"              % akkaVersion         % "test",
  "org.scalatest"       %     "scalatest_2.11"            % scalaTestVersion    % "test"
)
