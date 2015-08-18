package nl.timmybankers.api

import java.util.UUID

import cats.data.Xor
import com.twitter.app.Flag
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.finagle.param.Stats
import com.twitter.finagle.stats.Counter
import com.twitter.finagle.{Httpx, ListeningServer, Service, SimpleFilter}
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}
import io.circe.generic.auto._
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.finch.circe._
import io.finch.request._
import io.finch.response._
import io.finch.route._
import nl.timmybankers.api.Model._

import scala.collection.mutable

object Model {
  case class Api(id: String,
                 title: String,
                 description: String,
                 state: State,
                 owner: String)

  sealed trait State
  case object Proposed extends State
  case object Designed extends State
  case object Development extends State
  case object Production extends State

}

object ApiEndpoint extends TwitterServer {

  val port: Flag[Int] = flag("port", 8081, "TCP port for HTTP server")

  val path = "api"

  object Apis {
    private[this] val db: mutable.Map[String, Api] = mutable.Map.empty[String, Api]

    def get(id: String): Option[Api] = synchronized {
      db.get(id)
    }

    def list(): List[Api] = synchronized {
      db.values.toList
    }

    def save(t: Api): Unit = synchronized {
      db += (t.id -> t)
    }

    def delete(id: String): Unit = synchronized {
      db -= id
    }
  }

  val apiCounter: Counter = statsReceiver.counter(path)

  Apis.save(Api("ID", "Some API", "Some description", Proposed, "Tim"))

  val getApis: Router[List[Api]] = get(path) {
    Apis.list()
  }

  val postReader: RequestReader[Api] = {
    body.as[String => Api].map(_(UUID.randomUUID().toString))
  }

  val f: PartialFunction[String, State] = {
    case "Proposed"    => log.error("stateReader called"); Proposed
    case "Designed"    => Designed
    case "Development" => Development
    case "Production"  => Production
  }

  implicit val stateDecoder: Decoder[State] = Decoder.instance { c =>
    val maybeState = for {
      v <- c.focus.asString
      o <- f.lift(v)
    } yield o

    Xor.fromOption(maybeState, DecodingFailure("State", c.history))
  }

  implicit def stateEncoder: Encoder[State] = Encoder.instance(state => Json.string(state.toString))

  val postApi: Router[Api] = post(path ? postReader) { t: Api =>
    apiCounter.incr()
    Apis.save(t)

    t
  }


  case class ApiNotFound(id: String) extends Exception(s"Api($id) not found.")
  val deleteApi: Router[Api] = delete(path / string) { id: String =>
    Apis.get(id) match {
      case Some(t) => Apis.delete(id); t
      case None    => throw new ApiNotFound(id)
    }
  }

  val deleteApis: Router[List[Api]] = delete(path) {
    val all: List[Api] = Apis.list()
    all.foreach(t => Apis.delete(t.id))

    all
  }

  val patchedApi: RequestReader[Api => Api] = body.as[Api => Api]

  val patchApi: Router[Api] =
    patch(path / string ? patchedApi) { (id: String, pa: Api => Api) =>
      Apis.get(id) match {
        case Some(currentApi) =>
          val newApi: Api = pa(currentApi)
          Apis.delete(id)
          Apis.save(newApi)

          newApi
        case None             => throw ApiNotFound(id)
      }
    }

  val handleExceptions: SimpleFilter[Request, Response] = new SimpleFilter[Request, Response] {
    def apply(req: Request, service: Service[Request, Response]): Future[Response] =
      service(req).handle {
        case ApiNotFound(id) => NotFound(Map("id" -> id))
      }
  }

  val api: Service[Request, Response] = handleExceptions andThen (
    getApis :+: postApi :+: deleteApi :+: deleteApis :+: patchApi
    ).toService

  def main(): Unit = {
    val server: ListeningServer = Httpx.server
      .configured(Stats(statsReceiver))
      .serve(s":${port()}", api)

    onExit {
      server.close()
    }

    Await.ready(adminHttpServer)
  }
}
