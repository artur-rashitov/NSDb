package io.radicalbit.nsdb.actors

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import io.radicalbit.nsdb.actors.NamespaceSchemaActor.commands._
import io.radicalbit.nsdb.actors.NamespaceSchemaActor.events.AllSchemasDeleted
import io.radicalbit.nsdb.common.protocol.Bit
import io.radicalbit.nsdb.coordinator.ReadCoordinator.GetSchema
import io.radicalbit.nsdb.coordinator.WriteCoordinator.{DeleteNamespace, NamespaceDeleted}
import io.radicalbit.nsdb.index.Schema

import scala.collection.mutable
import scala.concurrent.duration._

class NamespaceSchemaActor(val basePath: String) extends Actor with ActorLogging {

  val schemaActors: mutable.Map[String, ActorRef] = mutable.Map.empty

  private def getSchemaActor(namespace: String): ActorRef =
    schemaActors.getOrElse(
      namespace, {
        val schemaActor = context.actorOf(SchemaActor.props(basePath, namespace), s"schema-service-$namespace")
        schemaActors += (namespace -> schemaActor)
        schemaActor
      }
    )

  implicit val timeout: Timeout = 1 second
  import context.dispatcher

  override def receive = {
    case msg @ GetSchema(namespace, _) =>
      getSchemaActor(namespace).forward(msg)
    case msg @ UpdateSchema(namespace, _, _) =>
      getSchemaActor(namespace).forward(msg)
    case msg @ UpdateSchemaFromRecord(namespace, _, _) =>
      getSchemaActor(namespace).forward(msg)
    case msg @ DeleteSchema(namespace, _) =>
      getSchemaActor(namespace).forward(msg)
    case DeleteNamespace(namespace) =>
      val schemaActorToDelete = getSchemaActor(namespace)
      (schemaActorToDelete ? DeleteAllSchemas(namespace))
        .mapTo[AllSchemasDeleted]
        .map { e =>
          schemaActorToDelete ! PoisonPill
          schemaActors -= namespace
          NamespaceDeleted(namespace)
        }
        .pipeTo(sender)
  }
}

object NamespaceSchemaActor {

  def props(basePath: String): Props = Props(new NamespaceSchemaActor(basePath))

  object commands {

    case class UpdateSchema(namespace: String, metric: String, newSchema: Schema)
    case class UpdateSchemaFromRecord(namespace: String, metric: String, record: Bit)
    case class DeleteSchema(namespace: String, metric: String)
    case class DeleteAllSchemas(namespace: String)
  }
  object events {
    case class SchemaUpdated(namespace: String, metric: String)
    case class UpdateSchemaFailed(namespace: String, metric: String, errors: List[String])
    case class SchemaDeleted(namespace: String, metric: String)
    case class AllSchemasDeleted(namespace: String)
  }
}