ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots") // for mdoc (see also plugins.sbt)
//ThisBuild / resolvers += Resolver.githubPackages("uosis")
ThisBuild / scalaVersion := Versions.Scala_2_13

import Versions._

//addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.1" cross CrossVersion.full)

lazy val backend = project
  .in(file("backend"))
  .settings(
    name := "MixzPokerBackend",
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


lazy val frontend = project
  .in(file("frontend"))
  .withId("frontend")
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .settings(
    name := "MixzPokerFrontend",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.6",
    scalaJSLinkerConfig ~= {_.withModuleKind(ModuleKind.CommonJSModule)},
    // Producing source maps throws warnings on material web components complaining about missing .ts files. Not sure why.
    scalaJSLinkerConfig ~= {_.withSourceMap(false)},
    scalaJSUseMainModuleInitializer := true,
    (Compile / npmDependencies) ++= Seq(
      "@material/mwc-button" -> MwcVersion,
      "@material/mwc-linear-progress" -> MwcVersion,
      "@material/mwc-slider" -> MwcVersion,
      "@material/mwc-top-app-bar-fixed" -> MwcVersion,
      "@material/mwc-top-app-bar" -> MwcVersion,
      "@material/mwc-menu" -> MwcVersion,
      "@material/mwc-list" -> MwcVersion,
      "@material/mwc-icon-button" -> MwcVersion,
      "@material/mwc-select" -> MwcVersion,
      "@material/mwc-tab" -> MwcVersion,
      "@material/mwc-tab-bar" -> MwcVersion,
      "@material/mwc-icon" -> MwcVersion,
      "@material/mwc-fab" -> MwcVersion,
      "@material/mwc-snackbar" -> MwcVersion,
      "@material/mwc-formfield" -> MwcVersion,
      "@material/mwc-textfield" -> MwcVersion,
      "@material/mwc-dialog" -> MwcVersion,
    ),
    scalacOptions ~= { options: Seq[String] =>
      options.filterNot { o =>
        o.startsWith("-Wvalue-discard") || o.startsWith("-Ywarn-value-discard") || o.startsWith("-Ywarn-unused") || o.startsWith("-Wunused")
      }
    },
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom"   % ScalaJsDom,
      "com.raquo"    %%% "laminar"       % LaminarVersion,
      "com.raquo"    %%% "waypoint"      % "0.5.0",
      "io.circe"     %%% "circe-generic" % circeVersion,
      "io.circe"     %%% "circe-parser"  % circeVersion,
      "io.laminext"  %%% "core"          % LaminarVersion,
      "io.laminext"  %%% "fetch"         % LaminarVersion,
      "io.laminext"  %%% "websocket"     % LaminarVersion,
      "io.laminext"  %%% "fetch-circe"   % LaminarVersion,
      "com.lihaoyi"  %%% "upickle"       % "1.3.8",
//      "com.github.uosis" %%% "laminar-web-components-material" % "0.1.0"
    )
  )