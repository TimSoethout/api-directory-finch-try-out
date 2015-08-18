package nl.timmybankers.api.stores

import nl.timmybankers.api.Model.Api
import nl.timmybankers.api.{Apis, Store}

import scala.collection.mutable

trait InMemoryStore extends Store {

  val apis = new Apis {
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

    def search(value: String): List[Api] = synchronized {
      db.values.filter { api =>
        val fieldValues: Iterable[String] = api.getFields.values.collect { case s: String => s }
        fieldValues.exists(_.matches(s"(?i:.*$value.*)"))
      }
    }.toList
  }
}