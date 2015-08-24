package nl.timmybankers.api.stores

import nl.timmybankers.api.Model._

object Events {
  sealed trait Event
  case class ApiAdded(api: Api) extends Event
  case class ApiRemoved(id: String) extends Event

  import io.circe._
  import io.circe.generic.semiauto._

  implicit val encodeEvent: Encoder[Event] = deriveFor[Event].encoder
  implicit val decodeEvent: Decoder[Event] = deriveFor[Event].decoder
  implicit val decodeApi: Decoder[Api] = deriveFor[Api].decoder

  implicit val encodeApiAdded: Encoder[ApiAdded] = deriveFor[ApiAdded].encoder
}
