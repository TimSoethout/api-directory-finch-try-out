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

    val topicName = "lonely-planet"
    val brokerPort = 5001
    val zkPort = 5000

    // For not unit test tool to make sure Kafka is available
    val kafkaUnitServer = new KafkaUnit(zkPort, brokerPort)
    kafkaUnitServer.startup()

    val kafka: ReactiveKafka = new ReactiveKafka()
    val eventLogConsumer = kafka.consume(ConsumerProperties(
      brokerList = s"localhost:$brokerPort",
      zooKeeperHost = s"localhost:$zkPort",
      topic = topicName,
      groupId = "groupName",
      decoder = new StringDecoder()
    ))
    val eventLogPublisher = kafka.publish(ProducerProperties(
      brokerList = s"localhost:$brokerPort",
      topic = topicName,
      clientId = "groupName",
      encoder = new StringEncoder()
    ))

    Source(eventLogConsumer)
      .map(json => {
      println(s"input for decoding $json")
      val decoded: Xor[Error, Event] = decode[ApiAdded](json).orElse(decode[ApiRemoved](json))
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
      println(s"Wrote to topic $topicName: $encoded")
    }

    override def list(): List[Api] = synchronized {
      db.values.toList
    }

    override def save(api: Api): Unit = {
      val added: ApiAdded = ApiAdded(api)
      val encoded: String = Encoder[ApiAdded].apply(added).toString()

      eventLogPublisher.onNext(encoded)
      println(s"Wrote to topic $topicName: $encoded")
    }

    override def search(value: String): List[Api] = synchronized {
      db.values.filter { api =>
        val fieldValues: Iterable[String] = api.getFields.values.collect { case s: String => s }
        fieldValues.exists(_.matches(s"(?i:.*$value.*)"))
      }
    }.toList
  }
}
