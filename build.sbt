// Common dependencies
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

lazy val shapeless = {
    val shapelessV = "2.3.3"
    Seq(
        "com.chuusai" %% "shapeless" % shapelessV
    )
}

lazy val commonDependencies = testing.map(_ % Test) ++ logging ++ shapeless

// Eff tagless
lazy val effTagless = {
    val effVersion = "4.5.0"
    Seq(
        "org.atnos" %% "eff" % effVersion excludeAll(ExclusionRule(organization = "org.typelevel"))
    )
}

// Cats tagless
lazy val cats = {
    Seq(
        "org.typelevel" %% "cats-core",
        "org.typelevel" %% "cats-kernel",
        "org.typelevel" %% "cats-macros"
    )
}

lazy val catsEffect = Seq(
  "org.typelevel" %% "cats-effect"
)

lazy val catsFree = Seq(
  "org.typelevel" %% "cats-free"
)

lazy val catsTagless = {
  val catsTaglessVersion = "0.9"
  Seq(
    "org.typelevel" %% "cats-tagless-core" % catsTaglessVersion,
    "org.typelevel" %% "cats-tagless-macros" % catsTaglessVersion
  )
}

lazy val EffTaglessFinalExample = project
  .settings(
    name := "EffTaglessFinalExample",
    version := "0.1",
    scalaVersion := "2.12.9",
    libraryDependencies ++= commonDependencies
      ++ cats.map(_ % "0.9.0")
      ++ effTagless,
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

lazy val CatsTaglessFinalExample = project
        .settings(
            name := "CatsTaglessFinalExample",
            version := "0.1",
            scalaVersion := "2.12.9",
            libraryDependencies ++= commonDependencies
              ++ (cats ++ catsEffect ++ catsFree).map(_ % "2.0.0-RC2")
              ++ catsTagless,
            scalacOptions ++= Seq(
                "-language:higherKinds",
                "-deprecation",
                "-encoding", "UTF-8",
                "-feature",
                "-language:_",
                "-Ypartial-unification"
            ),
            addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4"),
            addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
        )
