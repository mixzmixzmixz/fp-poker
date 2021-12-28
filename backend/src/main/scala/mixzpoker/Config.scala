package mixzpoker

import java.util.UUID

object Config {
//  val REDIS_URI = "redis://redis:6379" // docker compose
//  val REDIS_URI = "redis://10.53.72.227:6379" //docker in prod
  val REDIS_URI = "redis://localhost:6380" //docker local
//  val REDIS_URI = "redis://host.docker.internal:6379"
//  val KAFKA_HOST = "broker" //docker compose
//  val KAFKA_HOST = "localhost"
//val KAFKA_HOST = "host.docker.internal"
//  val KAFKA_HOST = "10.166.0.2"
val KAFKA_HOST = "localhost"
  val KAFKA_CONSUMER_UUID = UUID.fromString("6eeb25b6-1008-469d-99ad-6de7642de597")
}
