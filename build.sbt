scalaVersion in ThisBuild := "2.12.6"

lazy val commonSettings = Seq(
  organization := "com.github.erip",
  parallelExecution in Test := false,
  coverageExcludedFiles := ".*Application.*"
)

lazy val ticketApi = (project in file("ticket/ticket-api"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Dependencies.ticketApi
  )

lazy val ticketImpl = (project in file("ticket/ticket-impl"))
  .settings(commonSettings)
  .enablePlugins(LagomScala)
  .settings(lagomForkedTestSettings)
  .settings(
    libraryDependencies ++= Dependencies.ticketImpl
  )
  .dependsOn(ticketApi)

lazy val root = (project in file("."))
  .aggregate(ticketApi, ticketImpl)