import sbt._
import com.lightbend.lagom.sbt.Lagom.autoImport._
import play.sbt.Play.autoImport.filters

object Dependencies {

  private object Versions {
    val derivedCodecs = "4.0.1"
    val logback       = "1.2.3"
    val macwire       = "2.3.0"
    val play          = "2.6.10"
    val scalaLogging  = "3.9.0"
    val scalacheck    = "1.14.0"
    val scalatest     = "3.0.5"
  }

  private val derivedCodecs = "org.julienrf"               %% "play-json-derived-codecs" % Versions.derivedCodecs
  private val logback       = "ch.qos.logback"             % "logback-classic"           % Versions.logback
  private val macwire       = "com.softwaremill.macwire"   %% "macros"                   % Versions.macwire % "provided"
  private val playJson      = "com.typesafe.play"          %% "play-json"                % Versions.play
  private val scalacheck    = "org.scalacheck"             %% "scalacheck"               % Versions.scalacheck % "test"
  private val scalactic     = "org.scalactic"              %% "scalactic"                % Versions.scalatest
  private val scalaLogging  = "com.typesafe.scala-logging" %% "scala-logging"            % Versions.scalaLogging
  private val scalatest     = "org.scalatest"              %% "scalatest"                % Versions.scalatest % "test"

  private val defaultApiDependencies = Seq(
    derivedCodecs,
    playJson,
    scalacheck,
    scalactic,
    scalatest,
    lagomScaladslApi
  )

  private val defaultImplDependencies = Seq(
    derivedCodecs,
    logback,
    macwire,
    filters,
    playJson,
    scalacheck,
    scalactic,
    scalatest,
    scalaLogging,
    lagomScaladslApi,
    lagomScaladslServer,
    lagomScaladslTestKit,
    lagomScaladslKafkaClient,
    lagomScaladslKafkaBroker,
    lagomScaladslPersistenceCassandra
  )

  val ticketApi: Seq[ModuleID]  = defaultApiDependencies
  val ticketImpl: Seq[ModuleID] = defaultImplDependencies

}
