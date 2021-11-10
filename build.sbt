val http4sVersion = "0.21.11"
val circeVersion = "0.13.0"
val catsVersion = "2.2.0"
val catsEffectVersion = "2.2.0"
val refinedVersion = "0.9.18"
val enumeratumVersion = "1.6.1"
val pprintVersion = "0.5.6"
val TofuVersion = "0.10.6"

val LogbackVersion = "1.2.3"
val scalaTestVersion = "3.1.0.0-RC2"

//addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.1" cross CrossVersion.full)

lazy val root = (project in file("."))
  .settings(
    name := "MixzPoker",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.6",
    scalacOptions += "-Wunused:imports",
    // so that case classes with private constructors
    // will have private .apply and .copy like in Scala 3
    scalacOptions += "-Xsource:3",
    scalacOptions += "-Ymacro-annotations",
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-blaze-server" % http4sVersion,
      "org.http4s"      %% "http4s-blaze-client" % http4sVersion,
      "org.http4s"      %% "http4s-circe"        % http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % http4sVersion,

      "io.circe"        %% "circe-generic"       % circeVersion,
      "io.circe"        %% "circe-generic"       % circeVersion,
      "io.circe"        %% "circe-parser"        % circeVersion,
      "io.circe"        %% "circe-refined"       % circeVersion,

      "org.typelevel"   %% "cats-core"           % catsVersion,
      "org.typelevel"   %% "cats-effect"         % catsEffectVersion,

      "eu.timepit"      %% "refined"             % refinedVersion,

      "com.beachape"    %% "enumeratum"          % enumeratumVersion,

      "com.lihaoyi"     %% "pprint"              % pprintVersion,

      "ch.qos.logback"  %  "logback-classic"     % LogbackVersion,

      "tf.tofu"         %% "tofu"                    % TofuVersion,
      "tf.tofu"         %% "tofu-logging"            % TofuVersion,
      "tf.tofu"         %% "tofu-logging-derivation" % TofuVersion,
      "tf.tofu"         %% "tofu-kernel-ce2-interop" % TofuVersion,

      "org.scalatestplus" %% "scalatestplus-scalacheck" % scalaTestVersion % Test,
      "org.scalatestplus" %% "selenium-2-45"            % scalaTestVersion % Test
    )
  )
