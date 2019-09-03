// DEPENDENCIES
lazy val testing = Seq(
    "org.scalatest" %% "scalatest" % "3.0.8"
)

lazy val logging ={
    val logbackV = "1.2.3"
    val scalaLoggingV = "3.9.2"
    Seq(
        "ch.qos.logback" % "logback-classic" % logbackV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV
    )
}

lazy val effTagless = {
    val shapelessV = "2.3.3"
    val EffV = "4.5.0"
    Seq(
        "com.chuusai" %% "shapeless" % shapelessV,
        "org.atnos" %% "eff" % EffV
    )
}

lazy val EffTaglessFinalExample = project
        .settings(
            name := "EffTaglessFinalExample",
            version := "0.1",
            scalaVersion := "2.12.9",
            libraryDependencies ++= testing.map(_ % Test) ++ logging ++ effTagless,
            scalacOptions ++= Seq(
                "-language:higherKinds",
                "-deprecation",
                "-encoding", "UTF-8",
                "-feature",
                "-language:_",
                "-Ypartial-unification"
            ),
            addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")
            //addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
        )
