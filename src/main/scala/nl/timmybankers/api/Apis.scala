package nl.timmybankers.api

import nl.timmybankers.api.Model.Api

object Model {
  case class Api(id: String,
                 title: String,
                 description: String,
                 state: State,
                 owner: String)
    extends CaseClassReflector

  sealed trait State
  case object Proposed extends State
  case object Designed extends State
  case object Development extends State
  case object Production extends State


  //Helper
  trait CaseClassReflector extends Product {
    def getFields: Map[String, AnyRef] =
      getClass.getDeclaredFields.map(field => {
        field setAccessible true
        field.getName -> field.get(this)
      }).toMap
  }

}

trait Store {
  def apis : Apis
}

trait Apis {
  def get(id: String): Option[Api]

  def list(): List[Api]

  def save(t: Api): Unit

  def delete(id: String): Unit

  def search(value: String): List[Api]
}