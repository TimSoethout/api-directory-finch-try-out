package nl.timmybankers.api.stores

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import cats.data.Xor
import com.softwaremill.react.kafka.{ConsumerProperties, ProducerProperties, ReactiveKafka}
import info.batey.kafka.unit.KafkaUnit
import io.circe._
import io.circe.generic.auto._
import io.circe.jawn._
import kafka.serializer.{StringDecoder, StringEncoder}
import nl.timmybankers.api.Model._
import nl.timmybankers.api.stores.Events._
import nl.timmybankers.api.{Apis, Store}

import scala.collection.mutable

trait KafkaEventSourcedStore extends Store {

  val apis = new Apis {

    implicit val actorSystem = ActorSystem("ReactiveKafka")
    implicit val materializer = ActorMaterializer()

    // For not unit test tool to make sure Kafka is available
    val kafkaUnitServer = new KafkaUnit(5000, 5001)
    kafkaUnitServer.startup()

    val kafka: ReactiveKafka = new ReactiveKafka()
    val eventLogConsumer = kafka.consume(ConsumerProperties(
      brokerList = "localhost:5001",
      zooKeeperHost = "localhost:5000",
      topic = "lonely-planet",
      groupId = "groupName",
      decoder = new StringDecoder()
    ))
    val eventLogPublisher = kafka.publish(ProducerProperties(
      brokerList = "localhost:5001",
      topic = "lonely-planet",
      clientId = "groupName",
      encoder = new StringEncoder()
    ))

    Source(eventLogConsumer)
      .map(x => {
      val json: Json = parse(x).getOrElse(Json.empty)
      println(s"Read JSON from Kafka: $json")
      json
    })
      .map(json => {
      println(s"input for decoding $json")
      val decoded: Xor[DecodingFailure, Event] = Decoder[ApiAdded].decodeJson(json).orElse(Decoder[ApiRemoved].decodeJson(json))
      println(s"Decoded $decoded")
      decoded
    })
      .collect { case r: Xor.Right[Event] => r.b }
      .runForeach {
      case ApiAdded(api)   => saveToDb(api)
      case ApiRemoved(api) => deleteFromDb(api)
    }

    Source(eventLogConsumer).runForeach(println(_))

    def saveToDb(api: Api) = synchronized {
      db += (api.id -> api)
      println(s"Saved ${api.title} with ID ${api.id} to db")
    }

    def deleteFromDb(api: String) = synchronized {
      db -= api
      println(s"Removed $api} from db")
    }

    private[this] val db: mutable.Map[String, Api] = mutable.Map.empty[String, Api]

    override def get(id: String): Option[Api] = synchronized {
      db.get(id)
    }

    override def delete(id: String): Unit = {
      val removed: ApiRemoved = ApiRemoved(id)
      val encoded: String = Encoder[ApiRemoved].apply(removed).toString()

      eventLogPublisher.onNext(encoded)
    }

    override def list(): List[Api] = synchronized {
      db.values.toList
    }

    override def save(api: Api): Unit = {
      val added: ApiAdded = ApiAdded(api)
      val encoded: String = Encoder[ApiAdded].apply(added).toString()

      eventLogPublisher.onNext(encoded)
    }

    override def search(value: String): List[Api] = synchronized {
      db.values.filter { api =>
        val fieldValues: Iterable[String] = api.getFields.values.collect { case s: String => s }
        fieldValues.exists(_.matches(s"(?i:.*$value.*)"))
      }
    }.toList
  }
}
