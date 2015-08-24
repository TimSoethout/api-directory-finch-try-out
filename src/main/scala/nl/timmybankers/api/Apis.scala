package nl.timmybankers.api

import cats.data.Xor
import io.circe.generic.semiauto._
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import nl.timmybankers.api.Model._

object Model {
  case class Api(id: String,
                 title: String,
                 description: String,
                 state: State,
                 owner: String)
    extends CaseClassReflector

  object Api {
    implicit val encodeApi: Encoder[Api] = deriveFor[Api].encoder
    implicit val decodeApi: Decoder[Api] = deriveFor[Api].decoder
  }

  //Helper
  trait CaseClassReflector extends Product {
    def getFields: Map[String, AnyRef] =
      getClass.getDeclaredFields.map(field => {
        field setAccessible true
        field.getName -> field.get(this)
      }).toMap
  }

  sealed trait State
  case object Proposed extends State
  case object Designed extends State
  case object Development extends State
  case object Production extends State

  object State {
    implicit val stateDecoder: Decoder[State] = Decoder.instance { c =>
      val f: PartialFunction[String, State] = {
        case "Proposed"    => Proposed
        case "Designed"    => Designed
        case "Development" => Development
        case "Production"  => Production
      }
      val maybeState = for {
        v <- c.focus.asString
        o <- f.lift(v)
      } yield o

      Xor.fromOption(maybeState, DecodingFailure("State", c.history))
    }

    implicit val stateEncoder: Encoder[State] = Encoder.instance(state => Json.string(state.toString))
  }
}

trait Store {
  def apis: Apis
}

trait Apis {
  def get(id: String): Option[Api]

  def list(): List[Api]

  def save(t: Api): Unit

  def delete(id: String): Unit

  def search(value: String): List[Api]
}