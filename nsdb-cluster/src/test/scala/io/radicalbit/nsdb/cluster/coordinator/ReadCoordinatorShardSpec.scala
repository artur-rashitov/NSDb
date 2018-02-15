package io.radicalbit.nsdb.cluster.coordinator

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.radicalbit.nsdb.actors.SchemaActor
import io.radicalbit.nsdb.cluster.actor.NamespaceDataActor
import io.radicalbit.nsdb.cluster.actor.NamespaceDataActor.AddRecordToLocation
import io.radicalbit.nsdb.cluster.index.Location
import io.radicalbit.nsdb.common.statement._
import io.radicalbit.nsdb.protocol.MessageProtocol.Commands._
import io.radicalbit.nsdb.protocol.MessageProtocol.Events._
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._

class ReadCoordinatorShardSpec
    extends TestKit(
      ActorSystem(
        "nsdb-test",
        ConfigFactory
          .load()
          .withValue("nsdb.sharding.enabled", ConfigValueFactory.fromAnyRef(true))
          .withValue("nsdb.sharding.interval", ConfigValueFactory.fromAnyRef("5s"))
      ))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ReadCoordinatorBehaviour {

  override val probe                = TestProbe()
  override val basePath             = "target/test_index/ReadCoordinatorShardSpec"
  override val db                   = "db"
  override val namespace            = "registry"
  val schemaActor                   = system.actorOf(SchemaActor.props(basePath, db, namespace))
  val namespaceDataActor            = system.actorOf(NamespaceDataActor.props(basePath))
  override val readCoordinatorActor = system actorOf ReadCoordinator.props(null, schemaActor)

  override def beforeAll = {
    import scala.concurrent.duration._
    implicit val timeout = Timeout(5 second)

    Await.result(readCoordinatorActor ? SubscribeNamespaceDataActor(namespaceDataActor, Some("node1")), 3 seconds)
    Await.result(namespaceDataActor ? DropMetric(db, namespace, "people"), 3 seconds)

    val interval = FiniteDuration(
      system.settings.config.getDuration("nsdb.write.scheduler.interval", TimeUnit.SECONDS),
      TimeUnit.SECONDS)

    expectNoMessage(interval)

    Await.result(schemaActor ? UpdateSchemaFromRecord(db, namespace, "people", testRecords.head), 3 seconds)

    val location1 = Location("people", "node1", 0, 5)
    val location2 = Location("people", "node1", 6, 10)

    recordsShard1.foreach(r =>
      Await.result(namespaceDataActor ? AddRecordToLocation(db, namespace, "people", r, location1), 3 seconds))
    recordsShard2.foreach(r =>
      Await.result(namespaceDataActor ? AddRecordToLocation(db, namespace, "people", r, location2), 3 seconds))

    expectNoMessage(interval)
    expectNoMessage(interval)
  }

  "ReadCoordinator in shard mode" should behave.like(defaultBehaviour)

  "ReadCoordinator in shard mode" when {

    "receive a select projecting a wildcard with a limit" should {
      "execute it successfully" in {

        probe.send(
          readCoordinatorActor,
          ExecuteStatement(
            SelectSQLStatement(db = db,
                               namespace = namespace,
                               metric = "people",
                               distinct = false,
                               fields = AllFields,
                               limit = Some(LimitOperator(2)))
          )
        )
        within(5 seconds) {
          val expected = probe.expectMsgType[SelectStatementExecuted]
          expected.values.size shouldBe 2
        }
      }
    }

    "receive a select projecting a wildcard with a limit and a ordering" should {
      "execute it successfully when ordered by timestamp" in {

        probe.send(
          readCoordinatorActor,
          ExecuteStatement(
            SelectSQLStatement(db = db,
                               namespace = namespace,
                               metric = "people",
                               distinct = false,
                               fields = AllFields,
                               limit = Some(LimitOperator(2)),
                               order = Some(DescOrderOperator("timestamp")))
          )
        )
        within(5 seconds) {
          val expected = probe.expectMsgType[SelectStatementExecuted]
          expected.values.size shouldBe 2
          expected.values shouldBe recordsShard2.tail.reverse
        }
      }

      "execute it successfully when ordered by another dimension" in {
        probe.send(
          readCoordinatorActor,
          ExecuteStatement(
            SelectSQLStatement(db = db,
                               namespace = namespace,
                               metric = "people",
                               distinct = false,
                               fields = AllFields,
                               limit = Some(LimitOperator(2)),
                               order = Some(DescOrderOperator("name")))
          )
        )
        within(5 seconds) {
          val expected = probe.expectMsgType[SelectStatementExecuted]
          expected.values.size shouldBe 2
          recordsShard1 foreach { r =>
            expected.values.contains(r) shouldBe true
          }
        }
      }
    }

  }
}