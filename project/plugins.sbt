resolvers += Resolver.sonatypeRepo("snapshots") // For mdoc (see also build.sbt)

logLevel := Level.Warn

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.7.1")

addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.20.0")

addSbtPlugin("com.evolution" % "sbt-artifactory-plugin" % "0.0.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.6")