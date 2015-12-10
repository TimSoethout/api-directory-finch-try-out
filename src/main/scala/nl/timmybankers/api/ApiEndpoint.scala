package nl.timmybankers.api

import java.util.UUID

import com.twitter.app.Flag
import com.twitter.finagle
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.param.Stats
import com.twitter.finagle.stats.Counter
import com.twitter.finagle._
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}
import io.finch._
import io.finch.circe._
import io.finch._
import io.finch.circe._
import io.circe.generic.auto._
import nl.timmybankers.api.Model._
import nl.timmybankers.api.stores.KafkaEventSourcedStore


object ApiEndpoint extends TwitterServer with KafkaEventSourcedStore {

  val port: Flag[Int] = flag("port", 8081, "TCP port for HTTP server")

  val path = "api"

  val apiCounter: Counter = statsReceiver.counter(path)

  apis.save(Api("ID", "Some API", "Some description", Proposed, "Tim"))

  val getApis: io.finch.Endpoint[List[Api]] =
    get(path ? paramOption("search")) {
      value: Option[String] => Ok(value.map(apis.search).getOrElse(apis.list()))
    }

  val postReader: RequestReader[Api] = {
    body.as[String => Api].map(_(UUID.randomUUID().toString))
  }


  val postApi: Endpoint[Api] = post(path ? postReader) { t: Api =>
    apiCounter.incr()
    apis.save(t)

    Ok(t)
  }


  case class ApiNotFound(id: String) extends Exception(s"Api($id) not found.")
  val deleteApi: Endpoint[Api] = delete(path / string) { id: String =>
    Ok {
      apis.get(id) match {
        case Some(t) => apis.delete(id); t
        case None => throw new ApiNotFound(id)
      }
    }
  }

  val deleteApis: Endpoint[List[Api]] = delete(path) {
    val all: List[Api] = apis.list()
    all.foreach(t => apis.delete(t.id))

    Ok(all)
  }

  val patchedApi: RequestReader[Api => Api] = body.as[Api => Api]

  val patchApi: Endpoint[Api] =
    patch(path / string ? patchedApi) { (id: String, pa: Api => Api) =>
      Ok {
        apis.get(id) match {
          case Some(currentApi) =>
            val newApi: Api = pa(currentApi)
            apis.delete(id)
            apis.save(newApi)

            newApi
          case None => throw ApiNotFound(id)
        }
      }
    }

  val handleExceptions: SimpleFilter[Request, Response] = new SimpleFilter[Request, Response] {
    def apply(req: Request, service: Service[Request, Response]): Future[Response] =
      service(req).handle {
        case ApiNotFound(id) => Response(com.twitter.finagle.http.Status.NotFound) //.NotFound(Map("id" -> id)))
        case e               => Response(com.twitter.finagle.http.Status.BadRequest) //(e.toString) //TODO is this correct?
      }
  }

  private val service: Service[Request, Response] = (
    getApis :+: postApi :+: deleteApi :+: deleteApis :+: patchApi
    ).toService
  val api: Service[Request, Response] = handleExceptions andThen service

  def main(): Unit = {
    val server: ListeningServer = Http.server
      .configured(Stats(statsReceiver))
      .serve(s":${port()}", api)

    onExit {
      server.close()
    }

    Await.ready(adminHttpServer)
  }
}
